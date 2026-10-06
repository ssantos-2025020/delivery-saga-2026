package com.delivery.repository;

import com.delivery.model.EstadoPedido;
import com.delivery.model.Pedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    
    @EntityGraph(attributePaths = {"cliente", "detalles.producto", "repartidor"})
    Page<Pedido> findByClienteId(Long clienteId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"cliente", "detalles.producto", "repartidor"})
    Page<Pedido> findByEstado(EstadoPedido estado, Pageable pageable);
    
    @EntityGraph(attributePaths = {"cliente", "detalles.producto", "repartidor"})
    @Query("SELECT p FROM Pedido p WHERE p.repartidor.id = :repartidorId")
    Page<Pedido> findByRepartidorId(@Param("repartidorId") Long repartidorId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"cliente", "detalles.producto", "repartidor"})
    @Query("SELECT p FROM Pedido p WHERE p.estado = :estado AND p.repartidor IS NULL")
    Page<Pedido> findDisponiblesParaAsignar(@Param("estado") EstadoPedido estado, Pageable pageable);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @EntityGraph(attributePaths = {"cliente", "detalles.producto", "repartidor"})
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> findByIdWithLock(@Param("id") Long id);
    
    @EntityGraph(attributePaths = {"cliente", "detalles.producto", "repartidor"})
    Optional<Pedido> findById(Long id);
}
