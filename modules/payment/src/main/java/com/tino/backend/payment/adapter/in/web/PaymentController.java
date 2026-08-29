package com.tino.backend.payment.adapter.in.web;

import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.payment.application.exception.PaymentUnauthenticatedException;
import com.tino.backend.payment.application.exception.PaymentWebhookUnauthorizedException;
import com.tino.backend.payment.application.model.PaymentCommandResult;
import com.tino.backend.payment.application.model.PaymentView;
import com.tino.backend.payment.application.port.out.PaymentProvider;
import com.tino.backend.payment.application.usecase.CreatePayment;
import com.tino.backend.payment.application.usecase.GetPayment;
import com.tino.backend.payment.application.usecase.IngestPaymentWebhook;
import com.tino.backend.payment.application.usecase.ProcessPayment;
import com.tino.backend.payment.domain.model.PaymentStatus;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
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
@RequestMapping("/api/v1/businesses/{businessId}")
public final class PaymentController {
    private final AuthenticatedUserResolver authenticatedUsers;
    private final CreatePayment createPayment;
    private final GetPayment getPayment;
    private final ProcessPayment processPayment;
    private final IngestPaymentWebhook ingestWebhook;
    private final PaymentProvider provider;
    private final TenantContextExecutor tenantContext;

    public PaymentController(AuthenticatedUserResolver authenticatedUsers, CreatePayment createPayment,
            GetPayment getPayment, ProcessPayment processPayment, IngestPaymentWebhook ingestWebhook,
            PaymentProvider provider, TenantContextExecutor tenantContext) {
        this.authenticatedUsers = authenticatedUsers;
        this.createPayment = createPayment;
        this.getPayment = getPayment;
        this.processPayment = processPayment;
        this.ingestWebhook = ingestWebhook;
        this.provider = provider;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/customers/{customerId}/payments")
    public ResponseEntity<PaymentResponse> create(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID customerId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody JsonNode request) {
        var user = resolve(principal);
        var amount = decimal(request, "amount");
        var reference = optionalText(request, "external_reference");
        var result = createPayment.execute(user.userId(), new BusinessId(businessId), customerId,
                amount, reference, idempotencyKey, fingerprint(request));
        return ResponseEntity.status(result.replayed() ? 200 : 201).body(toResponse(result));
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID paymentId) {
        var user = resolve(principal);
        return toResponse(new PaymentCommandResult(
                getPayment.execute(user.userId(), new BusinessId(businessId), paymentId), true));
    }

    @PostMapping("/payments/{paymentId}/process")
    public PaymentResponse process(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID paymentId) {
        var user = resolve(principal);
        return toResponse(processPayment.execute(user.userId(), new BusinessId(businessId), paymentId));
    }

    @PostMapping("/payment-webhooks/{providerName}")
    public PaymentResponse webhook(@PathVariable UUID businessId, @PathVariable String providerName,
            @RequestHeader(name = "X-Provider-Event-Id") String eventId,
            @RequestHeader(name = "X-Provider-Signature") String signature,
            @RequestBody String payload) {
        if (!provider.name().equals(providerName) || !provider.verifySignature(payload, signature)) {
            throw new PaymentWebhookUnauthorizedException();
        }
        var request = parse(payload);
        var paymentId = uuid(request, "payment_id");
        var providerPaymentId = text(request, "provider_payment_id");
        PaymentStatus status;
        try {
            status = PaymentStatus.valueOf(text(request, "status"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status must be a valid payment status", exception);
        }
        return tenantContext.execute(new BusinessId(businessId), () -> new PaymentResponse(
                ingestWebhook.execute(new BusinessId(businessId), paymentId, providerName, eventId,
                        providerPaymentId, status, digest(payload)), false));
    }

    private com.tino.backend.identity.application.port.in.AuthenticatedUserSnapshot resolve(
            AuthenticatedPrincipal principal) {
        if (principal == null) throw new PaymentUnauthenticatedException();
        try {
            var user = authenticatedUsers.resolve(principal);
            if (!user.active()) throw new PaymentUnauthenticatedException();
            return user;
        } catch (DisabledUserException | InvalidAuthenticatedPrincipalException exception) {
            throw new PaymentUnauthenticatedException();
        }
    }

    private static PaymentResponse toResponse(PaymentCommandResult result) {
        return new PaymentResponse(result.payment(), result.replayed());
    }

    private static JsonNode parse(String payload) {
        try {
            var mapper = new tools.jackson.databind.ObjectMapper();
            var node = mapper.readTree(payload);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("webhook body must be an object");
            return node;
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalArgumentException("invalid webhook body", exception);
        }
    }

    private static java.math.BigDecimal decimal(JsonNode node, String field) {
        var value = text(node, field);
        try { return new java.math.BigDecimal(value); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " must be decimal", exception); }
    }

    private static String optionalText(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) return null;
        return text(node, field);
    }

    private static String text(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be nonblank text");
        }
        return value.stringValue();
    }

    private static UUID uuid(JsonNode node, String field) {
        try { return UUID.fromString(text(node, field)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException(field + " must be UUID", exception); }
    }

    private static String fingerprint(JsonNode request) {
        return digest(request == null ? "" : request.toString());
    }

    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record PaymentResponse(PaymentView payment, boolean replayed) {}
}
