package com.courtsync.pro.service;

import com.courtsync.pro.dto.CrearReservaRequest;
import com.courtsync.pro.dto.ReservaResponse;

public interface ReservaService {

    ReservaResponse crearReserva(CrearReservaRequest request, Long usuarioId);
}
