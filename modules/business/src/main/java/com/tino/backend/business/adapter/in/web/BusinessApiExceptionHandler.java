package com.tino.backend.business.adapter.in.web;

import com.tino.backend.business.application.exception.BusinessAccessDeniedException;
import com.tino.backend.business.application.exception.InactiveAuthenticatedUserException;
import com.tino.backend.business.application.port.out.BusinessPersistenceException;
import com.tino.backend.business.application.port.out.DuplicateMembershipException;
import com.tino.backend.business.domain.model.PhoneChangePersistenceException;
import com.tino.backend.identity.application.exception.PhoneIdentityConflictException;
import com.tino.backend.identity.application.exception.PhoneIdentityOperationException;
import com.tino.backend.identity.application.exception.OtpRateLimitedException;
import com.tino.backend.identity.application.exception.OtpDeliveryException;
import com.tino.backend.identity.application.exception.OtpVerificationException;
import org.springframework.http.HttpHeaders;
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

    @ExceptionHandler(PhoneChangePersistenceException.class)
    ResponseEntity<ErrorResponse> phoneChangePersistence(PhoneChangePersistenceException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "PHONE_CHANGE_OPERATION_FAILED", "phone change failed");
    }

    @ExceptionHandler(PhoneIdentityConflictException.class)
    ResponseEntity<ErrorResponse> phoneAlreadyUsed(PhoneIdentityConflictException exception) {
        return response(HttpStatus.CONFLICT, "PHONE_ALREADY_IN_USE", "phone is already in use");
    }

    @ExceptionHandler(PhoneIdentityOperationException.class)
    ResponseEntity<ErrorResponse> phoneIdentityUnavailable(PhoneIdentityOperationException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "PHONE_CHANGE_UNAVAILABLE", "phone change unavailable");
    }

    @ExceptionHandler(OtpRateLimitedException.class)
    ResponseEntity<ErrorResponse> otpRateLimited(OtpRateLimitedException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
                .body(new ErrorResponse("OTP_RATE_LIMITED", "try again later", MDC.get("correlationId")));
    }

    @ExceptionHandler(OtpDeliveryException.class)
    ResponseEntity<ErrorResponse> otpDeliveryUnavailable(OtpDeliveryException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "OTP_DELIVERY_UNAVAILABLE", "OTP delivery unavailable");
    }

    @ExceptionHandler(OtpVerificationException.class)
    ResponseEntity<ErrorResponse> otpVerification(OtpVerificationException exception) {
        return response(HttpStatus.BAD_REQUEST, "PHONE_CHANGE_OTP_INVALID", "invalid phone change verification");
    }

    private static ResponseEntity<ErrorResponse> response(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, MDC.get("correlationId")));
    }

    public record ErrorResponse(String code, String message, String correlationId) {}
}
