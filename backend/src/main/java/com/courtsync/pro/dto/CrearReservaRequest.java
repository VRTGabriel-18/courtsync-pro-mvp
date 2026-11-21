package com.courtsync.pro.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CrearReservaRequest(
        @NotNull(message = "El campo canchaId es obligatorio")
        Long canchaId,

        @NotNull(message = "El campo fechaHoraInicio es obligatorio")
        OffsetDateTime fechaHoraInicio,

        @NotNull(message = "El campo duracionHoras es obligatorio")
        @Min(value = 1, message = "La duración mínima es de 1 hora")
        @Max(value = 2, message = "La duración máxima es de 2 horas")
        Integer duracionHoras
) {
}
