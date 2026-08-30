package com.tino.backend.catalog.adapter.in.web;

import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = ProductSearchController.class)
public final class CatalogApiExceptionHandler {
    @ExceptionHandler(BusinessAuthorizationDeniedException.class)
    ResponseEntity<ProductSearchController.ErrorResponse> denied() {
        return response(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "business access denied", false);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProductSearchController.ErrorResponse> invalid(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), false);
    }

    private static ResponseEntity<ProductSearchController.ErrorResponse> response(
            HttpStatus status, String code, String message, boolean retryable) {
        return ResponseEntity.status(status)
                .body(new ProductSearchController.ErrorResponse(code, message, retryable, MDC.get("correlationId")));
    }
}
