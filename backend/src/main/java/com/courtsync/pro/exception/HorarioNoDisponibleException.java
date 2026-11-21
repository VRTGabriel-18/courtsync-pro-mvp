package com.courtsync.pro.exception;

import lombok.Getter;

@Getter
public class HorarioNoDisponibleException extends RuntimeException {

    private final String errorCode;

    public HorarioNoDisponibleException(String message) {
        this(message, "CONFLICT_HORARIO_OCUPADO");
    }

    public HorarioNoDisponibleException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
