package com.tino.backend.messaging.application.port.out;

import com.tino.backend.messaging.domain.model.WhatsAppMessageType;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WhatsAppMessageRepository {
    void insertPreview(PreviewRecord preview);

    void insertAudit(AuditRecord audit);

    Optional<PreviewRecord> findPreview(BusinessId businessId, UUID previewId);

    Optional<DeliveryRecord> findDelivery(BusinessId businessId, UUID messageId);

    Optional<DeliveryRecord> findDeliveryByIdempotency(BusinessId businessId, String idempotencyKey);

    boolean insertDelivery(DeliveryRecord delivery);

    Optional<DeliveryRecord> claimDelivery(BusinessId businessId, UUID messageId, Instant now);

    DeliveryRecord markSent(BusinessId businessId, UUID messageId, String provider,
            String providerMessageId, Instant now);

    DeliveryRecord markFailed(BusinessId businessId, UUID messageId, String error, Instant availableAt,
            Instant now);

    void deleteExpiredPreviews(BusinessId businessId, Instant now);

    record PreviewRecord(
            UUID id,
            BusinessId businessId,
            UUID accountId,
            UUID customerId,
            WhatsAppMessageType messageType,
            String templateId,
            String templateVersion,
            String renderedHash,
            long snapshotVersion,
            String text,
            String mimeType,
            byte[] media,
            String filename,
            Instant createdAt,
            Instant expiresAt) {}

    record DeliveryRecord(
            UUID id,
            BusinessId businessId,
            UUID previewId,
            UUID accountId,
            UUID customerId,
            WhatsAppMessageType messageType,
            String templateId,
            String templateVersion,
            String renderedHash,
            String idempotencyKey,
            String requestFingerprint,
            String provider,
            String providerMessageId,
            String status,
            int attemptCount,
            Instant availableAt,
            Instant sentAt,
            Instant failedAt,
            String lastError,
            Instant createdAt,
            Instant updatedAt) {}

    record AuditRecord(
            UUID id,
            BusinessId businessId,
            UUID deliveryId,
            String eventType,
            WhatsAppMessageType messageType,
            String templateId,
            String templateVersion,
            String renderedHash,
            UUID actorUserId,
            String providerMessageId,
            Instant createdAt) {}
}
