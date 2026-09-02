package com.tino.backend.messaging.adapter.in.web;
import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.messaging.application.exception.*;
import com.tino.backend.messaging.application.port.out.MessagingPersistenceException;
import org.slf4j.MDC; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(basePackageClasses=MessagingController.class)
public final class MessagingApiExceptionHandler {
 @ExceptionHandler(BusinessAuthorizationDeniedException.class) ResponseEntity<ErrorResponse> denied(RuntimeException e){return r(403,"MESSAGING_ACCESS_DENIED","messaging access denied");}
 @ExceptionHandler(MessagingCustomerNotFoundException.class) ResponseEntity<ErrorResponse> customer(RuntimeException e){return r(404,"CUSTOMER_NOT_FOUND","customer not found");}
 @ExceptionHandler(MessageNotFoundException.class) ResponseEntity<ErrorResponse> message(RuntimeException e){return r(404,"MESSAGE_NOT_FOUND","message not found");}
 @ExceptionHandler(ConsentRequiredException.class) ResponseEntity<ErrorResponse> consent(RuntimeException e){return r(422,"CONSENT_REQUIRED","explicit messaging consent required");}
 @ExceptionHandler(MessagingConflictException.class) ResponseEntity<ErrorResponse> conflict(RuntimeException e){return r(409,"MESSAGING_CONFLICT","message idempotency conflict");}
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ErrorResponse> invalid(RuntimeException e){return r(400,"INVALID_MESSAGING_REQUEST","invalid messaging request");}
 @ExceptionHandler(MessagingPersistenceException.class) ResponseEntity<ErrorResponse> persistence(RuntimeException e){return r(500,"MESSAGING_OPERATION_FAILED","messaging operation failed");}
 private static ResponseEntity<ErrorResponse> r(int s,String c,String m){return ResponseEntity.status(s).body(new ErrorResponse(c,m,MDC.get("correlationId")));}
 public record ErrorResponse(String code,String message,String correlationId){}
}
