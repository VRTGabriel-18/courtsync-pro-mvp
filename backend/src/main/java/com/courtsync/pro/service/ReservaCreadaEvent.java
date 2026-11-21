package com.courtsync.pro.service;

import java.time.OffsetDateTime;

public record ReservaCreadaEvent(
        Long reservaId,
        String codigoConfirmacion,
        Long usuarioId,
        String canchaNombre,
        OffsetDateTime fechaHoraInicio,
        Double valorAnticipo,
        OffsetDateTime fechaExpiracionPago
) {
}
