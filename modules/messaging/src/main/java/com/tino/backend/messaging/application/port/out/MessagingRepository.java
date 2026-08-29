package com.tino.backend.messaging.application.port.out;

import com.tino.backend.messaging.domain.model.*;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MessagingRepository {
    boolean customerExists(BusinessId businessId, UUID customerId);
    Optional<ConsentRecord> findConsent(BusinessId businessId, UUID customerId, MessageChannel channel, MessagePurpose purpose);
    ConsentRecord upsertConsent(BusinessId businessId, UUID customerId, MessageChannel channel, MessagePurpose purpose,
            boolean granted, String recipientHash, Instant updatedAt);
    void insertConsentAudit(ConsentAudit audit);
    Optional<Message> find(BusinessId businessId, UUID messageId);
    Optional<Message> findByIdempotency(BusinessId businessId, String key);
    void insert(Message message);
    void enqueue(OutboxCommand command);
    Optional<OutboxCommand> claimOutbox(BusinessId businessId, UUID messageId, Instant now);
    void completeOutbox(BusinessId businessId, UUID outboxId, Instant completedAt);
    void failOutbox(BusinessId businessId, UUID outboxId, Instant availableAt, String error, boolean deadLetter);
    Message applyDelivery(BusinessId businessId, UUID messageId, String provider, String eventId,
            String providerMessageId, MessageStatus status, Instant createdAt);
    Message applyFailure(BusinessId businessId, UUID messageId, String provider, String eventId,
            String providerMessageId, boolean deadLetter, Instant createdAt);

    record ConsentRecord(boolean granted, String recipientHash, long version) {}
    record ConsentAudit(UUID id, BusinessId businessId, UUID customerId, MessageChannel channel,
            MessagePurpose purpose, boolean granted, String recipientHash, UUID actorUserId, Instant createdAt) {}
    record OutboxCommand(UUID id, BusinessId businessId, UUID messageId, String commandType, String state,
            int attemptCount, Instant availableAt, Instant createdAt) {}
}
