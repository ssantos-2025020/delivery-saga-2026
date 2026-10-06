package com.delivery.catalogo.repository;

import com.delivery.catalogo.model.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    
    Page<Producto> findByComercioId(Long comercioId, Pageable pageable);
    
    @Query("SELECT p FROM Producto p WHERE p.comercio.id = :comercioId AND p.stock > 0")
    Page<Producto> findDisponiblesByComercio(@Param("comercioId") Long comercioId, Pageable pageable);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT p FROM Producto p WHERE p.id IN :ids ORDER BY p.id ASC")
    List<Producto> findByIdsWithLock(@Param("ids") List<Long> ids);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT p FROM Producto p WHERE p.id = :id")
    Optional<Producto> findByIdWithLock(@Param("id") Long id);
}
