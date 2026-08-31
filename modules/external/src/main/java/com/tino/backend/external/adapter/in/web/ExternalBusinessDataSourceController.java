package com.tino.backend.external.adapter.in.web;

import com.tino.backend.external.application.model.ConnectionRegistrationResult;
import com.tino.backend.external.application.model.ExternalSyncResult;
import com.tino.backend.external.application.usecase.ManageExternalBusinessDataSource;
import com.tino.backend.external.domain.model.ExternalBusinessConnection;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Backend-only onboarding and sync API; credentials are deliberately absent from its contract. */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/external-connections")
public final class ExternalBusinessDataSourceController {
    private final AuthenticatedUserResolver users;
    private final ManageExternalBusinessDataSource source;

    public ExternalBusinessDataSourceController(AuthenticatedUserResolver users, ManageExternalBusinessDataSource source) {
        this.users = users;
        this.source = source;
    }

    @PostMapping
    public ResponseEntity<ConnectionResponse> register(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @RequestBody RegisterConnectionRequest request) {
        var result = source.register(user(principal), new BusinessId(businessId), request.provider());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(response(result.connection()));
    }

    @GetMapping
    public List<ConnectionResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId) {
        return source.list(user(principal), new BusinessId(businessId)).stream().map(ExternalBusinessDataSourceController::response).toList();
    }

    @GetMapping("/{connectionId}")
    public ConnectionResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID connectionId) {
        return response(source.get(user(principal), new BusinessId(businessId), connectionId));
    }

    @PostMapping("/{connectionId}/sync")
    public ExternalSyncResult sync(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID connectionId) {
        return source.sync(user(principal), new BusinessId(businessId), connectionId);
    }

    private UUID user(AuthenticatedPrincipal principal) {
        if (principal == null) throw new IllegalArgumentException("authentication required");
        var snapshot = users.resolve(principal);
        if (!snapshot.active()) throw new IllegalArgumentException("authentication required");
        return snapshot.userId();
    }

    private static ConnectionResponse response(ExternalBusinessConnection connection) {
        return new ConnectionResponse(connection.id(), connection.businessId().value(), connection.provider(), connection.status(),
                connection.sourceType(), connection.lastSuccessfulSyncAt(), connection.syncCursor(), connection.lastSyncStartedAt(),
                connection.lastSyncFinishedAt(), connection.lastSyncErrorCode(), connection.lastSyncReceived(), connection.lastSyncCreated(),
                connection.lastSyncUpdated(), connection.lastSyncDeactivated(), connection.lastSyncRejected());
    }

    public record RegisterConnectionRequest(String provider) {}

    public record ConnectionResponse(UUID id, UUID businessId, String provider,
            com.tino.backend.external.domain.model.ExternalConnectionStatus status,
            com.tino.backend.external.domain.model.ExternalDataSourceType sourceType,
            java.time.Instant lastSuccessfulSyncAt, String syncCursor, java.time.Instant lastSyncStartedAt,
            java.time.Instant lastSyncFinishedAt, String lastSyncErrorCode, int lastSyncReceived, int lastSyncCreated,
            int lastSyncUpdated, int lastSyncDeactivated, int lastSyncRejected) {}
}
