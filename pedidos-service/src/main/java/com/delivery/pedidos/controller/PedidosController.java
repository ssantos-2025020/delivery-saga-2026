package com.delivery.pedidos.controller;

import com.delivery.common.enums.EstadoPedido;
import com.delivery.pedidos.dto.PedidoRequest;
import com.delivery.pedidos.dto.PedidoResponse;
import com.delivery.pedidos.model.Pedido;
import com.delivery.pedidos.repository.PedidoRepository;
import com.delivery.pedidos.service.PedidosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
public class PedidosController {
    
    private final PedidosService pedidosService;
    private final PedidoRepository pedidoRepository;
    
    @PostMapping
    public ResponseEntity<PedidoResponse> crearPedido(
            @Valid @RequestBody PedidoRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<PedidosService.ItemPedido> items = request.getItems().stream()
                .map(item -> new PedidosService.ItemPedido(item.getProductoId(), item.getCantidad()))
                .collect(Collectors.toList());
        
        Pedido pedido = pedidosService.crearPedido(userId, userName, items);
        return ResponseEntity.ok(mapToResponse(pedido));
    }
    
    @GetMapping("/mis-pedidos")
    public ResponseEntity<List<PedidoResponse>> obtenerMisPedidos(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<Pedido> pedidos = pedidoRepository.findByClienteId(userId);
        return ResponseEntity.ok(pedidos.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponse> obtenerPedido(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
        
        if (!pedido.getClienteId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        
        return ResponseEntity.ok(mapToResponse(pedido));
    }
    
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelarPedido(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        pedidosService.cancelarPedido(id, userId);
        return ResponseEntity.ok().build();
    }
    
    private PedidoResponse mapToResponse(Pedido pedido) {
        List<PedidoResponse.ItemPedidoResponse> items = pedido.getDetalles().stream()
                .map(d -> PedidoResponse.ItemPedidoResponse.builder()
                        .productoId(d.getProductoId())
                        .productoNombre(d.getProductoNombre())
                        .cantidad(d.getCantidad())
                        .precioUnitario(d.getPrecioUnitario())
                        .subtotal(d.getSubtotal())
                        .build())
                .collect(Collectors.toList());
        
        return PedidoResponse.builder()
                .id(pedido.getId())
                .clienteId(pedido.getClienteId())
                .clienteNombre(pedido.getClienteNombre())
                .repartidorId(pedido.getRepartidorId())
                .repartidorNombre(pedido.getRepartidorNombre())
                .estado(pedido.getEstado())
                .fechaPedido(pedido.getFechaPedido())
                .total(pedido.getTotal())
                .reservaId(pedido.getReservaId())
                .detalles(items)
                .build();
    }
}
