package com.tino.backend.customerchannel.adapter.in.web;

import com.tino.backend.customerchannel.application.exception.CustomerChannelAccessDeniedException;
import com.tino.backend.customerchannel.application.exception.CustomerChannelActivationConflictException;
import com.tino.backend.customerchannel.application.exception.CustomerChannelInviteConflictException;
import com.tino.backend.customerchannel.application.exception.CustomerInviteInvalidException;
import com.tino.backend.customerchannel.application.exception.CustomerInvitePhoneMissingException;
import com.tino.backend.customerchannel.application.exception.CustomerInvitePhoneInvalidException;
import com.tino.backend.customerchannel.application.exception.CustomerSessionRequiredException;
import com.tino.backend.customerchannel.application.exception.CustomerPushDisabledException;
import com.tino.backend.customerchannel.application.exception.CustomerPushSubscriptionInvalidException;
import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.business.application.port.in.BusinessPixUnavailableException;
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

    @ExceptionHandler(BusinessAuthorizationDeniedException.class)
    ResponseEntity<ErrorResponse> businessForbidden(RuntimeException exception) {
        return response(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "business access denied");
    }

    @ExceptionHandler(CustomerInviteInvalidException.class)
    ResponseEntity<ErrorResponse> invalidInvite(RuntimeException exception) {
        return response(HttpStatus.GONE, "INVITE_INVALID", "this invite is no longer valid");
    }

    @ExceptionHandler(CustomerInvitePhoneMissingException.class)
    ResponseEntity<ErrorResponse> phoneMissing(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVITE_PHONE_REQUIRED", "customer phone is required");
    }

    @ExceptionHandler(CustomerInvitePhoneInvalidException.class)
    ResponseEntity<ErrorResponse> phoneInvalid(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVITE_PHONE_INVALID", "customer phone is invalid");
    }

    @ExceptionHandler(CustomerChannelInviteConflictException.class)
    ResponseEntity<ErrorResponse> idempotencyConflict(RuntimeException exception) {
        return response(HttpStatus.CONFLICT, "INVITE_IDEMPOTENCY_CONFLICT",
                "Idempotency-Key was already used for another customer-channel invite");
    }

    @ExceptionHandler(CustomerChannelActivationConflictException.class)
    ResponseEntity<ErrorResponse> activationIdempotencyConflict(RuntimeException exception) {
        return response(HttpStatus.CONFLICT, "ACTIVATION_IDEMPOTENCY_CONFLICT",
                "Idempotency-Key was already used for another customer-channel activation");
    }

    @ExceptionHandler(CustomerPushDisabledException.class)
    ResponseEntity<ErrorResponse> pushDisabled(RuntimeException exception) {
        return response(HttpStatus.NOT_FOUND, "PUSH_DISABLED", "customer push is not enabled");
    }

    @ExceptionHandler(CustomerPushSubscriptionInvalidException.class)
    ResponseEntity<ErrorResponse> invalidPushSubscription(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, "PUSH_SUBSCRIPTION_INVALID", "push subscription is invalid");
    }

    @ExceptionHandler(BusinessPixUnavailableException.class)
    ResponseEntity<ErrorResponse> pixUnavailable(RuntimeException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "PIX_UNAVAILABLE", "Pix is temporarily unavailable");
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
