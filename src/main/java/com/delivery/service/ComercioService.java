package com.delivery.service;

import com.delivery.dto.ComercioResponse;
import com.delivery.dto.ProductoResponse;
import com.delivery.model.Comercio;
import com.delivery.model.Producto;
import com.delivery.repository.ComercioRepository;
import com.delivery.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComercioService {
    
    private final ComercioRepository comercioRepository;
    private final ProductoRepository productoRepository;
    
    @Value("${app.pagination.default-size}")
    private int defaultPageSize;
    
    @Value("${app.pagination.max-size}")
    private int maxPageSize;
    
    @Transactional(readOnly = true)
    public Page<ComercioResponse> listarComercios(Integer page, Integer size, String categoria) {
        Pageable pageable = createPageable(page, size, Sort.by("nombre").ascending());
        
        Page<Comercio> comercios;
        if (categoria != null && !categoria.isEmpty()) {
            comercios = comercioRepository.findByCategoriaAndAbierto(categoria, pageable);
        } else {
            comercios = comercioRepository.findComerciosAbiertos(pageable);
        }
        
        return comercios.map(this::mapToResponse);
    }
    
    @Transactional(readOnly = true)
    public ComercioResponse obtenerComercio(Long id) {
        Comercio comercio = comercioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comercio no encontrado"));
        return mapToResponse(comercio);
    }
    
    @Transactional(readOnly = true)
    public Page<ProductoResponse> listarProductos(Long comercioId, Integer page, Integer size, Boolean soloDisponibles) {
        Pageable pageable = createPageable(page, size, Sort.by("nombre").ascending());
        
        Page<Producto> productos;
        if (Boolean.TRUE.equals(soloDisponibles)) {
            productos = productoRepository.findDisponiblesByComercio(comercioId, pageable);
        } else {
            productos = productoRepository.findByComercioId(comercioId, pageable);
        }
        
        return productos.map(this::mapProductoToResponse);
    }
    
    private Pageable createPageable(Integer page, Integer size, Sort sort) {
        int pageSize = (size != null && size > 0) ? Math.min(size, maxPageSize) : defaultPageSize;
        int pageNumber = (page != null && page >= 0) ? page : 0;
        return PageRequest.of(pageNumber, pageSize, sort);
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
