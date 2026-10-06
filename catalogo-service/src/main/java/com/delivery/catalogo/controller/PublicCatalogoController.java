package com.delivery.catalogo.controller;

import com.delivery.catalogo.dto.ComercioResponse;
import com.delivery.catalogo.dto.ProductoResponse;
import com.delivery.catalogo.model.Comercio;
import com.delivery.catalogo.model.Producto;
import com.delivery.catalogo.repository.ComercioRepository;
import com.delivery.catalogo.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/comercios")
@RequiredArgsConstructor
public class PublicCatalogoController {
    
    private final ComercioRepository comercioRepository;
    private final ProductoRepository productoRepository;
    
    @GetMapping
    public ResponseEntity<Page<ComercioResponse>> listarComercios(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String categoria) {
        
        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by("nombre").ascending());
        
        Page<Comercio> comercios;
        if (categoria != null && !categoria.isEmpty()) {
            comercios = comercioRepository.findByCategoriaAndAbierto(categoria, pageable);
        } else {
            comercios = comercioRepository.findComerciosAbiertos(pageable);
        }
        
        return ResponseEntity.ok(comercios.map(this::mapToResponse));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ComercioResponse> obtenerComercio(@PathVariable Long id) {
        Comercio comercio = comercioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comercio no encontrado"));
        return ResponseEntity.ok(mapToResponse(comercio));
    }
    
    @GetMapping("/{id}/productos")
    public ResponseEntity<Page<ProductoResponse>> listarProductos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Boolean soloDisponibles) {
        
        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by("nombre").ascending());
        
        Page<Producto> productos;
        if (Boolean.TRUE.equals(soloDisponibles)) {
            productos = productoRepository.findDisponiblesByComercio(id, pageable);
        } else {
            productos = productoRepository.findByComercioId(id, pageable);
        }
        
        return ResponseEntity.ok(productos.map(this::mapProductoToResponse));
    }
    
    private ComercioResponse mapToResponse(Comercio comercio) {
        List<ProductoResponse> productos = comercio.getProductos().stream()
                .map(this::mapProductoToResponse)
                .collect(Collectors.toList());
        
        return ComercioResponse.builder()
                .id(comercio.getId())
                .nombre(comercio.getNombre())
                .categoria(comercio.getCategoria())
                .abierto(comercio.getAbierto())
                .productos(productos)
                .build();
    }
    
    private ProductoResponse mapProductoToResponse(Producto producto) {
        return ProductoResponse.builder()
                .id(producto.getId())
                .nombre(producto.getNombre())
                .precio(producto.getPrecio())
                .stock(producto.getStock())
                .comercioId(producto.getComercio().getId())
                .build();
    }
}
