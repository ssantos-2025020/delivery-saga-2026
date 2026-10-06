package com.delivery.catalogo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_reserva", indexes = {
    @Index(name = "idx_stock_reserva_reserva_id", columnList = "reserva_id"),
    @Index(name = "idx_stock_reserva_estado", columnList = "estado")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReserva {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 255)
    private String reservaId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;
    
    @Column(nullable = false)
    private Integer cantidad;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado = EstadoReserva.RESERVADA;
    
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();
    
    @Column(name = "fecha_expiracion")
    private LocalDateTime fechaExpiracion;
    
    public enum EstadoReserva {
        RESERVADA,
        LIBERADA,
        CONFIRMADA
    }
}
