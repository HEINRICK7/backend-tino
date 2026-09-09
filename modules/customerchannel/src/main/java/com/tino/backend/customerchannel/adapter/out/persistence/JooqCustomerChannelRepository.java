package com.tino.backend.customerchannel.adapter.out.persistence;

import com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository;
import com.tino.backend.customerchannel.domain.model.CustomerChannelStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqCustomerChannelRepository implements CustomerChannelRepository {
    private final DSLContext dsl;

    public JooqCustomerChannelRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public ChannelRecord upsertChannel(UUID id, BusinessId businessId, UUID customerId, Instant now) {
        dsl.execute("""
                INSERT INTO public.customer_channels
                    (id, business_id, customer_id, status, created_at, updated_at)
                VALUES (?, ?, ?, 'INVITED', CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))
                ON CONFLICT (business_id, customer_id)
                DO UPDATE SET updated_at = EXCLUDED.updated_at
                """, id, businessId.value(), customerId, time(now), time(now));
        return findChannel(businessId, customerId).orElseThrow();
    }

    @Override
    public Optional<ChannelRecord> findChannel(BusinessId businessId, UUID customerId) {
        var row = dsl.fetchOne("""
                SELECT id, business_id, customer_id, status, activated_at, last_access_at
                FROM public.customer_channels
                WHERE business_id = ? AND customer_id = ?
                """, businessId.value(), customerId);
        return row == null ? Optional.empty() : Optional.of(new ChannelRecord(
                row.get("id", UUID.class), new BusinessId(row.get("business_id", UUID.class)),
                row.get("customer_id", UUID.class),
                CustomerChannelStatus.valueOf(row.get("status", String.class)),
                instant(row.get("activated_at", OffsetDateTime.class)),
                instant(row.get("last_access_at", OffsetDateTime.class))));
    }

    @Override
    public Optional<InviteIdempotencyRecord> findInviteIdempotency(BusinessId businessId,
            String operation, String idempotencyKey) {
        var row = dsl.fetchOne("""
                SELECT request_fingerprint, customer_channel_id, response_status, delivery_status
                  FROM public.customer_channel_invite_idempotency
                 WHERE business_id = ? AND operation = ? AND idempotency_key = ?
                 FOR UPDATE
                """, businessId.value(), operation, idempotencyKey);
        return row == null ? Optional.empty() : Optional.of(new InviteIdempotencyRecord(
                row.get("request_fingerprint", String.class),
                row.get("customer_channel_id", UUID.class),
                row.get("response_status", String.class),
                row.get("delivery_status", String.class)));
    }

    @Override
    public boolean claimInviteIdempotency(BusinessId businessId, String operation,
            String idempotencyKey, String requestFingerprint, UUID channelId, Instant createdAt) {
        return dsl.execute("""
                INSERT INTO public.customer_channel_invite_idempotency
                    (business_id, operation, idempotency_key, request_fingerprint,
                     customer_channel_id, created_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS TIMESTAMPTZ))
                ON CONFLICT (business_id, operation, idempotency_key) DO NOTHING
                """, businessId.value(), operation, idempotencyKey, requestFingerprint,
                channelId, time(createdAt)) == 1;
    }

    @Override
    public void completeInviteIdempotency(BusinessId businessId, String operation,
            String idempotencyKey, String responseStatus, String deliveryStatus) {
        var updated = dsl.execute("""
                UPDATE public.customer_channel_invite_idempotency
                   SET response_status = ?, delivery_status = ?
                 WHERE business_id = ? AND operation = ? AND idempotency_key = ?
                """, responseStatus, deliveryStatus, businessId.value(), operation, idempotencyKey);
        if (updated != 1) {
            throw new IllegalStateException("customer-channel invite idempotency claim is missing");
        }
    }

    @Override
    public Optional<ActivationIdempotencyRecord> findActivationIdempotency(String operation,
            String idempotencyKey) {
        var row = dsl.fetchOne("""
                SELECT request_fingerprint, business_id, customer_channel_id, customer_id, session_id
                  FROM public.customer_channel_activation_idempotency
                 WHERE operation = ? AND idempotency_key = ?
                 FOR UPDATE
                """, operation, idempotencyKey);
        if (row == null) return Optional.empty();
        var businessId = row.get("business_id", UUID.class);
        return Optional.of(new ActivationIdempotencyRecord(
                row.get("request_fingerprint", String.class),
                businessId == null ? null : new BusinessId(businessId),
                row.get("customer_channel_id", UUID.class),
                row.get("customer_id", UUID.class),
                row.get("session_id", UUID.class)));
    }

    @Override
    public boolean claimActivationIdempotency(String operation, String idempotencyKey,
            String requestFingerprint, Instant createdAt) {
        return dsl.execute("""
                INSERT INTO public.customer_channel_activation_idempotency
                    (operation, idempotency_key, request_fingerprint, created_at)
                VALUES (?, ?, ?, CAST(? AS TIMESTAMPTZ))
                ON CONFLICT (operation, idempotency_key) DO NOTHING
                """, operation, idempotencyKey, requestFingerprint, time(createdAt)) == 1;
    }

    @Override
    public void completeActivationIdempotency(String operation, String idempotencyKey,
            BusinessId businessId, UUID channelId, UUID customerId, UUID sessionId,
            Instant completedAt) {
        var updated = dsl.execute("""
                UPDATE public.customer_channel_activation_idempotency
                   SET business_id = ?, customer_channel_id = ?, customer_id = ?, session_id = ?,
                       completed_at = CAST(? AS TIMESTAMPTZ)
                 WHERE operation = ? AND idempotency_key = ?
                """, businessId.value(), channelId, customerId, sessionId, time(completedAt),
                operation, idempotencyKey);
        if (updated != 1) {
            throw new IllegalStateException("customer-channel activation idempotency claim is missing");
        }
    }

    @Override
    public void revokeOpenInvites(BusinessId businessId, UUID channelId, Instant now) {
        dsl.execute("""
                UPDATE public.customer_invites
                   SET revoked_at = CAST(? AS TIMESTAMPTZ)
                 WHERE business_id = ? AND customer_channel_id = ?
                   AND consumed_at IS NULL AND revoked_at IS NULL
                """, time(now), businessId.value(), channelId);
    }

    @Override
    public void insertInvite(UUID id, BusinessId businessId, UUID channelId, String tokenHash,
            Instant expiresAt, Instant createdAt) {
        dsl.execute("""
                INSERT INTO public.customer_invites
                    (id, business_id, customer_channel_id, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?, CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))
                """, id, businessId.value(), channelId, tokenHash, time(expiresAt), time(createdAt));
    }

    @Override
    public Optional<InviteRecord> findValidInvite(String tokenHash, Instant now) {
        var row = dsl.fetchOne("""
                SELECT i.id, i.customer_channel_id, i.business_id, c.customer_id, i.expires_at
                  FROM public.customer_invites i
                  JOIN public.customer_channels c ON c.id = i.customer_channel_id
                 WHERE i.token_hash = ?
                   AND i.consumed_at IS NULL AND i.revoked_at IS NULL
                   AND i.expires_at > CAST(? AS TIMESTAMPTZ)
                   AND c.status IN ('INVITED', 'ACTIVE')
                FOR UPDATE
                """, tokenHash, time(now));
        return row == null ? Optional.empty() : Optional.of(new InviteRecord(
                row.get("id", UUID.class), row.get("customer_channel_id", UUID.class),
                new BusinessId(row.get("business_id", UUID.class)), row.get("customer_id", UUID.class),
                instant(row.get("expires_at", OffsetDateTime.class))));
    }

    @Override
    public void consumeInvite(UUID inviteId, Instant now) {
        dsl.execute("UPDATE public.customer_invites SET consumed_at = CAST(? AS TIMESTAMPTZ) WHERE id = ?",
                time(now), inviteId);
    }

    @Override
    public void insertSession(UUID id, UUID channelId, BusinessId businessId, UUID customerId,
            String tokenHash, Instant createdAt, Instant expiresAt) {
        dsl.execute("""
                INSERT INTO public.customer_sessions
                    (id, customer_channel_id, business_id, customer_id, session_token_hash,
                     created_at, last_seen_at, expires_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))
                """, id, channelId, businessId.value(), customerId, tokenHash,
                time(createdAt), time(createdAt), time(expiresAt));
        dsl.execute("""
                UPDATE public.customer_channels
                   SET status = 'ACTIVE', activated_at = COALESCE(activated_at, CAST(? AS TIMESTAMPTZ)),
                       updated_at = CAST(? AS TIMESTAMPTZ), last_access_at = CAST(? AS TIMESTAMPTZ)
                 WHERE id = ?
                """, time(createdAt), time(createdAt), time(createdAt), channelId);
    }

    @Override
    public Optional<SessionRecord> findActiveSession(String tokenHash, Instant now) {
        var row = dsl.fetchOne("""
                SELECT s.id, s.customer_channel_id, s.business_id, s.customer_id, s.expires_at
                  FROM public.customer_sessions s
                  JOIN public.customer_channels c ON c.id = s.customer_channel_id
                 WHERE s.session_token_hash = ? AND s.revoked_at IS NULL
                   AND s.expires_at > CAST(? AS TIMESTAMPTZ) AND c.status = 'ACTIVE'
                """, tokenHash, time(now));
        return row == null ? Optional.empty() : Optional.of(new SessionRecord(
                row.get("id", UUID.class), row.get("customer_channel_id", UUID.class),
                new BusinessId(row.get("business_id", UUID.class)), row.get("customer_id", UUID.class),
                instant(row.get("expires_at", OffsetDateTime.class))));
    }

    @Override
    public void revokeSession(UUID sessionId, Instant now) {
        dsl.execute("UPDATE public.customer_sessions SET revoked_at = CAST(? AS TIMESTAMPTZ), last_seen_at = CAST(? AS TIMESTAMPTZ) WHERE id = ?",
                time(now), time(now), sessionId);
    }

    @Override
    public void touchChannel(UUID channelId, Instant now) {
        dsl.execute("UPDATE public.customer_channels SET last_access_at = CAST(? AS TIMESTAMPTZ), updated_at = CAST(? AS TIMESTAMPTZ) WHERE id = ?",
                time(now), time(now), channelId);
    }

    @Override
    public Optional<HomeRecord> findHome(BusinessId businessId, UUID channelId, UUID customerId) {
        var row = dsl.fetchOne("""
                SELECT cch.status AS channel_status, c.name AS customer_name, b.trade_name AS business_name,
                       CASE a.status WHEN 'ACTIVE' THEN 'OPEN' WHEN 'ARCHIVED' THEN 'CLOSED' ELSE 'OPEN' END AS account_status,
                       COALESCE(a.balance, 0.00) AS balance,
                       COALESCE(trim(a.currency), 'BRL') AS currency,
                       COALESCE(a.version, 0) AS version,
                       COALESCE(a.updated_at, cch.updated_at) AS as_of
                  FROM public.customer_channels cch
                  JOIN public.customers c ON c.business_id = cch.business_id AND c.id = cch.customer_id
                  JOIN public.businesses b ON b.id = cch.business_id
                  LEFT JOIN public.credit_accounts a
                    ON a.business_id = cch.business_id AND a.customer_id = cch.customer_id
                   AND a.currency = 'BRL'
                 WHERE cch.business_id = ? AND cch.id = ? AND cch.customer_id = ?
                """, businessId.value(), channelId, customerId);
        if (row == null) return Optional.empty();
        return Optional.of(new HomeRecord(
                CustomerChannelStatus.valueOf(row.get("channel_status", String.class)),
                row.get("customer_name", String.class), row.get("business_name", String.class),
                row.get("account_status", String.class), row.get("balance", BigDecimal.class),
                row.get("currency", String.class), row.get("version", Long.class),
                instant(row.get("as_of", OffsetDateTime.class))));
    }

    @Override
    public List<ActivityRecord> listActivity(BusinessId businessId, UUID customerId,
            Instant beforeAt, UUID beforeId, int limit) {
        var sql = """
                SELECT id, direction, amount, created_at
                  FROM public.credit_ledger_entries
                 WHERE business_id = ? AND customer_id = ?
                """;
        if (beforeAt != null && beforeId != null) {
            sql += " AND (created_at < CAST(? AS TIMESTAMPTZ) OR (created_at = CAST(? AS TIMESTAMPTZ) AND id < ?)) ";
        }
        sql += " ORDER BY created_at DESC, id DESC LIMIT ?";
        var values = new java.util.ArrayList<Object>();
        values.add(businessId.value());
        values.add(customerId);
        if (beforeAt != null && beforeId != null) {
            values.add(time(beforeAt));
            values.add(time(beforeAt));
            values.add(beforeId);
        }
        values.add(limit);
        return dsl.fetch(sql, values.toArray()).map(row -> {
            var direction = row.get("direction", String.class);
            var payment = "DEBIT".equals(direction);
            return new ActivityRecord(row.get("id", UUID.class),
                    payment ? "PAYMENT_CONFIRMED" : "DEBT_CREATED",
                    payment ? "DECREASES_BALANCE" : "INCREASES_BALANCE",
                    row.get("amount", BigDecimal.class), "BRL",
                    payment ? "Pagamento confirmado" : "Compra fiada",
                    instant(row.get("created_at", OffsetDateTime.class)));
        });
    }

    @Override
    public Optional<ActivityRecord> findActivity(BusinessId businessId, UUID customerId, UUID activityId) {
        return listActivityById(businessId, customerId, activityId);
    }

    @Override
    @Transactional
    public UUID upsertPushSubscription(UUID id, BusinessId businessId, UUID channelId,
            UUID customerId, String endpoint, String p256dhKey, String authKey, Instant now) {
        var row = dsl.fetchOne("""
                INSERT INTO public.customer_push_subscriptions
                    (id, business_id, customer_channel_id, customer_id, endpoint,
                     p256dh_key, auth_key, created_at, updated_at, revoked_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ), NULL)
                ON CONFLICT (business_id, customer_channel_id, endpoint)
                DO UPDATE SET customer_id = EXCLUDED.customer_id,
                              p256dh_key = EXCLUDED.p256dh_key,
                              auth_key = EXCLUDED.auth_key,
                              updated_at = EXCLUDED.updated_at,
                              revoked_at = NULL
                RETURNING id
                """, id, businessId.value(), channelId, customerId, endpoint, p256dhKey, authKey,
                time(now), time(now));
        if (row == null) throw new IllegalStateException("push subscription upsert returned no id");
        return row.get("id", UUID.class);
    }

    @Override
    @Transactional(readOnly = true)
    public int countActivePushSubscriptions(BusinessId businessId, UUID customerId) {
        var value = dsl.fetchOne("""
                SELECT count(*) AS total
                  FROM public.customer_push_subscriptions
                 WHERE business_id = ? AND customer_id = ? AND revoked_at IS NULL
                """, businessId.value(), customerId);
        return value == null ? 0 : Math.toIntExact(value.get("total", Long.class));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PushSubscriptionRecord> listActivePushSubscriptions(
            BusinessId businessId, UUID customerId) {
        return dsl.fetch("""
                SELECT id, customer_channel_id, customer_id, endpoint, p256dh_key, auth_key
                  FROM public.customer_push_subscriptions
                 WHERE business_id = ? AND customer_id = ? AND revoked_at IS NULL
                 ORDER BY created_at, id
                """, businessId.value(), customerId).map(row -> new PushSubscriptionRecord(
                row.get("id", UUID.class), row.get("customer_channel_id", UUID.class),
                row.get("customer_id", UUID.class), row.get("endpoint", String.class),
                row.get("p256dh_key", String.class), row.get("auth_key", String.class)));
    }

    @Override
    @Transactional
    public void recordPushDelivery(UUID subscriptionId, Instant now) {
        dsl.execute("""
                UPDATE public.customer_push_subscriptions
                   SET last_success_at = CAST(? AS TIMESTAMPTZ), updated_at = CAST(? AS TIMESTAMPTZ)
                 WHERE id = ? AND revoked_at IS NULL
                """, time(now), time(now), subscriptionId);
    }

    @Override
    @Transactional
    public void revokePushSubscription(UUID subscriptionId, Instant now) {
        dsl.execute("""
                UPDATE public.customer_push_subscriptions
                   SET revoked_at = CAST(? AS TIMESTAMPTZ), updated_at = CAST(? AS TIMESTAMPTZ)
                 WHERE id = ? AND revoked_at IS NULL
                """, time(now), time(now), subscriptionId);
    }

    @Override
    @Transactional
    public void revokePushSubscriptionByEndpoint(BusinessId businessId, UUID channelId,
            UUID customerId, String endpoint, Instant now) {
        dsl.execute("""
                UPDATE public.customer_push_subscriptions
                   SET revoked_at = CAST(? AS TIMESTAMPTZ), updated_at = CAST(? AS TIMESTAMPTZ)
                 WHERE business_id = ? AND customer_channel_id = ? AND customer_id = ?
                   AND endpoint = ? AND revoked_at IS NULL
                """, time(now), time(now), businessId.value(), channelId, customerId, endpoint);
    }

    @Override
    @Transactional
    public void enqueuePushNotification(UUID id, BusinessId businessId, UUID customerId,
            UUID activityId, String kind, Instant createdAt) {
        dsl.execute("""
                INSERT INTO public.customer_push_outbox
                    (id, business_id, customer_id, activity_id, kind, created_at, available_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))
                ON CONFLICT (business_id, activity_id) DO NOTHING
                """, id, businessId.value(), customerId, activityId,
                kind, time(createdAt), time(createdAt));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PushNotificationRecord> findDuePushNotifications(Instant now, int limit) {
        enablePushDispatcherContext();
        return dsl.fetch("""
                SELECT id, business_id, customer_id, activity_id, kind, created_at, attempts
                  FROM public.customer_push_outbox
                 WHERE delivered_at IS NULL
                   AND available_at <= CAST(? AS TIMESTAMPTZ)
                   AND (locked_until IS NULL OR locked_until < CAST(? AS TIMESTAMPTZ))
                 ORDER BY available_at, created_at, id
                 LIMIT ?
                """, time(now), time(now), limit).map(row -> new PushNotificationRecord(
                row.get("id", UUID.class), new BusinessId(row.get("business_id", UUID.class)),
                row.get("customer_id", UUID.class), row.get("activity_id", UUID.class),
                row.get("kind", String.class), instant(row.get("created_at", OffsetDateTime.class)),
                row.get("attempts", Integer.class)));
    }

    @Override
    @Transactional
    public boolean claimPushNotification(UUID id, Instant now, Instant lockedUntil) {
        enablePushDispatcherContext();
        return dsl.execute("""
                UPDATE public.customer_push_outbox
                   SET locked_until = CAST(? AS TIMESTAMPTZ)
                 WHERE id = ? AND delivered_at IS NULL
                   AND (locked_until IS NULL OR locked_until < CAST(? AS TIMESTAMPTZ))
                """, time(lockedUntil), id, time(now)) == 1;
    }

    @Override
    @Transactional
    public void markPushNotificationDelivered(UUID id, Instant deliveredAt) {
        enablePushDispatcherContext();
        dsl.execute("""
                UPDATE public.customer_push_outbox
                   SET delivered_at = CAST(? AS TIMESTAMPTZ), locked_until = NULL, last_error = NULL
                 WHERE id = ? AND delivered_at IS NULL
                """, time(deliveredAt), id);
    }

    @Override
    @Transactional
    public void retryPushNotification(UUID id, Instant availableAt, String error) {
        enablePushDispatcherContext();
        dsl.execute("""
                UPDATE public.customer_push_outbox
                   SET attempts = attempts + 1, available_at = CAST(? AS TIMESTAMPTZ),
                       locked_until = NULL, last_error = ?
                 WHERE id = ? AND delivered_at IS NULL
                """, time(availableAt), error, id);
    }

    private void enablePushDispatcherContext() {
        dsl.execute("SELECT set_config('app.push_dispatcher', 'true', true)");
    }

    private Optional<ActivityRecord> listActivityById(BusinessId businessId, UUID customerId, UUID activityId) {
        var row = dsl.fetchOne("""
                SELECT id, direction, amount, created_at
                  FROM public.credit_ledger_entries
                 WHERE business_id = ? AND customer_id = ? AND id = ?
                """, businessId.value(), customerId, activityId);
        if (row == null) return Optional.empty();
        var payment = "DEBIT".equals(row.get("direction", String.class));
        return Optional.of(new ActivityRecord(row.get("id", UUID.class),
                payment ? "PAYMENT_CONFIRMED" : "DEBT_CREATED",
                payment ? "DECREASES_BALANCE" : "INCREASES_BALANCE",
                row.get("amount", BigDecimal.class), "BRL",
                payment ? "Pagamento confirmado" : "Compra fiada",
                instant(row.get("created_at", OffsetDateTime.class))));
    }

    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private static Instant instant(OffsetDateTime value) { return value == null ? null : value.toInstant(); }
}
