package com.tino.backend.messaging.adapter.in.web;

import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.identity.application.port.in.AuthenticatedUserSnapshot;
import com.tino.backend.messaging.application.model.WhatsAppDeliveryView;
import com.tino.backend.messaging.application.model.WhatsAppPreviewView;
import com.tino.backend.messaging.application.model.WhatsAppSendResult;
import com.tino.backend.messaging.application.service.ResolveWhatsAppBusiness;
import com.tino.backend.messaging.application.usecase.CreateWhatsAppPreview;
import com.tino.backend.messaging.application.usecase.GetWhatsAppMessageStatus;
import com.tino.backend.messaging.application.usecase.GetWhatsAppPreviewMedia;
import com.tino.backend.messaging.application.usecase.ProcessWhatsAppMessage;
import com.tino.backend.messaging.application.usecase.SendWhatsAppMessage;
import com.tino.backend.messaging.domain.model.WhatsAppMessageType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public final class WhatsAppRendererController {
    private final AuthenticatedUserResolver users;
    private final ResolveWhatsAppBusiness businesses;
    private final CreateWhatsAppPreview previews;
    private final SendWhatsAppMessage sends;
    private final GetWhatsAppMessageStatus statuses;
    private final GetWhatsAppPreviewMedia media;
    private final ProcessWhatsAppMessage processor;
    private final boolean deliveryEnabled;

    public WhatsAppRendererController(AuthenticatedUserResolver users, ResolveWhatsAppBusiness businesses,
            CreateWhatsAppPreview previews, SendWhatsAppMessage sends, GetWhatsAppMessageStatus statuses,
            GetWhatsAppPreviewMedia media, ProcessWhatsAppMessage processor,
            @Value("${tino.whatsapp.delivery.enabled:false}") boolean deliveryEnabled) {
        this.users = users;
        this.businesses = businesses;
        this.previews = previews;
        this.sends = sends;
        this.statuses = statuses;
        this.media = media;
        this.processor = processor;
        this.deliveryEnabled = deliveryEnabled;
    }

    @PostMapping("/debt-accounts/{accountId}/whatsapp-preview")
    public WhatsAppPreviewView preview(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID accountId, @RequestBody JsonNode request) {
        var user = resolve(principal);
        var business = businesses.forAccount(user.userId(), accountId);
        return previews.execute(user.userId(), business, accountId, messageType(request));
    }

    @PostMapping("/debt-accounts/{accountId}/whatsapp-send")
    public WhatsAppSendResult send(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID accountId, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody JsonNode request) {
        var user = resolve(principal);
        var business = businesses.forAccount(user.userId(), accountId);
        var previewId = uuid(request, "previewId", "preview_id");
        var type = optionalMessageType(request);
        var result = sends.execute(user.userId(), business, accountId, previewId, type, idempotencyKey,
                digest(previewId + "\u0000" + (type == null ? "" : type.name())));
        if (deliveryEnabled) {
            try {
                processor.execute(user.userId(), business, result.message().messageId());
            } catch (RuntimeException ignored) {
                // The durable delivery record contains the failure; the API contract remains QUEUED.
            }
        }
        return result;
    }

    @GetMapping("/whatsapp-messages/{messageId}")
    public WhatsAppDeliveryView status(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID messageId) {
        var user = resolve(principal);
        var business = businesses.forMessage(user.userId(), messageId);
        return statuses.execute(user.userId(), business, messageId);
    }

    @GetMapping("/whatsapp-previews/{previewId}/media")
    public ResponseEntity<byte[]> previewMedia(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID previewId) {
        var user = resolve(principal);
        var business = businesses.forPreview(user.userId(), previewId);
        var preview = media.execute(user.userId(), business, previewId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(preview.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + preview.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(preview.media());
    }

    @PostMapping("/businesses/{businessId}/whatsapp-messages/{messageId}/process")
    public WhatsAppDeliveryView process(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID messageId) {
        var user = resolve(principal);
        return processor.execute(user.userId(), new com.tino.backend.shared.kernel.BusinessId(businessId), messageId);
    }

    private AuthenticatedUserSnapshot resolve(AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("authentication required");
        }
        try {
            var user = users.resolve(principal);
            if (!user.active()) {
                throw new IllegalArgumentException("authentication required");
            }
            return user;
        } catch (DisabledUserException | InvalidAuthenticatedPrincipalException exception) {
            throw new IllegalArgumentException("authentication required", exception);
        }
    }

    private static WhatsAppMessageType messageType(JsonNode request) {
        var type = optionalMessageType(request);
        if (type == null) {
            throw new IllegalArgumentException("messageType is required");
        }
        return type;
    }

    private static WhatsAppMessageType optionalMessageType(JsonNode request) {
        var value = text(request, "messageType");
        if (value == null) {
            value = text(request, "message_type");
        }
        return value == null ? null : WhatsAppMessageType.valueOf(value);
    }

    private static UUID uuid(JsonNode request, String... fields) {
        for (var field : fields) {
            var value = text(request, field);
            if (value != null) {
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("invalid " + field, exception);
                }
            }
        }
        throw new IllegalArgumentException("previewId is required");
    }

    private static String text(JsonNode request, String field) {
        var value = request == null ? null : request.get(field);
        return value == null || !value.isString() || value.stringValue().isBlank() ? null : value.stringValue();
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
