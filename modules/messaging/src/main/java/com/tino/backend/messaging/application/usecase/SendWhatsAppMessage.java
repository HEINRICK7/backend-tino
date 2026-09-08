package com.tino.backend.messaging.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.messaging.application.exception.MessagingConflictException;
import com.tino.backend.messaging.application.exception.WhatsAppMessageNotFoundException;
import com.tino.backend.messaging.application.exception.WhatsAppPreviewStaleException;
import com.tino.backend.messaging.application.model.WhatsAppDeliveryView;
import com.tino.backend.messaging.application.model.WhatsAppSendResult;
import com.tino.backend.messaging.application.port.out.WhatsAppMessageRepository;
import com.tino.backend.messaging.application.port.out.WhatsAppStatementDataSource;
import com.tino.backend.messaging.application.service.WhatsAppPhoneNumbers;
import com.tino.backend.messaging.domain.model.WhatsAppMessageType;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public final class SendWhatsAppMessage {
    private final BusinessAuthorization authorization;
    private final WhatsAppStatementDataSource statements;
    private final WhatsAppMessageRepository messages;
    private final UuidGenerator ids;
    private final Clock clock;

    public SendWhatsAppMessage(BusinessAuthorization authorization, WhatsAppStatementDataSource statements,
            WhatsAppMessageRepository messages, UuidGenerator ids, Clock clock) {
        this.authorization = authorization;
        this.statements = statements;
        this.messages = messages;
        this.ids = ids;
        this.clock = clock;
    }

    public WhatsAppSendResult execute(UUID userId, BusinessId businessId, UUID accountId, UUID previewId,
            WhatsAppMessageType expectedType, String idempotencyKey, String requestFingerprint) {
        validateKey(idempotencyKey);
        validateFingerprint(requestFingerprint);
        return authorization.execute(userId, businessId, tenant -> {
            var existing = messages.findDeliveryByIdempotency(tenant, idempotencyKey);
            if (existing.isPresent()) {
                var previous = existing.orElseThrow();
                if (!previous.requestFingerprint().equals(requestFingerprint)) {
                    throw new MessagingConflictException();
                }
                return new WhatsAppSendResult(view(previous), true);
            }
            var preview = messages.findPreview(tenant, previewId).orElseThrow(WhatsAppMessageNotFoundException::new);
            var now = Instant.now(clock);
            if (now.compareTo(preview.expiresAt()) >= 0 || !preview.accountId().equals(accountId)
                    || (expectedType != null && preview.messageType() != expectedType)) {
                throw new WhatsAppPreviewStaleException();
            }
            var current = statements.find(tenant, accountId).orElseThrow(WhatsAppMessageNotFoundException::new);
            if (current.accountVersion() != preview.snapshotVersion()) {
                throw new WhatsAppPreviewStaleException();
            }
            WhatsAppPhoneNumbers.normalize(current.recipientPhone());
            var delivery = new WhatsAppMessageRepository.DeliveryRecord(ids.next(), tenant, preview.id(), accountId,
                    current.customerId(), preview.messageType(), preview.templateId(), preview.templateVersion(),
                    preview.renderedHash(), idempotencyKey, requestFingerprint, "WA_EVOLUTION", null, "QUEUED", 0,
                    now, null, null, null, now, now);
            if (!messages.insertDelivery(delivery)) {
                var concurrent = messages.findDeliveryByIdempotency(tenant, idempotencyKey)
                        .orElseThrow(MessagingConflictException::new);
                if (!concurrent.requestFingerprint().equals(requestFingerprint)) {
                    throw new MessagingConflictException();
                }
                return new WhatsAppSendResult(view(concurrent), true);
            }
            messages.insertAudit(new WhatsAppMessageRepository.AuditRecord(ids.next(), tenant, delivery.id(), "QUEUED",
                    delivery.messageType(), delivery.templateId(), delivery.templateVersion(), delivery.renderedHash(), userId, null, now));
            return new WhatsAppSendResult(view(delivery), false);
        });
    }

    private static WhatsAppDeliveryView view(WhatsAppMessageRepository.DeliveryRecord delivery) {
        return new WhatsAppDeliveryView(delivery.id(), delivery.status(), delivery.providerMessageId(),
                delivery.attemptCount(), delivery.sentAt(), delivery.failedAt());
    }

    private static void validateKey(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
    }

    private static void validateFingerprint(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid request fingerprint");
        }
    }
}
