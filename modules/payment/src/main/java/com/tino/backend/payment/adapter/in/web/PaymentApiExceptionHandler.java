package com.tino.backend.payment.adapter.in.web;

import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.payment.application.exception.PaymentConflictException;
import com.tino.backend.payment.application.exception.PaymentCustomerNotFoundException;
import com.tino.backend.payment.application.exception.PaymentNotFoundException;
import com.tino.backend.payment.application.port.out.PaymentPersistenceException;
import com.tino.backend.payment.application.exception.PaymentProviderException;
import com.tino.backend.payment.application.exception.PaymentTransitionException;
import com.tino.backend.payment.application.exception.PaymentUnauthenticatedException;
import com.tino.backend.payment.application.exception.PaymentWebhookUnauthorizedException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = PaymentController.class)
public final class PaymentApiExceptionHandler {
    @ExceptionHandler(PaymentUnauthenticatedException.class)
    ResponseEntity<ErrorResponse> unauthenticated(RuntimeException exception) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "authentication required");
    }
    @ExceptionHandler(BusinessAuthorizationDeniedException.class)
    ResponseEntity<ErrorResponse> denied(RuntimeException exception) {
        return response(HttpStatus.FORBIDDEN, "PAYMENT_ACCESS_DENIED", "payment access denied");
    }
    @ExceptionHandler(PaymentCustomerNotFoundException.class)
    ResponseEntity<ErrorResponse> customer(RuntimeException exception) {
        return response(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "customer not found");
    }
    @ExceptionHandler(PaymentNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(RuntimeException exception) {
        return response(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "payment not found");
    }
    @ExceptionHandler({PaymentConflictException.class, PaymentTransitionException.class})
    ResponseEntity<ErrorResponse> conflict(RuntimeException exception) {
        return response(HttpStatus.CONFLICT, "PAYMENT_CONFLICT", "payment request conflicts with its current state");
    }
    @ExceptionHandler(PaymentWebhookUnauthorizedException.class)
    ResponseEntity<ErrorResponse> webhookUnauthorized(RuntimeException exception) {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_PROVIDER_SIGNATURE", "invalid provider signature");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_REQUEST", "invalid payment request");
    }
    @ExceptionHandler(PaymentProviderException.class)
    ResponseEntity<ErrorResponse> provider(RuntimeException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_UNAVAILABLE", "payment provider unavailable");
    }
    @ExceptionHandler(PaymentPersistenceException.class)
    ResponseEntity<ErrorResponse> persistence(RuntimeException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT_OPERATION_FAILED", "payment operation failed");
    }
    private static ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, MDC.get("correlationId")));
    }
    public record ErrorResponse(String code, String message, String correlationId) {}
}
