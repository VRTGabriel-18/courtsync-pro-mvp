package com.courtsync.pro.service;

import com.courtsync.pro.domain.entity.TipoCancha;
import com.courtsync.pro.dto.DisponibilidadCanchaResponse;

import java.time.LocalDate;
import java.util.List;

public interface CanchaService {

    List<DisponibilidadCanchaResponse> consultarDisponibilidad(LocalDate fecha, TipoCancha tipoCancha);
}
