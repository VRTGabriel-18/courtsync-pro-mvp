package com.courtsync.pro.repository;

import com.courtsync.pro.domain.entity.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    @Query("""
            SELECT r FROM Reserva r
            WHERE r.cancha.id = :canchaId
              AND r.fechaHoraInicio < :fin
              AND r.fechaHoraFin > :inicio
              AND (
                    r.estado = com.courtsync.pro.domain.entity.EstadoReserva.CONFIRMADA
                    OR (
                        r.estado = com.courtsync.pro.domain.entity.EstadoReserva.PENDIENTE_PAGO
                        AND r.fechaExpiracionPago > :ahora
                    )
              )
            """)
    List<Reserva> findReservasActivasEnRangoPorCancha(
            @Param("canchaId") Long canchaId,
            @Param("inicio") OffsetDateTime inicio,
            @Param("fin") OffsetDateTime fin,
            @Param("ahora") OffsetDateTime ahora
    );

    @Query("""
            SELECT COUNT(r) FROM Reserva r
            WHERE r.usuarioId = :usuarioId
              AND r.fechaHoraInicio >= :inicioDia
              AND r.fechaHoraInicio < :finDia
              AND (
                    r.estado = com.courtsync.pro.domain.entity.EstadoReserva.CONFIRMADA
                    OR (
                        r.estado = com.courtsync.pro.domain.entity.EstadoReserva.PENDIENTE_PAGO
                        AND r.fechaExpiracionPago > :ahora
                    )
              )
            """)
    long countReservasActivasPorUsuarioEnFecha(
            @Param("usuarioId") Long usuarioId,
            @Param("inicioDia") OffsetDateTime inicioDia,
            @Param("finDia") OffsetDateTime finDia,
            @Param("ahora") OffsetDateTime ahora
    );

    @Query("""
            SELECT r FROM Reserva r
            WHERE r.cancha.id IN :canchaIds
              AND r.fechaHoraInicio < :finDia
              AND r.fechaHoraFin > :inicioDia
              AND (
                    r.estado = com.courtsync.pro.domain.entity.EstadoReserva.CONFIRMADA
                    OR (
                        r.estado = com.courtsync.pro.domain.entity.EstadoReserva.PENDIENTE_PAGO
                        AND r.fechaExpiracionPago > :ahora
                    )
              )
            """)
    List<Reserva> findReservasActivasPorCanchasEnFecha(
            @Param("canchaIds") List<Long> canchaIds,
            @Param("inicioDia") OffsetDateTime inicioDia,
            @Param("finDia") OffsetDateTime finDia,
            @Param("ahora") OffsetDateTime ahora
    );
}
