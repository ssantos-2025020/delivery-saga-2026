package com.delivery.catalogo.repository;

import com.delivery.catalogo.model.StockReserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockReservaRepository extends JpaRepository<StockReserva, Long> {
    
    Optional<StockReserva> findByReservaId(String reservaId);
    
    List<StockReserva> findByReservaIdIn(List<String> reservaIds);
    
    @Query("SELECT sr FROM StockReserva sr WHERE sr.estado = 'RESERVADA' AND sr.fechaExpiracion < :now")
    List<StockReserva> findReservasExpiradas(@Param("now") LocalDateTime now);
}
