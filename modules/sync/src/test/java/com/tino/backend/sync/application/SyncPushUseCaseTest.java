package com.tino.backend.sync.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.business.application.port.in.AccessibleBusinessView;
import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessContextReader;
import com.tino.backend.device.application.port.in.ActiveInstallationView;
import com.tino.backend.device.application.port.in.DeviceInstallationContextReader;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidV7Generator;
import com.tino.backend.shared.kernel.UuidGenerator;
import com.tino.backend.sync.application.exception.SyncEventRejectedException;
import com.tino.backend.sync.application.model.SyncPushResult;
import com.tino.backend.sync.application.port.in.SyncEventHandler;
import com.tino.backend.sync.application.port.out.SyncEventRepository;
import com.tino.backend.sync.application.usecase.ProcessSyncEvents;
import com.tino.backend.sync.application.usecase.SyncEventHandlerRegistry;
import com.tino.backend.sync.domain.model.SyncEvent;
import com.tino.backend.sync.domain.model.SyncEventEffects;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SyncPushUseCaseTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID BUSINESS_ID = UUID.fromString("00000000-0000-7000-8000-00000000000a");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-7000-8000-00000000001a");
    private static final UuidGenerator IDS = new UuidV7Generator();
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-29T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void acceptedEventIsAcknowledgedAndWritesAcceptedEffects() {
        var repository = new RecordingRepository();
        var result = resolver(repository, handler()).execute(
                USER_ID, BUSINESS_ID, List.of(event(EVENT_ID, "known", "device-a")));

        assertThat(result.acknowledgedEventIds()).containsExactly(EVENT_ID);
        assertThat(result.alreadyProcessedEventIds()).isEmpty();
        assertThat(result.rejected()).isEmpty();
        assertThat(repository.claims).containsExactly(EVENT_ID);
        assertThat(repository.accepted).containsExactly(EVENT_ID);
        assertThat(repository.rejections).isEmpty();
    }

    @Test
    void replayIsAlreadyProcessedWithoutApplyingEffectsAgain() {
        var repository = new RecordingRepository();
        var resolver = resolver(repository, handler());
        var first = resolver.execute(USER_ID, BUSINESS_ID,
                List.of(event(EVENT_ID, "known", "device-a")));
        var second = resolver.execute(USER_ID, BUSINESS_ID,
                List.of(event(EVENT_ID, "known", "device-a")));

        assertThat(first.acknowledgedEventIds()).containsExactly(EVENT_ID);
        assertThat(second.alreadyProcessedEventIds()).containsExactly(EVENT_ID);
        assertThat(second.acknowledgedEventIds()).isEmpty();
        assertThat(repository.accepted).containsExactly(EVENT_ID);
    }

    @Test
    void unknownEventTypeIsRejectedAndRecordedWithoutClaim() {
        var repository = new RecordingRepository();
        var result = resolver(repository, List.of()).execute(
                USER_ID, BUSINESS_ID, List.of(event(EVENT_ID, "unknown", "device-a")));

        assertThat(result.rejected()).extracting("eventId", "code", "retryable")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        EVENT_ID, "UNKNOWN_EVENT_TYPE_OR_VERSION", false));
        assertThat(repository.claims).isEmpty();
        assertThat(repository.rejections).containsExactly(EVENT_ID);
    }

    @Test
    void revokedOrForeignDeviceIsRejectedBeforeHandlerAndTenantWrite() {
        var repository = new RecordingRepository();
        var result = resolver(repository, handler(), Optional.empty()).execute(
                USER_ID, BUSINESS_ID, List.of(event(EVENT_ID, "known", "revoked-device")));

        assertThat(result.rejected()).extracting("code")
                .containsExactly("DEVICE_NOT_AUTHORIZED");
        assertThat(repository.claims).isEmpty();
        assertThat(repository.rejections).containsExactly(EVENT_ID);
    }

    @Test
    void multipleBusinessesRequireExplicitContextAndStoreIdIsNotAuthority() {
        var repository = new RecordingRepository();
        var result = new ProcessSyncEvents(
                userId -> List.of(business(BUSINESS_ID), business(UUID.fromString(
                        "00000000-0000-7000-8000-00000000000b"))),
                authorization(),
                deviceReader(Optional.of(activeInstallation(BUSINESS_ID))),
                repository,
                new SyncEventHandlerRegistry(List.of(handler())),
                IDS,
                CLOCK).execute(USER_ID, null, List.of(event(EVENT_ID, "known", "device-a")));

        assertThat(result.rejected()).extracting("code")
                .containsExactly("BUSINESS_CONTEXT_REQUIRED");
        assertThat(repository.claims).isEmpty();
    }

    @Test
    void handlerRejectionIsReportedAsRejectedEvent() {
        var repository = new RecordingRepository();
        var rejecting = new TestHandler() {
            @Override
            public SyncEventEffects handle(SyncEvent event) {
                throw new SyncEventRejectedException("DOMAIN_REJECTED", false, "domain rejected");
            }
        };
        var result = resolver(repository, rejecting).execute(
                USER_ID, BUSINESS_ID, List.of(event(EVENT_ID, "known", "device-a")));

        assertThat(result.rejected()).extracting("code", "message")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "DOMAIN_REJECTED", "domain rejected"));
        assertThat(repository.claims).isEmpty();
        assertThat(repository.rejections).containsExactly(EVENT_ID);
    }

    private static ProcessSyncEvents resolver(
            RecordingRepository repository, SyncEventHandler handler) {
        return resolver(repository, List.of(handler), Optional.of(activeInstallation(BUSINESS_ID)));
    }

    private static ProcessSyncEvents resolver(
            RecordingRepository repository,
            List<SyncEventHandler> handlers) {
        return resolver(repository, handlers, Optional.of(activeInstallation(BUSINESS_ID)));
    }

    private static ProcessSyncEvents resolver(
            RecordingRepository repository,
            SyncEventHandler handler,
            Optional<ActiveInstallationView> installation) {
        return resolver(repository, List.of(handler), installation);
    }

    private static ProcessSyncEvents resolver(
            RecordingRepository repository,
            List<SyncEventHandler> handlers,
            Optional<ActiveInstallationView> installation) {
        return new ProcessSyncEvents(
                userId -> List.of(business(BUSINESS_ID)),
                authorization(),
                deviceReader(installation),
                repository,
                new SyncEventHandlerRegistry(handlers),
                IDS,
                CLOCK);
    }

    private static BusinessAuthorization authorization() {
        return new BusinessAuthorization() {
            @Override
            public <T> T execute(
                    UUID authenticatedUserId,
                    BusinessId requestedBusinessId,
                    Function<BusinessId, T> operation) {
                return operation.apply(requestedBusinessId);
            }
        };
    }

    private static DeviceInstallationContextReader deviceReader(
            Optional<ActiveInstallationView> installation) {
        return (userId, businessId, deviceId) -> installation;
    }

    private static AccessibleBusinessView business(UUID id) {
        return new AccessibleBusinessView(id, "Test Business", "RETAIL", "ACTIVE", "OWNER");
    }

    private static ActiveInstallationView activeInstallation(UUID businessId) {
        return new ActiveInstallationView(
                UUID.fromString("00000000-0000-7000-8000-00000000001b"), "device-a", businessId);
    }

    private static SyncEvent event(UUID eventId, String eventType, String deviceId) {
        return new SyncEvent(
                eventId, "store-metadata", deviceId, "aggregate-a", eventType, 1,
                Instant.parse("2026-08-29T11:00:00Z"), "{\"value\":1}");
    }

    private static TestHandler handler() {
        return new TestHandler();
    }

    private static class TestHandler implements SyncEventHandler {
        @Override
        public String eventType() {
            return "known";
        }

        @Override
        public int schemaVersion() {
            return 1;
        }

        @Override
        public SyncEventEffects handle(SyncEvent event) {
            return new SyncEventEffects(event.payloadJson(), event.payloadJson());
        }
    }

    private static final class RecordingRepository implements SyncEventRepository {
        private final List<UUID> claims = new ArrayList<>();
        private final List<UUID> accepted = new ArrayList<>();
        private final List<UUID> rejections = new ArrayList<>();

        @Override
        public boolean claim(BusinessId businessId, SyncEvent event, Instant createdAt) {
            if (claims.contains(event.eventId())) {
                return false;
            }
            claims.add(event.eventId());
            return true;
        }

        @Override
        public void appendAccepted(
                BusinessId businessId, SyncEvent event, SyncEventEffects effects,
                UUID outboxId, Instant createdAt) {
            accepted.add(event.eventId());
        }

        @Override
        public void recordRejection(
                BusinessId businessId, UUID rejectionId, UUID eventId, String deviceId,
                String code, boolean retryable, String message, Instant createdAt) {
            rejections.add(eventId);
        }
    }
}
