package com.tino.backend.receiving.adapter.in.web;

import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.fiscal.application.exception.NfeDocumentNotFoundException;
import com.tino.backend.fiscal.application.exception.NfeIdempotencyConflictException;
import com.tino.backend.receiving.application.exception.ReceivingErrorCode;
import com.tino.backend.receiving.application.exception.ReceivingException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice(basePackageClasses = ReceivingController.class)
public final class ReceivingApiExceptionHandler {
    @ExceptionHandler(ReceivingException.class)
    ResponseEntity<ReceivingController.ErrorResponse> receiving(ReceivingException exception) {
        return response(HttpStatus.valueOf(exception.httpStatus()), exception.code().name(),
                exception.getMessage(), exception.retryable());
    }

    @ExceptionHandler(BusinessAuthorizationDeniedException.class)
    ResponseEntity<ReceivingController.ErrorResponse> denied() {
        return response(HttpStatus.FORBIDDEN, ReceivingErrorCode.BUSINESS_ACCESS_DENIED.name(),
                "business access denied", false);
    }

    @ExceptionHandler(NfeIdempotencyConflictException.class)
    ResponseEntity<ReceivingController.ErrorResponse> idempotencyConflict() {
        return response(HttpStatus.CONFLICT, ReceivingErrorCode.IDEMPOTENCY_CONFLICT.name(),
                "Idempotency-Key was already used for another NF-e", false);
    }

    @ExceptionHandler(NfeDocumentNotFoundException.class)
    ResponseEntity<ReceivingController.ErrorResponse> fiscalNotFound() {
        return response(HttpStatus.NOT_FOUND, ReceivingErrorCode.NFE_NOT_FOUND.name(),
                "fiscal document not found", false);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ReceivingController.ErrorResponse> invalid(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, ReceivingErrorCode.INVALID_REQUEST.name(),
                exception.getMessage(), false);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ReceivingController.ErrorResponse> invalidBody() {
        return response(HttpStatus.BAD_REQUEST, ReceivingErrorCode.INVALID_REQUEST.name(),
                "invalid request", false);
    }

    private static ResponseEntity<ReceivingController.ErrorResponse> response(
            HttpStatus status, String code, String message, boolean retryable) {
        return ResponseEntity.status(status).body(new ReceivingController.ErrorResponse(
                code, message, retryable, MDC.get("correlationId")));
    }
}
