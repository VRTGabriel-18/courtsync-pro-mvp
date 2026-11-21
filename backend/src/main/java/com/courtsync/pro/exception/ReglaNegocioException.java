package com.courtsync.pro.exception;

import lombok.Getter;

@Getter
public class ReglaNegocioException extends RuntimeException {

    private final String errorCode;

    public ReglaNegocioException(String message) {
        this(message, "UNPROCESSABLE_REGLA_NEGOCIO");
    }

    public ReglaNegocioException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
