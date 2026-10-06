package com.delivery.pedidos.repository;

import com.delivery.common.enums.EstadoPedido;
import com.delivery.pedidos.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    
    List<Pedido> findByClienteId(Long clienteId);
    
    @Query("SELECT p FROM Pedido p WHERE p.estado = :estado AND p.fechaPedido < :fecha")
    List<Pedido> findByEstadoAndFechaPedidoBefore(@Param("estado") EstadoPedido estado, 
                                                   @Param("fecha") LocalDateTime fecha);
}
