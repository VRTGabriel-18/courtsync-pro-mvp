package com.courtsync.pro.dto;

import com.courtsync.pro.domain.entity.EstadoReserva;

import java.time.OffsetDateTime;

public record ReservaResponse(
        Long reservaId,
        String codigoConfirmacion,
        Long canchaId,
        String canchaNombre,
        OffsetDateTime fechaHoraInicio,
        Integer duracionHoras,
        Double valorTotal,
        Double valorAnticipo,
        EstadoReserva estado,
        OffsetDateTime fechaExpiracionPago
) {
}
