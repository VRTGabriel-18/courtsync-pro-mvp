package com.courtsync.pro.repository;

import com.courtsync.pro.domain.entity.Cancha;
import com.courtsync.pro.domain.entity.TipoCancha;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CanchaRepository extends JpaRepository<Cancha, Long> {

    List<Cancha> findByActivaTrue();

    List<Cancha> findByActivaTrueAndTipoCancha(TipoCancha tipoCancha);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cancha c WHERE c.id = :id AND c.activa = true")
    Optional<Cancha> findByIdWithLock(@Param("id") Long id);
}
