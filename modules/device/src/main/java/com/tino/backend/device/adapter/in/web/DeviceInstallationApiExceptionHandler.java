package com.tino.backend.device.adapter.in.web;

import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.device.application.exception.DeviceInstallationAccessDeniedException;
import com.tino.backend.device.application.exception.DeviceInstallationPersistenceException;
import com.tino.backend.device.application.exception.RevokedDeviceInstallationException;
import com.tino.backend.device.application.exception.UnauthenticatedDeviceRequestException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps Device failures to non-disclosing HTTP responses. */
@RestControllerAdvice(basePackageClasses = DeviceInstallationController.class)
public final class DeviceInstallationApiExceptionHandler {
    @ExceptionHandler(UnauthenticatedDeviceRequestException.class)
    ResponseEntity<ErrorResponse> unauthenticated(UnauthenticatedDeviceRequestException exception) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "authentication required");
    }

    @ExceptionHandler({
        DeviceInstallationAccessDeniedException.class,
        BusinessAuthorizationDeniedException.class,
        RevokedDeviceInstallationException.class
    })
    ResponseEntity<ErrorResponse> denied(RuntimeException exception) {
        return response(HttpStatus.FORBIDDEN, "DEVICE_ACCESS_DENIED", "device access denied");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_INSTALLATION_REQUEST", "invalid installation request");
    }

    @ExceptionHandler(DeviceInstallationPersistenceException.class)
    ResponseEntity<ErrorResponse> persistenceFailure(DeviceInstallationPersistenceException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "DEVICE_PERSISTENCE_FAILURE", "device operation failed");
    }

    private static ResponseEntity<ErrorResponse> response(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, MDC.get("correlationId")));
    }

    public record ErrorResponse(String code, String message, String correlationId) {}
}
