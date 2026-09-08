package com.tino.backend.customerchannel.adapter.in.web;

import com.tino.backend.customerchannel.application.exception.CustomerChannelAccessDeniedException;
import com.tino.backend.customerchannel.application.exception.CustomerInviteInvalidException;
import com.tino.backend.customerchannel.application.exception.CustomerInvitePhoneMissingException;
import com.tino.backend.customerchannel.application.exception.CustomerSessionRequiredException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = CustomerChannelController.class)
public final class CustomerChannelApiExceptionHandler {
    @ExceptionHandler(CustomerSessionRequiredException.class)
    ResponseEntity<ErrorResponse> unauthenticated(RuntimeException exception) {
        return response(HttpStatus.UNAUTHORIZED, "CUSTOMER_SESSION_REQUIRED", "customer session required");
    }

    @ExceptionHandler(CustomerChannelAccessDeniedException.class)
    ResponseEntity<ErrorResponse> forbidden(RuntimeException exception) {
        return response(HttpStatus.FORBIDDEN, "CHANNEL_INACTIVE", "customer channel is not active");
    }

    @ExceptionHandler(CustomerInviteInvalidException.class)
    ResponseEntity<ErrorResponse> invalidInvite(RuntimeException exception) {
        return response(HttpStatus.GONE, "INVITE_INVALID", "this invite is no longer valid");
    }

    @ExceptionHandler(CustomerInvitePhoneMissingException.class)
    ResponseEntity<ErrorResponse> phoneMissing(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVITE_PHONE_REQUIRED", "customer phone is required");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "invalid customer-channel request");
    }

    private static ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, MDC.get("correlationId")));
    }

    public record ErrorResponse(String code, String message, String correlationId) {}
}
