package com.delivery.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "pedido", indexes = {
    @Index(name = "idx_pedido_cliente_fecha", columnList = "cliente_id, fecha_pedido DESC"),
    @Index(name = "idx_pedido_estado_repartidor", columnList = "estado, repartidor_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repartidor_id")
    private Usuario repartidor;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedido estado = EstadoPedido.PENDIENTE;
    
    @Column(name = "fecha_pedido", nullable = false, updatable = false)
    private LocalDateTime fechaPedido = LocalDateTime.now();
    
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetallePedido> detalles;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private java.math.BigDecimal total;
    
    @Version
    private Long version;
}
