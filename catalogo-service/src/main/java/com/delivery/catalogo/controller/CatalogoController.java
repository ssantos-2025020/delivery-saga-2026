package com.delivery.catalogo.controller;

import com.delivery.catalogo.service.CatalogoService;
import com.delivery.common.dto.StockReservaRequest;
import com.delivery.common.dto.StockReservaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/stock")
@RequiredArgsConstructor
public class CatalogoController {
    
    private final CatalogoService catalogoService;
    
    @PostMapping("/reservar")
    public ResponseEntity<StockReservaResponse> reservarStock(
            @RequestHeader("X-Internal-API-Key") String apiKey,
            @RequestBody StockReservaRequest request) {
        // Validar API key
        if (!apiKey.equals(System.getenv().getOrDefault("INTERNAL_API_KEY", "internal-api-key-secret"))) {
            return ResponseEntity.status(403).build();
        }
        
        StockReservaResponse response = catalogoService.reservarStock(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/liberar")
    public ResponseEntity<Void> liberarStock(
            @RequestHeader("X-Internal-API-Key") String apiKey,
            @RequestParam String reservaId) {
        if (!apiKey.equals(System.getenv().getOrDefault("INTERNAL_API_KEY", "internal-api-key-secret"))) {
            return ResponseEntity.status(403).build();
        }
        
        catalogoService.liberarStock(reservaId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/confirmar")
    public ResponseEntity<Void> confirmarReserva(
            @RequestHeader("X-Internal-API-Key") String apiKey,
            @RequestParam String reservaId) {
        if (!apiKey.equals(System.getenv().getOrDefault("INTERNAL_API_KEY", "internal-api-key-secret"))) {
            return ResponseEntity.status(403).build();
        }
        
        catalogoService.confirmarReserva(reservaId);
        return ResponseEntity.ok().build();
    }
}
