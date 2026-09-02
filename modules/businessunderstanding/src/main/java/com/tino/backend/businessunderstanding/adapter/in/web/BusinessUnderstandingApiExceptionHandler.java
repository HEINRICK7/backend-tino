package com.tino.backend.businessunderstanding.adapter.in.web;

import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.businessunderstanding.application.exception.BusinessNotReadyException;
import com.tino.backend.businessunderstanding.application.exception.BusinessUnderstandingNotFoundException;
import com.tino.backend.businessunderstanding.application.exception.BusinessUnderstandingPersistenceException;
import com.tino.backend.businessunderstanding.application.exception.InvalidBusinessUnderstandingException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = BusinessUnderstandingController.class)
public final class BusinessUnderstandingApiExceptionHandler {
    @ExceptionHandler(BusinessAuthorizationDeniedException.class)
    ResponseEntity<ErrorResponse> denied() {
        return response(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "business access denied");
    }

    @ExceptionHandler(InvalidBusinessUnderstandingException.class)
    ResponseEntity<ErrorResponse> invalid(InvalidBusinessUnderstandingException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.code(), "invalid business understanding request");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidArgument() {
        return response(HttpStatus.BAD_REQUEST, "INVALID_BUSINESS_UNDERSTANDING_REQUEST",
                "invalid business understanding request");
    }

    @ExceptionHandler(BusinessNotReadyException.class)
    ResponseEntity<ErrorResponse> notReady() {
        return response(HttpStatus.CONFLICT, "BUSINESS_NOT_READY", "business understanding is not ready");
    }

    @ExceptionHandler(BusinessUnderstandingNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound() {
        return response(HttpStatus.NOT_FOUND, "BUSINESS_UNDERSTANDING_NOT_FOUND", "business understanding item not found");
    }

    @ExceptionHandler(BusinessUnderstandingPersistenceException.class)
    ResponseEntity<ErrorResponse> persistence() {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "BUSINESS_UNDERSTANDING_OPERATION_FAILED",
                "business understanding operation failed");
    }

    private static ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, MDC.get("correlationId")));
    }

    public record ErrorResponse(String code, String message, String correlationId) {}
}
