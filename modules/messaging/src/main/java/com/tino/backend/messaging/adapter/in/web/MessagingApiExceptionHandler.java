package com.tino.backend.messaging.adapter.in.web;
import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.messaging.application.exception.*;
import com.tino.backend.messaging.application.port.out.MessagingPersistenceException;
import com.tino.backend.messaging.application.port.out.WhatsAppPersistenceException;
import org.slf4j.MDC; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(basePackageClasses={MessagingController.class,WhatsAppRendererController.class})
public final class MessagingApiExceptionHandler {
 @ExceptionHandler(BusinessAuthorizationDeniedException.class) ResponseEntity<ErrorResponse> denied(RuntimeException e){return r(403,"MESSAGING_ACCESS_DENIED","messaging access denied");}
 @ExceptionHandler(MessagingCustomerNotFoundException.class) ResponseEntity<ErrorResponse> customer(RuntimeException e){return r(404,"CUSTOMER_NOT_FOUND","customer not found");}
 @ExceptionHandler(MessageNotFoundException.class) ResponseEntity<ErrorResponse> message(RuntimeException e){return r(404,"MESSAGE_NOT_FOUND","message not found");}
 @ExceptionHandler(ConsentRequiredException.class) ResponseEntity<ErrorResponse> consent(RuntimeException e){return r(422,"CONSENT_REQUIRED","explicit messaging consent required");}
 @ExceptionHandler(MessagingConflictException.class) ResponseEntity<ErrorResponse> conflict(RuntimeException e){return r(409,"MESSAGING_CONFLICT","message idempotency conflict");}
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ErrorResponse> invalid(RuntimeException e){return r(400,"INVALID_MESSAGING_REQUEST","invalid messaging request");}
 @ExceptionHandler(MessagingPersistenceException.class) ResponseEntity<ErrorResponse> persistence(RuntimeException e){return r(500,"MESSAGING_OPERATION_FAILED","messaging operation failed");}
 @ExceptionHandler(WhatsAppPreviewNotFoundException.class) ResponseEntity<ErrorResponse> whatsappPreview(RuntimeException e){return r(404,"WHATSAPP_PREVIEW_NOT_FOUND","WhatsApp preview not found");}
 @ExceptionHandler(WhatsAppMessageNotFoundException.class) ResponseEntity<ErrorResponse> whatsappMessage(RuntimeException e){return r(404,"WHATSAPP_MESSAGE_NOT_FOUND","WhatsApp message not found");}
 @ExceptionHandler(WhatsAppPreviewStaleException.class) ResponseEntity<ErrorResponse> whatsappStale(RuntimeException e){return r(409,"WHATSAPP_PREVIEW_STALE","preview is no longer current; create a new preview");}
 @ExceptionHandler(WhatsAppPhoneMissingException.class) ResponseEntity<ErrorResponse> whatsappPhoneMissing(RuntimeException e){return r(422,"WHATSAPP_PHONE_MISSING","customer WhatsApp phone is missing");}
 @ExceptionHandler(WhatsAppPhoneInvalidException.class) ResponseEntity<ErrorResponse> whatsappPhoneInvalid(RuntimeException e){return r(422,"WHATSAPP_PHONE_INVALID","customer WhatsApp phone is invalid");}
 @ExceptionHandler(WhatsAppRenderFailedException.class) ResponseEntity<ErrorResponse> whatsappRender(RuntimeException e){return r(500,"WHATSAPP_RENDER_FAILED","WhatsApp message rendering failed");}
 @ExceptionHandler(WhatsAppProviderUnavailableException.class) ResponseEntity<ErrorResponse> whatsappProvider(RuntimeException e){return r(503,"WHATSAPP_PROVIDER_UNAVAILABLE","WhatsApp provider is temporarily unavailable");}
 @ExceptionHandler(WhatsAppSendFailedException.class) ResponseEntity<ErrorResponse> whatsappSend(RuntimeException e){return r(502,"WHATSAPP_SEND_FAILED","WhatsApp message could not be sent");}
 @ExceptionHandler(WhatsAppPersistenceException.class) ResponseEntity<ErrorResponse> whatsappPersistence(RuntimeException e){return r(500,"WHATSAPP_OPERATION_FAILED","WhatsApp operation failed");}
 private static ResponseEntity<ErrorResponse> r(int s,String c,String m){return ResponseEntity.status(s).body(new ErrorResponse(c,m,MDC.get("correlationId")));}
 public record ErrorResponse(String code,String message,String correlationId){}
}
