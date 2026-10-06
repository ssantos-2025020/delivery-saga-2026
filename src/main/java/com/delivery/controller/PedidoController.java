package com.delivery.controller;

import com.delivery.dto.PedidoRequest;
import com.delivery.dto.PedidoResponse;
import com.delivery.model.EstadoPedido;
import com.delivery.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
public class PedidoController {
    
    private final PedidoService pedidoService;
    
    @PostMapping
    public ResponseEntity<PedidoResponse> crearPedido(
            @Valid @RequestBody PedidoRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Long clienteId) {
        return ResponseEntity.ok(pedidoService.crearPedido(clienteId, request, idempotencyKey));
    }
    
    @GetMapping("/mis-pedidos")
    public ResponseEntity<Page<PedidoResponse>> obtenerMisPedidos(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal Long clienteId) {
        return ResponseEntity.ok(pedidoService.obtenerMisPedidos(clienteId, page, size));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponse> obtenerPedido(
            @PathVariable Long id,
            @AuthenticationPrincipal Long clienteId) {
        return ResponseEntity.ok(pedidoService.obtenerPedido(id, clienteId));
    }
    
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelarPedido(
            @PathVariable Long id,
            @AuthenticationPrincipal Long clienteId) {
        pedidoService.cancelarPedido(id, clienteId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/disponibles")
    public ResponseEntity<Page<PedidoResponse>> obtenerPedidosDisponibles(
            @RequestParam EstadoPedido estado,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(pedidoService.obtenerPedidosDisponibles(estado, page, size));
    }
    
    @PostMapping("/{id}/asignar")
    public ResponseEntity<Void> asignarRepartidor(
            @PathVariable Long id,
            @AuthenticationPrincipal Long repartidorId) {
        pedidoService.asignarRepartidor(id, repartidorId);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/{id}/estado")
    public ResponseEntity<Void> actualizarEstado(
            @PathVariable Long id,
            @RequestParam EstadoPedido estado,
            @AuthenticationPrincipal Long repartidorId) {
        pedidoService.actualizarEstado(id, estado, repartidorId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/repartidor")
    public ResponseEntity<Page<PedidoResponse>> obtenerPedidosRepartidor(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal Long repartidorId) {
        return ResponseEntity.ok(pedidoService.obtenerPedidosRepartidor(repartidorId, page, size));
    }
}
