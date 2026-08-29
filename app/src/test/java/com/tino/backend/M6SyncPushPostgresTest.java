package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.shared.kernel.UuidV7Generator;
import com.tino.backend.sync.application.exception.SyncPersistenceException;
import com.tino.backend.sync.application.model.SyncPushResult;
import com.tino.backend.sync.application.port.in.SyncEventHandler;
import com.tino.backend.sync.application.usecase.ProcessSyncEvents;
import com.tino.backend.sync.domain.model.SyncEvent;
import com.tino.backend.sync.domain.model.SyncEventEffects;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL proof for M6 claim, replay, rollback, RLS, and outbox writes. */
@Testcontainers
@SpringBootTest
@Import(M6SyncPushPostgresTest.HandlerConfiguration.class)
class M6SyncPushPostgresTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");
    private static final UUID BUSINESS_ID = UUID.fromString("00000000-0000-7000-8000-00000000010a");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("00000000-0000-7000-8000-00000000010b");
    private static final UUID INSTALLATION_ID = UUID.fromString("00000000-0000-7000-8000-00000000010c");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-7000-8000-00000000011a");
    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @Autowired
    private ProcessSyncEvents processSyncEvents;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> M2PostgresTestContainer.APP);
        registry.add("spring.datasource.password", POSTGRES::appPassword);
        registry.add("spring.flyway.user", () -> M2PostgresTestContainer.MIGRATOR);
        registry.add("spring.flyway.password", POSTGRES::migratorPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "https://issuer.example.test/realms/tino");
    }

    @BeforeEach
    void migrateAndSeed() throws Exception {
        migrate();
        try (var connection = migratorConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.message_delivery_evidence, public.message_outbox, public.messages, public.message_consent_audit, public.message_consents, public.reconciliation_items, public.reconciliation_runs, public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, public.credit_idempotency_keys, "
                    + "public.credit_ledger_entries, public.credit_accounts, public.customer_idempotency_keys, public.customers, "
                    + "public.sync_event_rejections, public.sync_outbox, "
                    + "public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
        }
        seedFixture();
    }

    @Test
    void acceptedEventClaimsAndWritesChangeAndOutboxAtomically() throws Exception {
        var result = processSyncEvents.execute(
                USER_ID, BUSINESS_ID, List.of(event(EVENT_ID, "known")));

        assertThat(result.acknowledgedEventIds()).containsExactly(EVENT_ID);
        assertThat(count("sync_event_claims")).isEqualTo(1);
        assertThat(count("sync_changes")).isEqualTo(1);
        assertThat(count("sync_outbox")).isEqualTo(1);
        assertThat(count("sync_event_rejections")).isZero();
        assertThat(changeSequence()).isPositive();
    }

    @Test
    void replayReturnsAlreadyProcessedAndDoesNotDuplicateEffects() throws Exception {
        var first = processSyncEvents.execute(
                USER_ID, BUSINESS_ID, List.of(event(EVENT_ID, "known")));
        var second = processSyncEvents.execute(
                USER_ID, BUSINESS_ID, List.of(event(EVENT_ID, "known")));

        assertThat(first.acknowledgedEventIds()).containsExactly(EVENT_ID);
        assertThat(second.alreadyProcessedEventIds()).containsExactly(EVENT_ID);
        assertThat(count("sync_event_claims")).isEqualTo(1);
        assertThat(count("sync_changes")).isEqualTo(1);
        assertThat(count("sync_outbox")).isEqualTo(1);
    }

    @Test
    void concurrentReplayHasOneWinner() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<SyncPushResult> call = () -> processSyncEvents.execute(
                    USER_ID, BUSINESS_ID, List.of(event(EVENT_ID, "known")));
            var results = executor.invokeAll(List.of(call, call));
            var first = results.get(0).get();
            var second = results.get(1).get();

            assertThat(List.of(first, second).stream()
                    .filter(result -> result.acknowledgedEventIds().contains(EVENT_ID)))
                    .hasSize(1);
            assertThat(List.of(first, second).stream()
                    .filter(result -> result.alreadyProcessedEventIds().contains(EVENT_ID)))
                    .hasSize(1);
        }
        assertThat(count("sync_event_claims")).isEqualTo(1);
        assertThat(count("sync_changes")).isEqualTo(1);
        assertThat(count("sync_outbox")).isEqualTo(1);
    }

    @Test
    void handlerFailureRollsBackClaimChangeAndOutboxTogether() throws Exception {
        var rollbackEvent = event(UUID.fromString(
                "00000000-0000-7000-8000-00000000012a"), "rollback");

        assertThatThrownBy(() -> processSyncEvents.execute(
                USER_ID, BUSINESS_ID, List.of(rollbackEvent)))
                .isInstanceOf(SyncPersistenceException.class);

        assertThat(count("sync_event_claims")).isZero();
        assertThat(count("sync_changes")).isZero();
        assertThat(count("sync_outbox")).isZero();
    }

    @Test
    void unknownEventIsRejectedAndRecordedWithoutClaim() throws Exception {
        var unknown = event(UUID.fromString(
                "00000000-0000-7000-8000-00000000013a"), "unknown");
        var result = processSyncEvents.execute(USER_ID, BUSINESS_ID, List.of(unknown));

        assertThat(result.rejected()).extracting("code")
                .containsExactly("UNKNOWN_EVENT_TYPE_OR_VERSION");
        assertThat(count("sync_event_claims")).isZero();
        assertThat(count("sync_changes")).isZero();
        assertThat(count("sync_outbox")).isZero();
        assertThat(count("sync_event_rejections")).isEqualTo(1);
    }

    @Test
    void unauthorizedDeviceIsRejectedBeforeAcceptedWrites() throws Exception {
        var unknownDevice = event(UUID.fromString(
                "00000000-0000-0000-0000-00000000013b"), "known");
        unknownDevice = new SyncEvent(
                unknownDevice.eventId(), unknownDevice.storeId(), "foreign-device",
                unknownDevice.aggregateId(), unknownDevice.eventType(), unknownDevice.schemaVersion(),
                unknownDevice.occurredAt(), unknownDevice.payloadJson());
        var result = processSyncEvents.execute(USER_ID, BUSINESS_ID, List.of(unknownDevice));

        assertThat(result.rejected()).extracting("code")
                .containsExactly("DEVICE_NOT_AUTHORIZED");
        assertThat(count("sync_event_claims")).isZero();
        assertThat(count("sync_changes")).isZero();
        assertThat(count("sync_outbox")).isZero();
        assertThat(count("sync_event_rejections")).isEqualTo(1);
    }

    private static SyncEvent event(UUID eventId, String eventType) {
        return new SyncEvent(
                eventId, "store-metadata", "device-a", "aggregate-a", eventType, 1,
                NOW, "{\"value\":1}");
    }

    private void seedFixture() throws SQLException {
        try (var connection = migratorConnection()) {
            execute(connection, "INSERT INTO public.users "
                    + "(id, external_subject, status, created_at, updated_at) VALUES "
                    + "('%s', 'm6-sync-user', 'ACTIVE', '%s', '%s')"
                    .formatted(USER_ID, NOW, NOW));
            execute(connection, "INSERT INTO public.businesses "
                    + "(id, trade_name, vertical, status, created_at, updated_at) VALUES "
                    + "('%s', 'M6 Business', 'OTHER', 'ACTIVE', '%s', '%s')"
                    .formatted(BUSINESS_ID, NOW, NOW));
            execute(connection, "INSERT INTO public.business_memberships "
                    + "(id, business_id, user_id, role, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', '%s', 'OWNER', 'ACTIVE', '%s', '%s')"
                    .formatted(MEMBERSHIP_ID, BUSINESS_ID, USER_ID, NOW, NOW));
        }
        // The fixture role is deliberately subject to forced RLS. Use the
        // disposable cluster administrator for setup; application access is
        // exercised through tino_app and the tenant transaction boundary.
        try (var connection = adminConnection()) {
            execute(connection, "INSERT INTO public.device_installations "
                    + "(id, business_id, installation_external_id, status, "
                    + "registered_by_user_id, created_at, updated_at) VALUES "
                    + "('%s', '%s', 'device-a', 'ACTIVE', '%s', '%s', '%s')"
                    .formatted(INSTALLATION_ID, BUSINESS_ID, USER_ID, NOW, NOW));
        }
    }

    private long count(String table) throws SQLException {
        try (var connection = adminConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT count(*) FROM public." + table)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private long changeSequence() throws SQLException {
        try (var connection = adminConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT sequence_id FROM public.sync_changes")) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void migrate() {
        org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                        POSTGRES.migratorPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static Connection migratorConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
    }

    private static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.APP, POSTGRES.appPassword());
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HandlerConfiguration {
        @Bean
        SyncEventHandler knownSyncHandler() {
            return new SyncEventHandler() {
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
            };
        }

        @Bean
        SyncEventHandler rollbackSyncHandler() {
            return new SyncEventHandler() {
                @Override
                public String eventType() {
                    return "rollback";
                }

                @Override
                public int schemaVersion() {
                    return 1;
                }

                @Override
                public SyncEventEffects handle(SyncEvent event) {
                    return new SyncEventEffects(event.payloadJson(), "not-json");
                }
            };
        }
    }
}
