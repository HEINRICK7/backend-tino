package com.tino.backend.reconciliation.adapter.in.web;

import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.payment.domain.model.PaymentAmount;
import com.tino.backend.payment.domain.model.PaymentStatus;
import com.tino.backend.reconciliation.application.exception.ReconciliationConflictException;
import com.tino.backend.reconciliation.application.exception.ReconciliationNotFoundException;
import com.tino.backend.reconciliation.application.model.ReconciliationCommandResult;
import com.tino.backend.reconciliation.application.model.ReconciliationRunView;
import com.tino.backend.reconciliation.application.usecase.GetReconciliationRun;
import com.tino.backend.reconciliation.application.usecase.ReconcilePayments;
import com.tino.backend.reconciliation.domain.model.SettlementEntry;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
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
@RequestMapping("/api/v1/businesses/{businessId}/reconciliation")
public final class ReconciliationController {
    private final AuthenticatedUserResolver users;
    private final ReconcilePayments reconcile;
    private final GetReconciliationRun getRun;
    public ReconciliationController(AuthenticatedUserResolver users, ReconcilePayments reconcile,
            GetReconciliationRun getRun) { this.users = users; this.reconcile = reconcile; this.getRun = getRun; }

    @PostMapping("/runs")
    public ResponseEntity<ReconciliationResponse> create(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @RequestHeader("Idempotency-Key") String key,
            @RequestBody JsonNode request) {
        var user = resolve(principal);
        var entries = parseEntries(request);
        var result = reconcile.execute(user.userId(), new BusinessId(businessId), text(request, "provider"), key,
                digest(request.toString()), entries);
        return ResponseEntity.status(result.replayed() ? 200 : 201).body(new ReconciliationResponse(result.run(), result.replayed()));
    }

    @GetMapping("/runs/{runId}")
    public ReconciliationRunView get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID runId) {
        var user = resolve(principal);
        return getRun.execute(user.userId(), new BusinessId(businessId), runId);
    }

    private java.util.List<SettlementEntry> parseEntries(JsonNode request) {
        var nodes = request == null ? null : request.get("entries");
        if (request == null || !request.isObject() || nodes == null || !nodes.isArray()
                || nodes.isEmpty()) throw new IllegalArgumentException("entries must not be empty");
        var result = new ArrayList<SettlementEntry>();
        for (var node : nodes) {
            var amount = new BigDecimal(text(node, "amount"));
            PaymentStatus status;
            try { status = PaymentStatus.valueOf(text(node, "status")); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("invalid status", exception); }
            result.add(new SettlementEntry(text(node, "provider_event_id"), text(node, "provider_payment_id"),
                    new PaymentAmount(amount), text(node, "currency"), status, digest(node.toString())));
        }
        return result;
    }
    private com.tino.backend.identity.application.port.in.AuthenticatedUserSnapshot resolve(AuthenticatedPrincipal principal) {
        if (principal == null) throw new IllegalArgumentException("authentication required");
        try {
            var user = users.resolve(principal);
            if (!user.active()) throw new IllegalArgumentException("authentication required");
            return user;
        }
        catch (DisabledUserException | InvalidAuthenticatedPrincipalException exception) { throw new IllegalArgumentException("authentication required", exception); }
    }
    private static String text(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        if (value == null || !value.isString() || value.stringValue().isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.stringValue();
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    public record ReconciliationResponse(ReconciliationRunView run, boolean replayed) {}
}
