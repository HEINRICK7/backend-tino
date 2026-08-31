package com.tino.backend.identity.adapter.in.otp;

import com.tino.backend.identity.application.exception.OtpDeliveryException;
import com.tino.backend.identity.application.exception.OtpInvalidRequestException;
import com.tino.backend.identity.application.exception.OtpRateLimitedException;
import com.tino.backend.identity.application.exception.OtpVerificationException;
import com.tino.backend.identity.application.port.out.OtpPersistenceException;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Non-disclosing HTTP mapping for OTP and internal proof failures. */
@RestControllerAdvice(basePackageClasses = OtpController.class)
public final class OtpApiExceptionHandler {
    @ExceptionHandler({OtpInvalidRequestException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> invalid(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_OTP_REQUEST", "invalid OTP request");
    }

    @ExceptionHandler(OtpRateLimitedException.class)
    ResponseEntity<ErrorResponse> rateLimited(OtpRateLimitedException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
                .body(new ErrorResponse("OTP_RATE_LIMITED", "try again later", MDC.get("correlationId")));
    }

    @ExceptionHandler(OtpDeliveryException.class)
    ResponseEntity<ErrorResponse> delivery(OtpDeliveryException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "OTP_DELIVERY_UNAVAILABLE", "OTP delivery unavailable");
    }

    @ExceptionHandler(OtpVerificationException.class)
    ResponseEntity<ErrorResponse> verification(OtpVerificationException exception) {
        var reason = exception.reason();
        var code = switch (reason) {
            case INVALID -> "OTP_INVALID";
            case EXPIRED -> "OTP_EXPIRED";
            case LOCKED -> "OTP_LOCKED";
            case ALREADY_USED -> "OTP_ALREADY_USED";
        };
        var message = reason == OtpVerificationException.Reason.EXPIRED
                ? "OTP expired"
                : reason == OtpVerificationException.Reason.LOCKED
                        ? "too many attempts"
                        : reason == OtpVerificationException.Reason.ALREADY_USED
                                ? "OTP already used"
                                : "invalid OTP";
        return response(HttpStatus.BAD_REQUEST, code, message);
    }

    @ExceptionHandler(OtpPersistenceException.class)
    ResponseEntity<ErrorResponse> persistence(OtpPersistenceException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "OTP_OPERATION_FAILED", "OTP operation failed");
    }

    private static ResponseEntity<ErrorResponse> response(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, MDC.get("correlationId")));
    }

    public record ErrorResponse(String code, String message, String correlationId) {}
}
