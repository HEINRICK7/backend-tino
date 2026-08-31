package com.tino.backend.external.adapter.in.web;

import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.external.adapter.out.persistence.JooqExternalBusinessConnectionRepository.ExternalSyncAlreadyRunningException;
import com.tino.backend.external.application.exception.ExternalConnectionNotFoundException;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = {ExternalBusinessDataSourceController.class, BusinessDataSourceController.class})
public final class ExternalBusinessDataSourceApiExceptionHandler {
    @ExceptionHandler(BusinessAuthorizationDeniedException.class)
    ResponseEntity<ErrorResponse> denied() { return response(403, "EXTERNAL_CONNECTION_ACCESS_DENIED", "business access denied"); }

    @ExceptionHandler(ExternalConnectionNotFoundException.class)
    ResponseEntity<ErrorResponse> missing() { return response(404, "EXTERNAL_CONNECTION_NOT_FOUND", "external connection not found"); }

    @ExceptionHandler(ExternalSyncAlreadyRunningException.class)
    ResponseEntity<ErrorResponse> running() { return response(409, "EXTERNAL_SYNC_ALREADY_RUNNING", "external catalog sync already running"); }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid() { return response(400, "INVALID_EXTERNAL_CONNECTION_REQUEST", "invalid external connection request"); }

    private static ResponseEntity<ErrorResponse> response(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, MDC.get("correlationId")));
    }

    public record ErrorResponse(String code, String message, String correlationId) {}
}
