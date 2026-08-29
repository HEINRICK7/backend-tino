package com.tino.backend.credit.adapter.in.web;

import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.credit.application.exception.CreditAccessDeniedException;
import com.tino.backend.credit.application.exception.CreditCompensationException;
import com.tino.backend.credit.application.exception.CreditConflictException;
import com.tino.backend.credit.application.exception.CreditCustomerNotFoundException;
import com.tino.backend.credit.application.exception.CreditEntryNotFoundException;
import com.tino.backend.credit.application.exception.CreditInsufficientBalanceException;
import com.tino.backend.credit.application.exception.CreditUnauthenticatedException;
import com.tino.backend.credit.application.port.out.CreditPersistenceException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice(basePackageClasses = CreditController.class)
public final class CreditApiExceptionHandler {
    @ExceptionHandler(CreditUnauthenticatedException.class)
    ResponseEntity<ErrorResponse> unauthenticated(CreditUnauthenticatedException exception) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "authentication required");
    }

    @ExceptionHandler({CreditAccessDeniedException.class, BusinessAuthorizationDeniedException.class})
    ResponseEntity<ErrorResponse> denied(RuntimeException exception) {
        return response(HttpStatus.FORBIDDEN, "CREDIT_ACCESS_DENIED", "credit access denied");
    }

    @ExceptionHandler(CreditCustomerNotFoundException.class)
    ResponseEntity<ErrorResponse> customerNotFound(CreditCustomerNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "customer not found");
    }

    @ExceptionHandler(CreditEntryNotFoundException.class)
    ResponseEntity<ErrorResponse> entryNotFound(CreditEntryNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "CREDIT_ENTRY_NOT_FOUND", "credit entry not found");
    }

    @ExceptionHandler(CreditConflictException.class)
    ResponseEntity<ErrorResponse> conflict(CreditConflictException exception) {
        return response(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "idempotency key conflict");
    }

    @ExceptionHandler(CreditCompensationException.class)
    ResponseEntity<ErrorResponse> compensationConflict(CreditCompensationException exception) {
        return response(HttpStatus.CONFLICT, "CREDIT_COMPENSATION_CONFLICT", "credit compensation conflict");
    }

    @ExceptionHandler(CreditInsufficientBalanceException.class)
    ResponseEntity<ErrorResponse> insufficient(CreditInsufficientBalanceException exception) {
        return response(HttpStatus.valueOf(422), "INSUFFICIENT_CREDIT_BALANCE",
                "insufficient credit balance");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_CREDIT_REQUEST", "invalid credit request");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_CREDIT_REQUEST", "invalid credit request");
    }

    @ExceptionHandler(CreditPersistenceException.class)
    ResponseEntity<ErrorResponse> persistence(CreditPersistenceException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "CREDIT_OPERATION_FAILED", "credit operation failed");
    }

    private static ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, MDC.get("correlationId")));
    }

    public record ErrorResponse(String code, String message, String correlationId) {}
}
