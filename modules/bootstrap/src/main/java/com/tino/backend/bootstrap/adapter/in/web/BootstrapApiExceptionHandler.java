package com.tino.backend.bootstrap.adapter.in.web;

import com.tino.backend.bootstrap.application.exception.BootstrapAccessDeniedException;
import com.tino.backend.bootstrap.application.exception.BootstrapAuthenticationRequiredException;
import com.tino.backend.bootstrap.application.exception.BootstrapContextUnavailableException;
import com.tino.backend.business.application.port.in.BusinessContextUnavailableException;
import com.tino.backend.device.application.port.in.DeviceContextUnavailableException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Safe M5 errors with no tenant, provider, or persistence disclosure. */
@RestControllerAdvice(basePackageClasses = BootstrapController.class)
public final class BootstrapApiExceptionHandler {
    @ExceptionHandler(BootstrapAuthenticationRequiredException.class)
    ResponseEntity<ErrorResponse> unauthenticated(BootstrapAuthenticationRequiredException exception) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "authentication required");
    }

    @ExceptionHandler(BootstrapAccessDeniedException.class)
    ResponseEntity<ErrorResponse> denied(BootstrapAccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "BOOTSTRAP_ACCESS_DENIED", "bootstrap access denied");
    }

    @ExceptionHandler({
        BootstrapContextUnavailableException.class,
        BusinessContextUnavailableException.class,
        DeviceContextUnavailableException.class
    })
    ResponseEntity<ErrorResponse> unavailable(RuntimeException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "BOOTSTRAP_UNAVAILABLE", "bootstrap unavailable");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_BOOTSTRAP_REQUEST", "invalid bootstrap request");
    }

    private static ResponseEntity<ErrorResponse> response(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, MDC.get("correlationId")));
    }

    public record ErrorResponse(String code, String message, String correlationId) {}
}
