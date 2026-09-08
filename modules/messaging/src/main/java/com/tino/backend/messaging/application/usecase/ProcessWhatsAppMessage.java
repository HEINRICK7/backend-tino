package com.tino.backend.messaging.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.messaging.application.model.WhatsAppDeliveryView;
import com.tino.backend.messaging.application.port.out.WhatsAppDeliveryPort;
import com.tino.backend.messaging.application.port.out.WhatsAppMessageRepository;
import com.tino.backend.messaging.application.service.WhatsAppPhoneNumbers;
import com.tino.backend.messaging.application.exception.WhatsAppMessageNotFoundException;
import com.tino.backend.messaging.application.exception.WhatsAppSendFailedException;
import com.tino.backend.messaging.application.exception.WhatsAppPhoneInvalidException;
import com.tino.backend.messaging.application.exception.WhatsAppPhoneMissingException;
import com.tino.backend.messaging.application.port.out.WhatsAppStatementDataSource;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import com.tino.backend.messaging.application.port.out.WhatsAppMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class ProcessWhatsAppMessage {
    private final BusinessAuthorization authorization;
    private final WhatsAppMessageRepository messages;
    private final WhatsAppStatementDataSource statements;
    private final WhatsAppDeliveryPort delivery;
    private final UuidGenerator ids;
    private final Clock clock;
    private final WhatsAppMetrics metrics;

    public ProcessWhatsAppMessage(BusinessAuthorization authorization, WhatsAppMessageRepository messages,
            WhatsAppStatementDataSource statements, WhatsAppDeliveryPort delivery, UuidGenerator ids, Clock clock) {
        this(authorization, messages, statements, delivery, ids, clock, WhatsAppMetrics.noop());
    }

    public ProcessWhatsAppMessage(BusinessAuthorization authorization, WhatsAppMessageRepository messages,
            WhatsAppStatementDataSource statements, WhatsAppDeliveryPort delivery, UuidGenerator ids, Clock clock,
            WhatsAppMetrics metrics) {
        this.authorization = authorization;
        this.messages = messages;
        this.statements = statements;
        this.delivery = delivery;
        this.ids = ids;
        this.clock = clock;
        this.metrics = metrics;
    }

    public WhatsAppDeliveryView execute(UUID userId, BusinessId businessId, UUID messageId) {
        var work = authorization.execute(userId, businessId, tenant -> {
            var current = messages.findDelivery(tenant, messageId).orElseThrow(WhatsAppMessageNotFoundException::new);
            if ("SENT".equals(current.status()) || "SENDING".equals(current.status())) {
                return new Work(current, null, null, null);
            }
            var claimed = messages.claimDelivery(tenant, messageId, Instant.now(clock)).orElse(null);
            if (claimed == null) {
                return new Work(current, null, null, null);
            }
            var preview = messages.findPreview(tenant, claimed.previewId()).orElseThrow(WhatsAppMessageNotFoundException::new);
            var snapshot = statements.find(tenant, claimed.accountId()).orElseThrow(WhatsAppMessageNotFoundException::new);
            return new Work(claimed, preview, snapshot.recipientPhone(), tenant);
        });
        if (work.preview() == null) {
            return view(work.delivery());
        }

        WhatsAppDeliveryPort.DeliveryResult result;
        var timer = metrics.startSend();
        metrics.sendStarted();
        try {
            var phone = WhatsAppPhoneNumbers.normalize(work.recipientPhone());
            result = delivery.send(new WhatsAppDeliveryPort.WhatsAppDeliveryCommand(work.delivery().id(),
                    work.delivery().idempotencyKey(), phone, work.preview().text(), work.preview().mimeType(),
                    work.preview().filename(), work.preview().media()));
        } catch (WhatsAppPhoneMissingException | WhatsAppPhoneInvalidException failure) {
            metrics.sendFailed();
            metrics.stopSend(timer);
            return authorization.execute(userId, businessId, tenant -> fail(tenant, work.delivery(), failure, userId));
        } catch (RuntimeException failure) {
            metrics.sendFailed();
            metrics.providerError();
            metrics.stopSend(timer);
            return authorization.execute(userId, businessId, tenant -> fail(tenant, work.delivery(),
                    new WhatsAppSendFailedException(failure), userId));
        }
        metrics.stopSend(timer);
        return authorization.execute(userId, businessId, tenant -> {
            var now = Instant.now(clock);
            var updated = messages.markSent(tenant, messageId, result.provider(), result.providerMessageId(), now);
            messages.insertAudit(new WhatsAppMessageRepository.AuditRecord(ids.next(), tenant, messageId, "SENT",
                    updated.messageType(), updated.templateId(), updated.templateVersion(), updated.renderedHash(), userId,
                    result.providerMessageId(), now));
            return view(updated);
        });
    }

    private WhatsAppDeliveryView fail(BusinessId tenant, WhatsAppMessageRepository.DeliveryRecord delivery,
            RuntimeException failure, UUID actorUserId) {
        var now = Instant.now(clock);
        var retryAt = now.plus(Duration.ofSeconds(Math.min(300, 10L * (1L << Math.min(delivery.attemptCount(), 4)))));
        var updated = messages.markFailed(tenant, delivery.id(), failure.getClass().getSimpleName(), retryAt, now);
        messages.insertAudit(new WhatsAppMessageRepository.AuditRecord(ids.next(), tenant, delivery.id(), "FAILED",
                updated.messageType(), updated.templateId(), updated.templateVersion(), updated.renderedHash(), actorUserId, null, now));
        return view(updated);
    }

    private static WhatsAppDeliveryView view(WhatsAppMessageRepository.DeliveryRecord delivery) {
        return new WhatsAppDeliveryView(delivery.id(), delivery.status(), delivery.providerMessageId(),
                delivery.attemptCount(), delivery.sentAt(), delivery.failedAt());
    }

    private record Work(WhatsAppMessageRepository.DeliveryRecord delivery,
            WhatsAppMessageRepository.PreviewRecord preview, String recipientPhone, BusinessId tenant) {}
}
