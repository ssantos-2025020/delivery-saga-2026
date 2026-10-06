package com.delivery.catalogo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "comercio", indexes = {
    @Index(name = "idx_comercio_abierto_categoria", columnList = "abierto, categoria")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comercio {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String nombre;
    
    @Column(nullable = false, length = 100)
    private String categoria;
    
    @Column(nullable = false)
    private Boolean abierto = true;
    
    @OneToMany(mappedBy = "comercio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Producto> productos;
}
