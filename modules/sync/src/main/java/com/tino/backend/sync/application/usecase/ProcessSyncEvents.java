package com.tino.backend.sync.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.business.application.port.in.BusinessContextReader;
import com.tino.backend.device.application.port.in.DeviceInstallationContextReader;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import com.tino.backend.sync.application.exception.SyncEventRejectedException;
import com.tino.backend.sync.application.exception.SyncPersistenceException;
import com.tino.backend.sync.application.exception.SyncUnavailableException;
import com.tino.backend.sync.application.model.SyncEventRejection;
import com.tino.backend.sync.application.model.SyncPushResult;
import com.tino.backend.sync.application.port.out.SyncEventRepository;
import com.tino.backend.sync.domain.model.SyncEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Tenant-safe, idempotent orchestration for one Android push batch. */
public final class ProcessSyncEvents {
    private static final String BUSINESS_REQUIRED_CODE = "BUSINESS_CONTEXT_REQUIRED";
    private static final String BUSINESS_DENIED_CODE = "BUSINESS_ACCESS_DENIED";
    private static final String DEVICE_DENIED_CODE = "DEVICE_NOT_AUTHORIZED";
    private static final String UNKNOWN_EVENT_CODE = "UNKNOWN_EVENT_TYPE_OR_VERSION";

    private final BusinessContextReader businesses;
    private final BusinessAuthorization businessAuthorization;
    private final DeviceInstallationContextReader devices;
    private final SyncEventRepository events;
    private final SyncEventHandlerRegistry handlers;
    private final UuidGenerator ids;
    private final Clock clock;

    public ProcessSyncEvents(
            BusinessContextReader businesses,
            BusinessAuthorization businessAuthorization,
            DeviceInstallationContextReader devices,
            SyncEventRepository events,
            SyncEventHandlerRegistry handlers,
            UuidGenerator ids,
            Clock clock) {
        this.businesses = Objects.requireNonNull(businesses, "businesses");
        this.businessAuthorization = Objects.requireNonNull(businessAuthorization, "businessAuthorization");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.events = Objects.requireNonNull(events, "events");
        this.handlers = Objects.requireNonNull(handlers, "handlers");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SyncPushResult execute(
            UUID authenticatedUserId, UUID requestedBusinessId, List<SyncEvent> batch) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId");
        Objects.requireNonNull(batch, "batch");
        if (batch.isEmpty()) {
            throw new IllegalArgumentException("events must not be empty");
        }

        var accessible = businesses.listAccessibleBusinesses(authenticatedUserId);
        var business = selectBusiness(accessible, requestedBusinessId);
        if (business.isEmpty()) {
            var code = requestedBusinessId == null && accessible.size() > 1
                    ? BUSINESS_REQUIRED_CODE : BUSINESS_DENIED_CODE;
            return rejectedWithoutTenant(batch, code, false, rejectionMessage(code));
        }

        var businessId = new BusinessId(business.get());
        var acknowledged = new ArrayList<UUID>();
        var alreadyProcessed = new ArrayList<UUID>();
        var rejected = new ArrayList<SyncEventRejection>();
        for (var event : batch) {
            processOne(authenticatedUserId, businessId, event, acknowledged, alreadyProcessed, rejected);
        }
        return new SyncPushResult(acknowledged, alreadyProcessed, rejected);
    }

    private void processOne(
            UUID userId,
            BusinessId businessId,
            SyncEvent event,
            List<UUID> acknowledged,
            List<UUID> alreadyProcessed,
            List<SyncEventRejection> rejected) {
        try {
            if (devices.resolve(userId, businessId.value(), event.deviceId()).isEmpty()) {
                persistRejection(userId, businessId, event, DEVICE_DENIED_CODE, false,
                        "device is not authorized");
                rejected.add(new SyncEventRejection(
                        event.eventId(), DEVICE_DENIED_CODE, false, "device is not authorized"));
                return;
            }
            var handler = handlers.find(event.eventType(), event.schemaVersion());
            if (handler == null) {
                persistRejection(userId, businessId, event, UNKNOWN_EVENT_CODE, false,
                        "event type or schema version is not supported");
                rejected.add(new SyncEventRejection(event.eventId(), UNKNOWN_EVENT_CODE, false,
                        "event type or schema version is not supported"));
                return;
            }
            var effects = handler.handle(event);
            var outcome = businessAuthorization.execute(userId, businessId, authorized -> {
                var now = Instant.now(clock);
                if (!events.claim(businessId, event, now)) {
                    return Outcome.ALREADY_PROCESSED;
                }
                events.appendAccepted(businessId, event, effects, ids.next(), now);
                return Outcome.ACKNOWLEDGED;
            });
            if (outcome == Outcome.ACKNOWLEDGED) {
                acknowledged.add(event.eventId());
            } else {
                alreadyProcessed.add(event.eventId());
            }
        } catch (SyncEventRejectedException exception) {
            persistRejection(userId, businessId, event, exception.code(), exception.retryable(),
                    exception.getMessage());
            rejected.add(new SyncEventRejection(
                    event.eventId(), exception.code(), exception.retryable(), exception.getMessage()));
        } catch (BusinessAuthorizationDeniedException exception) {
            rejected.add(new SyncEventRejection(
                    event.eventId(), BUSINESS_DENIED_CODE, false, "business access denied"));
        } catch (SyncPersistenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SyncUnavailableException(exception);
        }
    }

    private void persistRejection(
            UUID userId,
            BusinessId businessId,
            SyncEvent event,
            String code,
            boolean retryable,
            String message) {
        businessAuthorization.execute(userId, businessId, authorized -> {
            events.recordRejection(
                    businessId, ids.next(), event.eventId(), event.deviceId(), code,
                    retryable, message, Instant.now(clock));
            return null;
        });
    }

    private SyncPushResult rejectedWithoutTenant(
            List<SyncEvent> batch, String code, boolean retryable, String message) {
        return new SyncPushResult(
                List.of(), List.of(), batch.stream()
                        .map(event -> new SyncEventRejection(
                                event.eventId(), code, retryable, message))
                        .toList());
    }

    private static java.util.Optional<UUID> selectBusiness(
            List<com.tino.backend.business.application.port.in.AccessibleBusinessView> accessible,
            UUID requestedBusinessId) {
        if (requestedBusinessId != null) {
            return accessible.stream()
                    .map(com.tino.backend.business.application.port.in.AccessibleBusinessView::businessId)
                    .filter(requestedBusinessId::equals)
                    .findFirst();
        }
        return accessible.size() == 1
                ? java.util.Optional.of(accessible.getFirst().businessId())
                : java.util.Optional.empty();
    }

    private static String rejectionMessage(String code) {
        return BUSINESS_REQUIRED_CODE.equals(code)
                ? "business context is required"
                : "business access denied";
    }

    private enum Outcome { ACKNOWLEDGED, ALREADY_PROCESSED }
}
