package com.delivery.pedidos.service;

import com.delivery.common.dto.StockReservaRequest;
import com.delivery.common.dto.StockReservaResponse;
import com.delivery.pedidos.client.CatalogoClient;
import com.delivery.pedidos.model.DetallePedido;
import com.delivery.pedidos.model.Pedido;
import com.delivery.pedidos.repository.DetallePedidoRepository;
import com.delivery.pedidos.repository.PedidoRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PedidosService {
    
    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final CatalogoClient catalogoClient;
    private final MeterRegistry meterRegistry;
    
    @Value("${internal.api-key}")
    private String internalApiKey;
    
    private static final BigDecimal COSTO_DELIVERY = new BigDecimal("20.00");
    
    @CircuitBreaker(name = "catalogoService", fallbackMethod = "fallbackReservarStock")
    @Retry(name = "reservarStock")
    @Transactional
    public Pedido crearPedido(Long clienteId, String clienteNombre, List<ItemPedido> items) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        String reservaId = UUID.randomUUID().toString();
        log.info("Creando pedido con reservaId: {}", reservaId);
        
        try {
            // Paso 1: Reservar stock en catalogo-service
            StockReservaRequest request = StockReservaRequest.builder()
                    .reservaId(reservaId)
                    .items(convertirItems(items))
                    .build();
            
            StockReservaResponse response = catalogoClient.reservarStock(internalApiKey, request);
            
            if (!response.isExito()) {
                log.error("Fallo al reservar stock: {}", response.getMensaje());
                throw new RuntimeException("No se pudo reservar stock: " + response.getMensaje());
            }
            
            // Paso 2: Calcular total y guardar pedido
            BigDecimal total = calcularTotal(response.getProductos());
            
            Pedido pedido = Pedido.builder()
                    .clienteId(clienteId)
                    .clienteNombre(clienteNombre)
                    .estado(com.delivery.common.enums.EstadoPedido.PENDIENTE)
                    .reservaId(reservaId)
                    .total(total)
                    .build();
            
            pedido = pedidoRepository.save(pedido);
            
            // Paso 3: Guardar detalles
            List<DetallePedido> detalles = new ArrayList<>();
            for (StockReservaResponse.ProductoReservado pr : response.getProductos()) {
                DetallePedido detalle = DetallePedido.builder()
                        .pedido(pedido)
                        .productoId(pr.getProductoId())
                        .productoNombre(pr.getNombre())
                        .cantidad(pr.getCantidad())
                        .precioUnitario(pr.getPrecio())
                        .subtotal(pr.getPrecio().multiply(BigDecimal.valueOf(pr.getCantidad())))
                        .build();
                detalles.add(detalle);
            }
            
            detallePedidoRepository.saveAll(detalles);
            
            // Paso 4: Confirmar reserva
            catalogoClient.confirmarReserva(internalApiKey, reservaId);
            
            sample.stop(Timer.builder("pedido.creacion")
                    .description("Tiempo de creación de pedido")
                    .register(meterRegistry));
            
            log.info("Pedido creado exitosamente: {}", pedido.getId());
            return pedido;
            
        } catch (Exception e) {
            log.error("Error al crear pedido, iniciando compensación", e);
            // Compensación: liberar stock
            liberarStockCompensacion(reservaId);
            throw new RuntimeException("Error al crear pedido: " + e.getMessage(), e);
        }
    }
    
    @Retry(name = "liberarStock")
    @Transactional
    public void cancelarPedido(Long pedidoId, Long clienteId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
        
        if (!pedido.getClienteId().equals(clienteId)) {
            throw new RuntimeException("No tiene permisos para cancelar este pedido");
        }
        
        if (pedido.getEstado() != com.delivery.common.enums.EstadoPedido.PENDIENTE) {
            throw new RuntimeException("Solo se pueden cancelar pedidos en estado PENDIENTE");
        }
        
        pedido.setEstado(com.delivery.common.enums.EstadoPedido.CANCELADO);
        pedidoRepository.save(pedido);
        
        // Liberar stock
        liberarStockCompensacion(pedido.getReservaId());
        
        log.info("Pedido {} cancelado exitosamente", pedidoId);
    }
    
    @Retry(name = "liberarStock")
    private void liberarStockCompensacion(String reservaId) {
        try {
            catalogoClient.liberarStock(internalApiKey, reservaId);
            log.info("Stock liberado para reservaId: {}", reservaId);
        } catch (Exception e) {
            log.error("Error al liberar stock para reservaId: {}", reservaId, e);
            // No relanzar la excepción - la compensación fallida se maneja por reconciliación
        }
    }
    
    @Scheduled(fixedRate = 300000) // Cada 5 minutos
    @Transactional
    public void reconciliarReservas() {
        log.info("Iniciando reconciliación de reservas");
        
        // Buscar pedidos en estado PENDIENTE con fecha_pedido > 5 minutos
        LocalDateTime fechaLimite = LocalDateTime.now().minusMinutes(5);
        List<Pedido> pedidosPendientes = pedidoRepository
                .findByEstadoAndFechaPedidoBefore(
                        com.delivery.common.enums.EstadoPedido.PENDIENTE, fechaLimite);
        
        for (Pedido pedido : pedidosPendientes) {
            log.warn("Reserva huérfana detectada: reservaId={}", pedido.getReservaId());
            liberarStockCompensacion(pedido.getReservaId());
            pedido.setEstado(com.delivery.common.enums.EstadoPedido.CANCELADO);
            pedidoRepository.save(pedido);
        }
        
        log.info("Reconciliación completada. Reservas liberadas: {}", pedidosPendientes.size());
    }
    
    private Pedido fallbackReservarStock(Long clienteId, String clienteNombre, List<ItemPedido> items, Exception e) {
        log.error("Circuit breaker abierto para catalogo-service", e);
        throw new RuntimeException("Servicio de catálogo no disponible. Intente más tarde.");
    }
    
    private List<StockReservaRequest.StockItemRequest> convertirItems(List<ItemPedido> items) {
        return items.stream()
                .map(item -> StockReservaRequest.StockItemRequest.builder()
                        .productoId(item.getProductoId())
                        .cantidad(item.getCantidad())
                        .build())
                .toList();
    }
    
    private BigDecimal calcularTotal(List<StockReservaResponse.ProductoReservado> productos) {
        BigDecimal subtotal = productos.stream()
                .map(pr -> pr.getPrecio().multiply(BigDecimal.valueOf(pr.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return subtotal.add(COSTO_DELIVERY);
    }
    
    public record ItemPedido(Long productoId, Integer cantidad) {}
}
