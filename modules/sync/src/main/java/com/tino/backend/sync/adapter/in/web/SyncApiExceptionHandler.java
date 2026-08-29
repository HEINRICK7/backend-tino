package com.tino.backend.sync.adapter.in.web;

import com.tino.backend.sync.application.exception.SyncPersistenceException;
import com.tino.backend.sync.application.exception.SyncUnavailableException;
import com.tino.backend.sync.application.exception.SyncAccessDeniedException;
import com.tino.backend.sync.application.exception.UnauthenticatedSyncRequestException;
import com.tino.backend.sync.application.exception.SyncBusinessContextRequiredException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Safe Sync errors without persistence, tenant, or authentication disclosure. */
@RestControllerAdvice(basePackageClasses = SyncController.class)
public final class SyncApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_SYNC_REQUEST", "invalid sync request");
    }

    @ExceptionHandler(UnauthenticatedSyncRequestException.class)
    ResponseEntity<ErrorResponse> unauthenticated(UnauthenticatedSyncRequestException exception) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "authentication required");
    }

    @ExceptionHandler(SyncAccessDeniedException.class)
    ResponseEntity<ErrorResponse> denied(SyncAccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "access denied");
    }

    @ExceptionHandler(SyncBusinessContextRequiredException.class)
    ResponseEntity<ErrorResponse> businessContextRequired(
            SyncBusinessContextRequiredException exception) {
        return response(HttpStatus.BAD_REQUEST, "BUSINESS_CONTEXT_REQUIRED", "business context required");
    }

    @ExceptionHandler({SyncUnavailableException.class, SyncPersistenceException.class})
    ResponseEntity<ErrorResponse> unavailable(RuntimeException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "SYNC_UNAVAILABLE", "sync unavailable");
    }

    private static ResponseEntity<ErrorResponse> response(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, MDC.get("correlationId")));
    }

    public record ErrorResponse(String code, String message, String correlationId) {}
}
