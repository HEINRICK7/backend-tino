package com.tino.backend.receiving.adapter.in.web;

import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.receiving.application.exception.ReceivingException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = ReceivingController.class)
public final class ReceivingApiExceptionHandler {
    @ExceptionHandler(ReceivingException.class) ResponseEntity<Map<String, String>> receiving(ReceivingException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "RECEIVING_NOT_READY", "message", e.getMessage())); }
    @ExceptionHandler(BusinessAuthorizationDeniedException.class) ResponseEntity<Map<String, String>> denied() { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "BUSINESS_ACCESS_DENIED", "message", "business access denied")); }
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("code", "INVALID_NFE_REQUEST", "message", e.getMessage())); }
}
