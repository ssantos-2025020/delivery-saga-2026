package com.delivery.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_key", 
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_cliente_key", columnNames = {"cliente_id", "key"})
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;
    
    @Column(nullable = false, length = 255)
    private String key;
    
    @Column(nullable = false)
    private Long pedidoId;
    
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}
