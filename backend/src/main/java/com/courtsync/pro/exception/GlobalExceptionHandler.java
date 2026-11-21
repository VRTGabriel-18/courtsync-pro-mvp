package com.courtsync.pro.exception;

import com.courtsync.pro.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST_VALIDACION", message, request);
    }

    @ExceptionHandler({
            FechaInvalidaException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(
            Exception ex,
            HttpServletRequest request
    ) {
        String errorCode = ex instanceof FechaInvalidaException fie
                ? fie.getErrorCode()
                : "BAD_REQUEST_PARAMETRO_INVALIDO";
        String message = ex instanceof FechaInvalidaException
                ? ex.getMessage()
                : "Parámetro o formato de solicitud inválido: " + ex.getMessage();
        return buildError(HttpStatus.BAD_REQUEST, errorCode, message, request);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            RecursoNoEncontradoException ex,
            HttpServletRequest request
    ) {
        return buildError(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(HorarioNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            HorarioNoDisponibleException ex,
            HttpServletRequest request
    ) {
        return buildError(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessableEntity(
            ReglaNegocioException ex,
            HttpServletRequest request
    ) {
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> buildError(
            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request
    ) {
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                status.value(),
                errorCode,
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
