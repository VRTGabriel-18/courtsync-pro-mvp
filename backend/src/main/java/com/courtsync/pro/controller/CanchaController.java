package com.courtsync.pro.controller;

import com.courtsync.pro.domain.entity.TipoCancha;
import com.courtsync.pro.dto.DisponibilidadCanchaResponse;
import com.courtsync.pro.service.CanchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/canchas")
@RequiredArgsConstructor
public class CanchaController {

    private final CanchaService canchaService;

    @GetMapping("/disponibilidad")
    public ResponseEntity<List<DisponibilidadCanchaResponse>> consultarDisponibilidad(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) TipoCancha tipoCancha
    ) {
        return ResponseEntity.ok(canchaService.consultarDisponibilidad(fecha, tipoCancha));
    }
}
