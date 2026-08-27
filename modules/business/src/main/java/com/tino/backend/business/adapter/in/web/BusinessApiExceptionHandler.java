package com.tino.backend.business.adapter.in.web;

import com.tino.backend.business.application.exception.BusinessAccessDeniedException;
import com.tino.backend.business.application.exception.InactiveAuthenticatedUserException;
import com.tino.backend.business.application.port.out.BusinessPersistenceException;
import com.tino.backend.business.application.port.out.DuplicateMembershipException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps Business failures to safe structured errors without exposing persistence details. */
@RestControllerAdvice(basePackageClasses = BusinessController.class)
public final class BusinessApiExceptionHandler {
    @ExceptionHandler({
        BusinessAccessDeniedException.class,
        InactiveAuthenticatedUserException.class
    })
    ResponseEntity<ErrorResponse> forbidden(RuntimeException exception) {
        return response(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "business access denied");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_BUSINESS_REQUEST", "invalid business request");
    }

    @ExceptionHandler(DuplicateMembershipException.class)
    ResponseEntity<ErrorResponse> duplicateMembership(DuplicateMembershipException exception) {
        return response(HttpStatus.CONFLICT, "MEMBERSHIP_EXISTS", "business membership already exists");
    }

    @ExceptionHandler(BusinessPersistenceException.class)
    ResponseEntity<ErrorResponse> persistenceFailure(BusinessPersistenceException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "BUSINESS_PERSISTENCE_FAILURE", "business operation failed");
    }

    private static ResponseEntity<ErrorResponse> response(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, MDC.get("correlationId")));
    }

    public record ErrorResponse(String code, String message, String correlationId) {}
}
