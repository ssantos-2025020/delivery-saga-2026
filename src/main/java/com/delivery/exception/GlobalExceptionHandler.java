package com.delivery.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBusinessException(BusinessException ex) {
        logger.warn("Business exception: {}", ex.getMessage());
        return new ErrorResponse(ex.getMessage());
    }
    
    @ExceptionHandler(StockInsuficienteException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleStockInsuficiente(StockInsuficienteException ex) {
        logger.warn("Stock insuficiente: {}", ex.getMessage());
        return new ErrorResponse(ex.getMessage());
    }
    
    @ExceptionHandler(InvalidStatusException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInvalidStatus(InvalidStatusException ex) {
        logger.warn("Estado inválido: {}", ex.getMessage());
        return new ErrorResponse(ex.getMessage());
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        logger.warn("Validación fallida: {}", message);
        return new ErrorResponse("Error de validación: " + message);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        logger.warn("Violación de restricción: {}", message);
        return new ErrorResponse("Error de validación: " + message);
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        logger.warn("JSON malformado: {}", ex.getMessage());
        return new ErrorResponse("JSON malformado o inválido");
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        logger.warn("Tipo de argumento inválido: {}", ex.getMessage());
        return new ErrorResponse("Parámetro inválido: " + ex.getName());
    }
    
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingParameter(MissingServletRequestParameterException ex) {
        logger.warn("Parámetro faltante: {}", ex.getMessage());
        return new ErrorResponse("Parámetro requerido faltante: " + ex.getParameterName());
    }
    
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ErrorResponse handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        logger.warn("Método no soportado: {}", ex.getMessage());
        return new ErrorResponse("Método HTTP no soportado");
    }
    
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ErrorResponse handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        logger.warn("Tipo de media no soportado: {}", ex.getMessage());
        return new ErrorResponse("Tipo de contenido no soportado");
    }
    
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        logger.warn("Conflicto de bloqueo optimista: {}", ex.getMessage());
        return new ErrorResponse("El recurso fue modificado por otro usuario. Intente nuevamente.");
    }
    
    @ExceptionHandler({LockTimeoutException.class, PessimisticLockException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleLockTimeout(Exception ex) {
        logger.warn("Timeout de bloqueo: {}", ex.getMessage());
        return new ErrorResponse("El recurso está siendo modificado por otro usuario. Intente nuevamente.");
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        logger.warn("Violación de integridad: {}", ex.getMessage());
        return new ErrorResponse("Violación de integridad de datos. El recurso ya existe o está siendo modificado.");
    }
    
    @ExceptionHandler(CannotCreateTransactionException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleTransactionException(CannotCreateTransactionException ex) {
        logger.error("Error de conexión a base de datos", ex);
        return new ErrorResponse("Servicio temporalmente no disponible. Intente más tarde.");
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        logger.warn("Acceso denegado: {}", ex.getMessage());
        return new ErrorResponse("No tiene permisos para realizar esta acción");
    }
    
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NoHandlerFoundException ex) {
        logger.warn("Recurso no encontrado: {}", ex.getRequestURL());
        return new ErrorResponse("Recurso no encontrado");
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGenericException(Exception ex) {
        logger.error("Error interno del servidor", ex);
        return new ErrorResponse("Error interno del servidor");
    }
    
    public record ErrorResponse(String error) {}
}
