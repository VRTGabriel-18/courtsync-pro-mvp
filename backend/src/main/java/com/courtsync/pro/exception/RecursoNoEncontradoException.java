package com.courtsync.pro.exception;

import lombok.Getter;

@Getter
public class RecursoNoEncontradoException extends RuntimeException {

    private final String errorCode;

    public RecursoNoEncontradoException(String message) {
        this(message, "NOT_FOUND_RECURSO");
    }

    public RecursoNoEncontradoException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
