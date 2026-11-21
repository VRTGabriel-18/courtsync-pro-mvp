package com.courtsync.pro.exception;

import lombok.Getter;

@Getter
public class FechaInvalidaException extends RuntimeException {

    private final String errorCode;

    public FechaInvalidaException(String message) {
        this(message, "BAD_REQUEST_FECHA_INVALIDA");
    }

    public FechaInvalidaException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
