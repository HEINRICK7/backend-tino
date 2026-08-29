package com.tino.backend.reconciliation.adapter.in.web;

import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.reconciliation.application.exception.ReconciliationConflictException;
import com.tino.backend.reconciliation.application.exception.ReconciliationNotFoundException;
import com.tino.backend.reconciliation.application.port.out.ReconciliationPersistenceException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = ReconciliationController.class)
public final class ReconciliationApiExceptionHandler {
    @ExceptionHandler(BusinessAuthorizationDeniedException.class)
    ResponseEntity<ErrorResponse> denied(RuntimeException exception) { return response(403, "RECONCILIATION_ACCESS_DENIED", "reconciliation access denied"); }
    @ExceptionHandler(ReconciliationNotFoundException.class)
    ResponseEntity<ErrorResponse> missing(RuntimeException exception) { return response(404, "RECONCILIATION_NOT_FOUND", "reconciliation run not found"); }
    @ExceptionHandler(ReconciliationConflictException.class)
    ResponseEntity<ErrorResponse> conflict(RuntimeException exception) { return response(409, "RECONCILIATION_CONFLICT", "reconciliation idempotency conflict"); }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(RuntimeException exception) { return response(400, "INVALID_RECONCILIATION_REQUEST", "invalid reconciliation request"); }
    @ExceptionHandler(ReconciliationPersistenceException.class)
    ResponseEntity<ErrorResponse> persistence(RuntimeException exception) { return response(500, "RECONCILIATION_OPERATION_FAILED", "reconciliation operation failed"); }
    private static ResponseEntity<ErrorResponse> response(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, MDC.get("correlationId")));
    }
    public record ErrorResponse(String code, String message, String correlationId) {}
}
