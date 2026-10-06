package com.delivery.service;

import com.delivery.dto.*;
import com.delivery.exception.BusinessException;
import com.delivery.exception.InvalidStatusException;
import com.delivery.exception.StockInsuficienteException;
import com.delivery.model.*;
import com.delivery.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PedidoService {
    
    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final MeterRegistry meterRegistry;
    
    @Value("${app.order.max-items}")
    private int maxItems;
    
    @Value("${app.order.max-quantity-per-item}")
    private int maxQuantityPerItem;
    
    @Value("${app.pagination.default-size}")
    private int defaultPageSize;
    
    @Value("${app.pagination.max-size}")
    private int maxPageSize;
    
    @Transactional
    public PedidoResponse crearPedido(Long clienteId, PedidoRequest request, String idempotencyKey) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                Optional<IdempotencyKey> existing = idempotencyKeyRepository
                        .findByClienteIdAndKey(clienteId, idempotencyKey);
                if (existing.isPresent()) {
                    Pedido pedido = pedidoRepository.findById(existing.get().getPedidoId())
                            .orElseThrow(() -> new BusinessException("Pedido no encontrado"));
                    log.info("Pedido recuperado por idempotency key: {}", pedido.getId());
                    return mapToResponse(pedido);
                }
            }
            
            Pedido pedido = crearPedidoConBloqueo(clienteId, request);
            
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                IdempotencyKey key = IdempotencyKey.builder()
                        .cliente(usuarioRepository.findById(clienteId).orElseThrow())
                        .key(idempotencyKey)
                        .pedidoId(pedido.getId())
                        .build();
                idempotencyKeyRepository.save(key);
            }
            
            log.info("Pedido creado exitosamente: {}", pedido.getId());
            return mapToResponse(pedido);
            
        } finally {
            sample.stop(Timer.builder("pedido.creacion")
                    .description("Tiempo de creación de pedido")
                    .register(meterRegistry));
        }
    }
    
    @Transactional
    protected Pedido crearPedidoConBloqueo(Long clienteId, PedidoRequest request) {
        Usuario cliente = usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new BusinessException("Cliente no encontrado"));
        
        if (request.getItems().size() > maxItems) {
            throw new BusinessException("El pedido no puede tener más de " + maxItems + " items");
        }
        
        Set<Long> productoIds = request.getItems().stream()
                .map(ItemPedidoRequest::getProductoId)
                .collect(Collectors.toSet());
        
        List<Producto> productos = productoRepository.findByIdsWithLock(new ArrayList<>(productoIds));
        
        if (productos.size() != productoIds.size()) {
            throw new BusinessException("Uno o más productos no existen");
        }
        
        Map<Long, Producto> productoMap = productos.stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));
        
        List<DetallePedido> detalles = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        
        for (ItemPedidoRequest item : request.getItems()) {
            Producto producto = productoMap.get(item.getProductoId());
            
            if (item.getCantidad() > maxQuantityPerItem) {
                throw new BusinessException("La cantidad máxima por item es " + maxQuantityPerItem);
            }
            
            if (producto.getStock() < item.getCantidad()) {
                throw new StockInsuficienteException(
                        String.format("Stock insuficiente para producto %s. Disponible: %d, Solicitado: %d",
                                producto.getNombre(), producto.getStock(), item.getCantidad()));
            }
            
            producto.setStock(producto.getStock() - item.getCantidad());
            
            BigDecimal subtotal = producto.getPrecio().multiply(BigDecimal.valueOf(item.getCantidad()));
            total = total.add(subtotal);
            
            DetallePedido detalle = DetallePedido.builder()
                    .producto(producto)
                    .cantidad(item.getCantidad())
                    .precioUnitario(producto.getPrecio())
                    .subtotal(subtotal)
                    .build();
            
            detalles.add(detalle);
        }
        
        Pedido pedido = Pedido.builder()
                .cliente(cliente)
                .estado(EstadoPedido.PENDIENTE)
                .total(total)
                .detalles(detalles)
                .build();
        
        detalles.forEach(d -> d.setPedido(pedido));
        
        return pedidoRepository.save(pedido);
    }
    
    @Transactional(readOnly = true)
    public Page<PedidoResponse> obtenerMisPedidos(Long clienteId, Integer page, Integer size) {
        Pageable pageable = createPageable(page, size, Sort.by("fechaPedido").descending());
        Page<Pedido> pedidos = pedidoRepository.findByClienteId(clienteId, pageable);
        return pedidos.map(this::mapToResponse);
    }
    
    @Transactional(readOnly = true)
    public PedidoResponse obtenerPedido(Long pedidoId, Long clienteId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new BusinessException("Pedido no encontrado"));
        
        if (!pedido.getCliente().getId().equals(clienteId)) {
            throw new BusinessException("No tiene permisos para ver este pedido");
        }
        
        return mapToResponse(pedido);
    }
    
    @Transactional
    public void cancelarPedido(Long pedidoId, Long clienteId) {
        Pedido pedido = pedidoRepository.findByIdWithLock(pedidoId)
                .orElseThrow(() -> new BusinessException("Pedido no encontrado"));
        
        if (!pedido.getCliente().getId().equals(clienteId)) {
            throw new BusinessException("No tiene permisos para cancelar este pedido");
        }
        
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new InvalidStatusException(
                    "Solo se pueden cancelar pedidos en estado PENDIENTE. Estado actual: " + pedido.getEstado());
        }
        
        Set<Long> productoIds = pedido.getDetalles().stream()
                .map(d -> d.getProducto().getId())
                .collect(Collectors.toSet());
        
        List<Producto> productos = productoRepository.findByIdsWithLock(new ArrayList<>(productoIds));
        Map<Long, Producto> productoMap = productos.stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));
        
        for (DetallePedido detalle : pedido.getDetalles()) {
            Producto producto = productoMap.get(detalle.getProducto().getId());
            producto.setStock(producto.getStock() + detalle.getCantidad());
        }
        
        pedido.setEstado(EstadoPedido.CANCELADO);
        pedidoRepository.save(pedido);
        
        log.info("Pedido {} cancelado exitosamente", pedidoId);
    }
    
    @Transactional
    public void asignarRepartidor(Long pedidoId, Long repartidorId) {
        Pedido pedido = pedidoRepository.findByIdWithLock(pedidoId)
                .orElseThrow(() -> new BusinessException("Pedido no encontrado"));
        
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new InvalidStatusException("El pedido no está disponible para asignación");
        }
        
        Usuario repartidor = usuarioRepository.findById(repartidorId)
                .orElseThrow(() -> new BusinessException("Repartidor no encontrado"));
        
        pedido.setRepartidor(repartidor);
        pedido.setEstado(EstadoPedido.ASIGNADO);
        pedidoRepository.save(pedido);
        
        log.info("Pedido {} asignado a repartidor {}", pedidoId, repartidorId);
    }
    
    @Transactional
    public void actualizarEstado(Long pedidoId, EstadoPedido nuevoEstado, Long repartidorId) {
        Pedido pedido = pedidoRepository.findByIdWithLock(pedidoId)
                .orElseThrow(() -> new BusinessException("Pedido no encontrado"));
        
        if (!pedido.getRepartidor().getId().equals(repartidorId)) {
            throw new BusinessException("No tiene permisos para modificar este pedido");
        }
        
        if (!esTransicionValida(pedido.getEstado(), nuevoEstado)) {
            throw new InvalidStatusException(
                    String.format("Transición inválida de %s a %s", pedido.getEstado(), nuevoEstado));
        }
        
        pedido.setEstado(nuevoEstado);
        pedidoRepository.save(pedido);
        
        log.info("Pedido {} actualizado a estado {}", pedidoId, nuevoEstado);
    }
    
    @Transactional(readOnly = true)
    public Page<PedidoResponse> obtenerPedidosDisponibles(EstadoPedido estado, Integer page, Integer size) {
        Pageable pageable = createPageable(page, size, Sort.by("fechaPedido").ascending());
        Page<Pedido> pedidos = pedidoRepository.findDisponiblesParaAsignar(estado, pageable);
        return pedidos.map(this::mapToResponse);
    }
    
    @Transactional(readOnly = true)
    public Page<PedidoResponse> obtenerPedidosRepartidor(Long repartidorId, Integer page, Integer size) {
        Pageable pageable = createPageable(page, size, Sort.by("fechaPedido").descending());
        Page<Pedido> pedidos = pedidoRepository.findByRepartidorId(repartidorId, pageable);
        return pedidos.map(this::mapToResponse);
    }
    
    private boolean esTransicionValida(EstadoPedido actual, EstadoPedido nuevo) {
        return switch (actual) {
            case PENDIENTE -> nuevo == EstadoPedido.ASIGNADO || nuevo == EstadoPedido.CANCELADO;
            case ASIGNADO -> nuevo == EstadoPedido.EN_CAMINO || nuevo == EstadoPedido.CANCELADO;
            case EN_CAMINO -> nuevo == EstadoPedido.ENTREGADO;
            case ENTREGADO, CANCELADO -> false;
        };
    }
    
    private Pageable createPageable(Integer page, Integer size, Sort sort) {
        int pageSize = (size != null && size > 0) ? Math.min(size, maxPageSize) : defaultPageSize;
        int pageNumber = (page != null && page >= 0) ? page : 0;
        return PageRequest.of(pageNumber, pageSize, sort);
    }
    
    private PedidoResponse mapToResponse(Pedido pedido) {
        List<ItemPedidoResponse> items = pedido.getDetalles().stream()
                .map(d -> ItemPedidoResponse.builder()
                        .productoId(d.getProducto().getId())
                        .productoNombre(d.getProducto().getNombre())
                        .cantidad(d.getCantidad())
                        .precioUnitario(d.getPrecioUnitario())
                        .subtotal(d.getSubtotal())
                        .build())
                .collect(Collectors.toList());
        
        return PedidoResponse.builder()
                .id(pedido.getId())
                .clienteId(pedido.getCliente().getId())
                .clienteNombre(pedido.getCliente().getNombre())
                .repartidorId(pedido.getRepartidor() != null ? pedido.getRepartidor().getId() : null)
                .repartidorNombre(pedido.getRepartidor() != null ? pedido.getRepartidor().getNombre() : null)
                .estado(pedido.getEstado())
                .fechaPedido(pedido.getFechaPedido())
                .total(pedido.getTotal())
                .detalles(items)
                .build();
    }
}
