package com.tino.backend.customerchannel.application.port.out;

import com.tino.backend.customerchannel.domain.model.CustomerChannelStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerChannelRepository {
    ChannelRecord upsertChannel(UUID id, BusinessId businessId, UUID customerId, Instant now);

    Optional<ChannelRecord> findChannel(BusinessId businessId, UUID customerId);

    Optional<InviteIdempotencyRecord> findInviteIdempotency(BusinessId businessId,
            String operation, String idempotencyKey);

    boolean claimInviteIdempotency(BusinessId businessId, String operation, String idempotencyKey,
            String requestFingerprint, UUID channelId, Instant createdAt);

    void completeInviteIdempotency(BusinessId businessId, String operation, String idempotencyKey,
            String responseStatus, String deliveryStatus);

    Optional<ActivationIdempotencyRecord> findActivationIdempotency(String operation,
            String idempotencyKey);

    boolean claimActivationIdempotency(String operation, String idempotencyKey,
            String requestFingerprint, Instant createdAt);

    void completeActivationIdempotency(String operation, String idempotencyKey,
            BusinessId businessId, UUID channelId, UUID customerId, UUID sessionId,
            Instant completedAt);

    void revokeOpenInvites(BusinessId businessId, UUID channelId, Instant now);

    void insertInvite(UUID id, BusinessId businessId, UUID channelId, String tokenHash,
            Instant expiresAt, Instant createdAt);

    Optional<InviteRecord> findValidInvite(String tokenHash, Instant now);

    void consumeInvite(UUID inviteId, Instant now);

    void insertSession(UUID id, UUID channelId, BusinessId businessId, UUID customerId,
            String tokenHash, Instant createdAt, Instant expiresAt);

    Optional<SessionRecord> findActiveSession(String tokenHash, Instant now);

    void revokeSession(UUID sessionId, Instant now);

    void touchChannel(UUID channelId, Instant now);

    Optional<HomeRecord> findHome(BusinessId businessId, UUID channelId, UUID customerId);

    List<ActivityRecord> listActivity(BusinessId businessId, UUID customerId,
            Instant beforeAt, UUID beforeId, int limit);

    Optional<ActivityRecord> findActivity(BusinessId businessId, UUID customerId, UUID activityId);

    default UUID upsertPushSubscription(UUID id, BusinessId businessId, UUID channelId,
            UUID customerId, String endpoint, String p256dhKey, String authKey, Instant now) {
        throw new UnsupportedOperationException("push subscriptions are not available");
    }

    default int countActivePushSubscriptions(BusinessId businessId, UUID customerId) {
        return 0;
    }

    default List<PushSubscriptionRecord> listActivePushSubscriptions(BusinessId businessId, UUID customerId) {
        return List.of();
    }

    default void recordPushDelivery(UUID subscriptionId, Instant now) {
        throw new UnsupportedOperationException("push subscriptions are not available");
    }

    default void revokePushSubscription(UUID subscriptionId, Instant now) {
        throw new UnsupportedOperationException("push subscriptions are not available");
    }

    default void revokePushSubscriptionByEndpoint(BusinessId businessId, UUID channelId,
            UUID customerId, String endpoint, Instant now) {
        throw new UnsupportedOperationException("push subscriptions are not available");
    }

    default void enqueuePushNotification(UUID id, BusinessId businessId, UUID customerId,
            UUID activityId, String kind, Instant createdAt) {
        throw new UnsupportedOperationException("push notifications are not available");
    }

    default List<PushNotificationRecord> findDuePushNotifications(Instant now, int limit) {
        return List.of();
    }

    default boolean claimPushNotification(UUID id, Instant now, Instant lockedUntil) {
        return false;
    }

    default void markPushNotificationDelivered(UUID id, Instant deliveredAt) {
        throw new UnsupportedOperationException("push notifications are not available");
    }

    default void retryPushNotification(UUID id, Instant availableAt, String error) {
        throw new UnsupportedOperationException("push notifications are not available");
    }

    record ChannelRecord(UUID id, BusinessId businessId, UUID customerId,
            CustomerChannelStatus status, Instant activatedAt, Instant lastAccessAt) {}

    record InviteRecord(UUID id, UUID channelId, BusinessId businessId, UUID customerId,
            Instant expiresAt) {}

    record InviteIdempotencyRecord(String requestFingerprint, UUID channelId,
            String responseStatus, String deliveryStatus) {}

    record ActivationIdempotencyRecord(String requestFingerprint, BusinessId businessId,
            UUID channelId, UUID customerId, UUID sessionId) {}

    record SessionRecord(UUID id, UUID channelId, BusinessId businessId, UUID customerId,
            Instant expiresAt) {}

    record HomeRecord(CustomerChannelStatus channelStatus, String customerName, String businessName,
            String accountStatus, BigDecimal balance, String currency, long version, Instant asOf) {}

    record ActivityRecord(UUID id, String type, String impact, BigDecimal amount,
            String currency, String label, Instant occurredAt) {}

    record PushSubscriptionRecord(UUID id, UUID channelId, UUID customerId, String endpoint,
            String p256dhKey, String authKey) {}

    record PushNotificationRecord(UUID id, BusinessId businessId, UUID customerId, UUID activityId,
            String kind, Instant createdAt, int attempts) {}
}
