package com.tino.backend.sync.adapter.in.web;

import tools.jackson.databind.JsonNode;
import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.sync.application.model.SyncEventRejection;
import com.tino.backend.sync.application.model.SyncPushResult;
import com.tino.backend.sync.application.exception.SyncUnavailableException;
import com.tino.backend.sync.application.exception.SyncAccessDeniedException;
import com.tino.backend.sync.application.exception.UnauthenticatedSyncRequestException;
import com.tino.backend.sync.application.exception.SyncBusinessContextRequiredException;
import com.tino.backend.sync.application.model.SyncChange;
import com.tino.backend.sync.application.model.SyncChangePage;
import com.tino.backend.sync.application.usecase.ProcessSyncEvents;
import com.tino.backend.sync.application.usecase.PullSyncChanges;
import com.tino.backend.sync.domain.model.SyncEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/** Thin compatibility adapter for the Android Sync Push contract. */
@RestController
@RequestMapping("/v1/sync")
public final class SyncController {
    private final AuthenticatedUserResolver authenticatedUsers;
    private final ProcessSyncEvents processSyncEvents;
    private final PullSyncChanges pullSyncChanges;
    private final ObjectMapper objectMapper;
    public SyncController(
            AuthenticatedUserResolver authenticatedUsers,
            ProcessSyncEvents processSyncEvents,
            PullSyncChanges pullSyncChanges,
            ObjectMapper objectMapper) {
        this.authenticatedUsers = authenticatedUsers;
        this.processSyncEvents = processSyncEvents;
        this.pullSyncChanges = pullSyncChanges;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/events")
    public ResponseEntity<SyncPushResponse> push(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestBody JsonNode request) {
        if (principal == null) {
            throw new UnauthenticatedSyncRequestException();
        }
        try {
            var user = authenticatedUsers.resolve(principal);
            if (!user.active()) {
                throw new SyncAccessDeniedException();
            }
            var parsed = parseRequest(request);
            var result = processSyncEvents.execute(
                    user.userId(), parsed.businessId(), parsed.events());
            return ResponseEntity.ok(toResponse(result));
        } catch (DisabledUserException | InvalidAuthenticatedPrincipalException exception) {
            if (exception instanceof DisabledUserException) {
                throw new SyncAccessDeniedException();
            }
            throw new UnauthenticatedSyncRequestException();
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException
                    || exception instanceof SyncUnavailableException) {
                throw exception;
            }
            throw new SyncUnavailableException(exception);
        }
    }

    @GetMapping("/changes")
    public ResponseEntity<SyncPullResponse> pull(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(name = "business_id", required = false) UUID businessId,
            @RequestParam(name = "cursor", defaultValue = "0") long cursor,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        var user = resolveActiveUser(principal);
        var page = pullSyncChanges.execute(user.userId(), businessId, cursor, limit);
        return ResponseEntity.ok(toPullResponse(page));
    }

    private com.tino.backend.identity.application.port.in.AuthenticatedUserSnapshot
            resolveActiveUser(AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new UnauthenticatedSyncRequestException();
        }
        try {
            var user = authenticatedUsers.resolve(principal);
            if (!user.active()) {
                throw new SyncAccessDeniedException();
            }
            return user;
        } catch (DisabledUserException exception) {
            throw new SyncAccessDeniedException();
        } catch (InvalidAuthenticatedPrincipalException exception) {
            throw new UnauthenticatedSyncRequestException();
        }
    }

    private ParsedRequest parseRequest(JsonNode request) {
        if (request == null || request.isNull()) {
            throw new IllegalArgumentException("request must not be null");
        }
        UUID requestedBusinessId = null;
        JsonNode eventNodes = request;
        if (request.isObject()) {
            if (request.has("business_id") && !request.get("business_id").isNull()) {
                requestedBusinessId = parseUuid(request.get("business_id"), "business_id");
            }
            eventNodes = request.get("events");
            if (eventNodes == null || !eventNodes.isArray()) {
                throw new IllegalArgumentException("events must be an array");
            }
        }
        if (!eventNodes.isArray() || eventNodes.isEmpty()) {
            throw new IllegalArgumentException("events must not be empty");
        }
        var events = new ArrayList<SyncEvent>();
        for (var node : eventNodes) {
            events.add(parseEvent(node));
        }
        return new ParsedRequest(requestedBusinessId, events);
    }

    private SyncEvent parseEvent(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("event must be an object");
        }
        var payload = node.get("payload");
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("payload is required");
        }
        return new SyncEvent(
                parseUuid(required(node, "event_id"), "event_id"),
                text(node, "store_id"),
                text(node, "device_id"),
                text(node, "aggregate_id"),
                text(node, "event_type"),
                positiveInt(node, "schema_version"),
                parseInstant(required(node, "occurred_at"), "occurred_at"),
                writePayload(payload));
    }

    private String writePayload(JsonNode payload) {
        return payload.toString();
    }

    private static JsonNode required(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        var value = required(node, field);
        if (!value.isString() || value.stringValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return value.stringValue();
    }

    private static int positiveInt(JsonNode node, String field) {
        var value = required(node, field);
        if (!value.canConvertToInt() || value.intValue() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value.intValue();
    }

    private static UUID parseUuid(JsonNode value, String field) {
        try {
            return UUID.fromString(value.stringValue());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static Instant parseInstant(JsonNode value, String field) {
        try {
            return Instant.parse(value.stringValue());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be an instant", exception);
        }
    }

    private static SyncPushResponse toResponse(SyncPushResult result) {
        return new SyncPushResponse(
                result.acknowledgedEventIds(),
                result.alreadyProcessedEventIds(),
                result.rejected().stream().map(SyncController::toResponse).toList());
    }

    private SyncPullResponse toPullResponse(SyncChangePage page) {
        return new SyncPullResponse(
                page.changes().stream().map(this::toChangeResponse).toList(), page.nextCursor());
    }

    private SyncChangeResponse toChangeResponse(SyncChange change) {
        try {
            return new SyncChangeResponse(
                    change.eventId(), change.storeId(), change.deviceId(), change.aggregateId(),
                    change.eventType(), change.schemaVersion(), change.occurredAt(),
                    objectMapper.readTree(change.payloadJson()));
        } catch (tools.jackson.core.JacksonException exception) {
            throw new SyncUnavailableException(exception);
        }
    }

    private static RejectedEventResponse toResponse(SyncEventRejection rejection) {
        return new RejectedEventResponse(
                rejection.eventId(), rejection.code(), rejection.retryable(), rejection.message());
    }

    private record ParsedRequest(UUID businessId, List<SyncEvent> events) {
        private ParsedRequest {
            events = List.copyOf(events);
        }
    }

    public record SyncPushResponse(
            List<UUID> acknowledgedEventIds,
            List<UUID> alreadyProcessedEventIds,
            List<RejectedEventResponse> rejected) {}

    public record RejectedEventResponse(
            UUID eventId, String code, boolean retryable, String message) {}

    public record SyncPullResponse(List<SyncChangeResponse> changes, long nextCursor) {}

    public record SyncChangeResponse(
            UUID eventId,
            String storeId,
            String deviceId,
            String aggregateId,
            String eventType,
            int schemaVersion,
            Instant occurredAt,
            tools.jackson.databind.JsonNode payload) {}
}
