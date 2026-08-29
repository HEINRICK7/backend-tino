package com.tino.backend.customer.adapter.in.web;

import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.customer.application.exception.CustomerAccessDeniedException;
import com.tino.backend.customer.application.exception.CustomerConflictException;
import com.tino.backend.customer.application.exception.CustomerNotFoundException;
import com.tino.backend.customer.application.exception.CustomerUnauthenticatedException;
import com.tino.backend.customer.application.port.out.CustomerPersistenceException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = CustomerController.class)
public final class CustomerApiExceptionHandler {
    @ExceptionHandler(CustomerUnauthenticatedException.class)
    ResponseEntity<ErrorResponse> unauthenticated(CustomerUnauthenticatedException exception) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "authentication required");
    }

    @ExceptionHandler({CustomerAccessDeniedException.class, BusinessAuthorizationDeniedException.class})
    ResponseEntity<ErrorResponse> forbidden(RuntimeException exception) {
        return response(HttpStatus.FORBIDDEN, "CUSTOMER_ACCESS_DENIED", "customer access denied");
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(CustomerNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "customer not found");
    }

    @ExceptionHandler(CustomerConflictException.class)
    ResponseEntity<ErrorResponse> conflict(CustomerConflictException exception) {
        return response(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "idempotency key conflict");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_CUSTOMER_REQUEST", "invalid customer request");
    }

    @ExceptionHandler(CustomerPersistenceException.class)
    ResponseEntity<ErrorResponse> persistence(CustomerPersistenceException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "CUSTOMER_OPERATION_FAILED", "customer operation failed");
    }

    private static ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, MDC.get("correlationId")));
    }

    public record ErrorResponse(String code, String message, String correlationId) {}
}
