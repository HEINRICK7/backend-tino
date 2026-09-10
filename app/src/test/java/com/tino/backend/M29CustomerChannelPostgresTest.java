package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.customerchannel.adapter.out.persistence.JooqCustomerChannelRepository;
import com.tino.backend.customerchannel.application.service.CustomerChannelToken;
import com.tino.backend.shared.kernel.BusinessId;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class M29CustomerChannelPostgresTest {
    private static final UUID BUSINESS_ID = UUID.fromString("00000000-0000-7029-8029-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-7029-8029-000000000002");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-7029-8029-000000000003");
    private static final UUID INVITE_ID = UUID.fromString("00000000-0000-7029-8029-000000000004");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-7029-8029-000000000005");
    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @BeforeEach
    void migrateAndClear() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (var connection = migratorConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.customer_payment_intent_confirmation_idempotency_keys, public.customer_payment_evidence_idempotency_keys, public.customer_payment_evidence, public.customer_payment_intent_idempotency_keys, public.customer_payment_intents, public.customer_sessions, public.customer_invites, "
                    + "public.customer_channel_activation_idempotency, public.customer_push_subscriptions, public.customer_push_outbox, public.customer_channels, "
                    + "public.customers, public.businesses CASCADE");
        }
    }

    @Test
    void appRoleBindsCustomerChannelTemporalPredicatesAsPostgresTimestamps() throws Exception {
        seedInviteAndSession();

        try (var connection = appConnection()) {
            var repository = new JooqCustomerChannelRepository(DSL.using(connection, SQLDialect.POSTGRES));

            var invite = repository.findValidInvite(CustomerChannelToken.hash("a".repeat(32)), NOW)
                    .orElseThrow();
            assertThat(invite.businessId()).isEqualTo(new BusinessId(BUSINESS_ID));
            assertThat(repository.findActiveSession(CustomerChannelToken.hash("b".repeat(32)), NOW))
                    .isPresent();
            assertThat(repository.findLatestInvite(new BusinessId(BUSINESS_ID), CHANNEL_ID))
                    .get()
                    .satisfies(history -> {
                        assertThat(history.consumed()).isFalse();
                        assertThat(history.revoked()).isFalse();
                        assertThat(history.deliveryStatus()).isEqualTo("QUEUED");
                    });

            var key = "m29-activation";
            var fingerprint = CustomerChannelToken.hash("a".repeat(32));
            assertThat(repository.claimActivationIdempotency("CUSTOMER_CHANNEL_ACTIVATION", key,
                    fingerprint, NOW)).isTrue();
            assertThat(repository.findActivationIdempotency("CUSTOMER_CHANNEL_ACTIVATION", key))
                    .get().extracting(record -> record.requestFingerprint()).isEqualTo(fingerprint);
            repository.completeActivationIdempotency("CUSTOMER_CHANNEL_ACTIVATION", key,
                    new BusinessId(BUSINESS_ID), CHANNEL_ID, CUSTOMER_ID, SESSION_ID, NOW);
            assertThat(repository.findActivationIdempotency("CUSTOMER_CHANNEL_ACTIVATION", key))
                    .get().extracting(record -> record.sessionId()).isEqualTo(SESSION_ID);
        }
    }

    private void seedInviteAndSession() throws Exception {
        try (var connection = adminConnection()) {
            var dsl = DSL.using(connection, SQLDialect.POSTGRES);
            var timestamp = NOW.atOffset(ZoneOffset.UTC);
            dsl.execute("INSERT INTO public.businesses "
                    + "(id, trade_name, vertical, status, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))",
                    BUSINESS_ID, "Customer channel test", "STORE", "ACTIVE", timestamp, timestamp);
            dsl.execute("INSERT INTO public.customers "
                    + "(id, business_id, name, status, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))",
                    CUSTOMER_ID, BUSINESS_ID, "Customer channel test", "ACTIVE", timestamp, timestamp);
            dsl.execute("INSERT INTO public.customer_channels "
                    + "(id, business_id, customer_id, status, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))",
                    CHANNEL_ID, BUSINESS_ID, CUSTOMER_ID, "ACTIVE", timestamp, timestamp);
            dsl.execute("INSERT INTO public.customer_invites "
                    + "(id, business_id, customer_channel_id, token_hash, expires_at, created_at) "
                    + "VALUES (?, ?, ?, ?, CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))",
                    INVITE_ID, BUSINESS_ID, CHANNEL_ID, CustomerChannelToken.hash("a".repeat(32)),
                    NOW.plusSeconds(300).atOffset(ZoneOffset.UTC), timestamp);
            dsl.execute("INSERT INTO public.customer_sessions "
                    + "(id, customer_channel_id, business_id, customer_id, session_token_hash, "
                    + "created_at, last_seen_at, expires_at) "
                    + "VALUES (?, ?, ?, ?, ?, CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))",
                    SESSION_ID, CHANNEL_ID, BUSINESS_ID, CUSTOMER_ID, CustomerChannelToken.hash("b".repeat(32)),
                    timestamp, timestamp, NOW.plusSeconds(300).atOffset(ZoneOffset.UTC));
        }
    }

    private java.sql.Connection migratorConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
    }

    private java.sql.Connection appConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.APP, POSTGRES.appPassword());
    }

    private java.sql.Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
