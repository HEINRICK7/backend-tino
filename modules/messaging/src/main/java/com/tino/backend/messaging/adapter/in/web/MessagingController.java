package com.tino.backend.messaging.adapter.in.web;
import com.tino.backend.identity.application.exception.*;
import com.tino.backend.identity.application.port.in.*;
import com.tino.backend.messaging.application.model.*;
import com.tino.backend.messaging.application.usecase.*;
import com.tino.backend.messaging.domain.model.*;
import com.tino.backend.shared.kernel.BusinessId;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.*;
import org.springframework.http.ResponseEntity; import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*; import tools.jackson.databind.JsonNode;
@RestController @RequestMapping("/api/v1/businesses/{businessId}")
public final class MessagingController {
    private final AuthenticatedUserResolver users; private final SetConsent consent; private final QueueMessage queue; private final GetMessage get; private final ProcessMessage process;
    public MessagingController(AuthenticatedUserResolver u,SetConsent c,QueueMessage q,GetMessage g,ProcessMessage p){users=u;consent=c;queue=q;get=g;process=p;}
    @PutMapping("/customers/{customerId}/messaging/consent")
    public ConsentView consent(@AuthenticationPrincipal AuthenticatedPrincipal principal,@PathVariable UUID businessId,@PathVariable UUID customerId,@RequestBody JsonNode request){var user=resolve(principal);return consent.execute(user.userId(),new BusinessId(businessId),customerId,MessageChannel.valueOf(text(request,"channel")),MessagePurpose.valueOf(text(request,"purpose")),bool(request,"granted"),text(request,"recipient_ref"));}
    @PostMapping("/customers/{customerId}/messages")
    public ResponseEntity<MessageResponse> queue(@AuthenticationPrincipal AuthenticatedPrincipal principal,@PathVariable UUID businessId,@PathVariable UUID customerId,@RequestHeader("Idempotency-Key") String key,@RequestBody JsonNode request){var user=resolve(principal);var result=queue.execute(user.userId(),new BusinessId(businessId),customerId,MessageChannel.valueOf(text(request,"channel")),MessagePurpose.valueOf(text(request,"purpose")),MessageTemplate.valueOf(text(request,"template")),key,digest(request.toString()));return ResponseEntity.status(result.replayed()?200:201).body(new MessageResponse(result.message(),result.replayed()));}
    @PostMapping("/messages/{messageId}/process") public MessageResponse process(@AuthenticationPrincipal AuthenticatedPrincipal principal,@PathVariable UUID businessId,@PathVariable UUID messageId){var user=resolve(principal);var r=process.execute(user.userId(),new BusinessId(businessId),messageId);return new MessageResponse(r.message(),r.replayed());}
    @GetMapping("/messages/{messageId}") public MessageView get(@AuthenticationPrincipal AuthenticatedPrincipal principal,@PathVariable UUID businessId,@PathVariable UUID messageId){var user=resolve(principal);return get.execute(user.userId(),new BusinessId(businessId),messageId);}
    private AuthenticatedUserSnapshot resolve(AuthenticatedPrincipal p){if(p==null)throw new IllegalArgumentException("authentication required");try{var u=users.resolve(p);if(!u.active())throw new IllegalArgumentException("authentication required");return u;}catch(DisabledUserException|InvalidAuthenticatedPrincipalException e){throw new IllegalArgumentException("authentication required",e);}}
    private static String text(JsonNode n,String f){var v=n==null?null:n.get(f);if(v==null||!v.isString()||v.stringValue().isBlank())throw new IllegalArgumentException(f+" is required");return v.stringValue();}
    private static boolean bool(JsonNode n,String f){var v=n==null?null:n.get(f);if(v==null||!v.isBoolean())throw new IllegalArgumentException(f+" must be boolean");return v.booleanValue();}
    private static String digest(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record MessageResponse(MessageView message,boolean replayed){}
}
