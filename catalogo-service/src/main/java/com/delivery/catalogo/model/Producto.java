package com.delivery.catalogo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "producto", indexes = {
    @Index(name = "idx_producto_comercio", columnList = "comercio_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String nombre;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;
    
    @Column(nullable = false)
    private Integer stock;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comercio_id", nullable = false)
    private Comercio comercio;
    
    @Version
    private Long version;
}
