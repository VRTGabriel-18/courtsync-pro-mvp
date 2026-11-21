package com.courtsync.pro.dto;

import com.courtsync.pro.domain.entity.TipoCancha;

import java.util.List;

public record DisponibilidadCanchaResponse(
        Long canchaId,
        String nombre,
        TipoCancha tipoCancha,
        Double tarifaHora,
        List<FranjaHorariaDTO> franjas
) {
}
