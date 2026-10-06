package com.delivery.repository;

import com.delivery.model.Comercio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ComercioRepository extends JpaRepository<Comercio, Long> {
    
    @Query("SELECT c FROM Comercio c WHERE c.abierto = true")
    Page<Comercio> findComerciosAbiertos(Pageable pageable);
    
    @Query("SELECT c FROM Comercio c WHERE c.categoria = :categoria AND c.abierto = true")
    Page<Comercio> findByCategoriaAndAbierto(@Param("categoria") String categoria, Pageable pageable);
}
