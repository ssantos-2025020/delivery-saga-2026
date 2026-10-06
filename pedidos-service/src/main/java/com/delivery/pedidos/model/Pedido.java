package com.delivery.pedidos.model;

import com.delivery.common.enums.EstadoPedido;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
    
    @Column(nullable = false)
    private Long clienteId;
    
    @Column(length = 100)
    private String clienteNombre;
    
    @Column
    private Long repartidorId;
    
    @Column(length = 100)
    private String repartidorNombre;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedido estado = EstadoPedido.PENDIENTE;
    
    @Column(name = "fecha_pedido", nullable = false, updatable = false)
    private LocalDateTime fechaPedido = LocalDateTime.now();
    
    @Column(nullable = false, length = 255)
    private String reservaId;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;
    
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetallePedido> detalles;
    
    @Version
    private Long version;
}
