package com.delivery.controller;

import com.delivery.dto.ComercioResponse;
import com.delivery.dto.ProductoResponse;
import com.delivery.service.ComercioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comercios")
@RequiredArgsConstructor
public class ComercioController {
    
    private final ComercioService comercioService;
    
    @GetMapping
    public ResponseEntity<Page<ComercioResponse>> listarComercios(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String categoria) {
        return ResponseEntity.ok(comercioService.listarComercios(page, size, categoria));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ComercioResponse> obtenerComercio(@PathVariable Long id) {
        return ResponseEntity.ok(comercioService.obtenerComercio(id));
    }
    
    @GetMapping("/{id}/productos")
    public ResponseEntity<Page<ProductoResponse>> listarProductos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Boolean soloDisponibles) {
        return ResponseEntity.ok(comercioService.listarProductos(id, page, size, soloDisponibles));
    }
}
