package com.courtsync.pro.controller;

import com.courtsync.pro.dto.CrearReservaRequest;
import com.courtsync.pro.dto.ReservaResponse;
import com.courtsync.pro.service.ReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    @PostMapping
    public ResponseEntity<ReservaResponse> crearReserva(
            @Valid @RequestBody CrearReservaRequest request,
            @RequestHeader(value = "X-Usuario-Id", defaultValue = "1") Long usuarioId
    ) {
        ReservaResponse response = reservaService.crearReserva(request, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
