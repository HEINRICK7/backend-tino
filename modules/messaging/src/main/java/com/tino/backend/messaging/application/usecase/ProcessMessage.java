package com.tino.backend.messaging.application.usecase;
import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.messaging.application.exception.MessageNotFoundException;
import com.tino.backend.messaging.application.model.MessageCommandResult;
import com.tino.backend.messaging.application.model.MessageView;
import com.tino.backend.messaging.application.port.out.*;
import com.tino.backend.messaging.domain.model.MessageStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock; import java.time.Instant; import java.util.UUID;
public final class ProcessMessage {
    private final BusinessAuthorization authorization; private final MessagingRepository messaging; private final MessageProvider provider; private final Clock clock;
    public ProcessMessage(BusinessAuthorization authorization, MessagingRepository messaging, MessageProvider provider, Clock clock) { this.authorization = authorization; this.messaging = messaging; this.provider = provider; this.clock = clock; }
    public MessageCommandResult execute(UUID userId, BusinessId businessId, UUID messageId) {
        var work = authorization.execute(userId, businessId, tenant -> {
            var message = messaging.find(tenant, messageId).orElseThrow(MessageNotFoundException::new);
            if (message.status() != MessageStatus.QUEUED && message.status() != MessageStatus.FAILED) return new Work(message, null);
            return new Work(message, messaging.claimOutbox(tenant, messageId, Instant.now(clock)).orElse(null));
        });
        if (work.command() == null) return new MessageCommandResult(MessageView.from(work.message()), true);
        MessageProvider.Delivery delivery;
        try {
            delivery = provider.deliver(work.message());
        } catch (RuntimeException failure) {
            var finalAttempt = work.command().attemptCount() >= 3;
            return authorization.execute(userId, businessId, tenant -> {
                var now = Instant.now(clock);
                var eventId = "failure-" + messageId + "-" + work.command().attemptCount();
                var updated = messaging.applyFailure(tenant, messageId, provider.name(), eventId, "unavailable", finalAttempt, now);
                messaging.failOutbox(tenant, work.command().id(), now, failure.getClass().getSimpleName(), finalAttempt);
                return new MessageCommandResult(MessageView.from(updated), false);
            });
        }
        return authorization.execute(userId, businessId, tenant -> {
            var updated = messaging.applyDelivery(tenant, messageId, provider.name(), delivery.eventId(), delivery.providerMessageId(), MessageStatus.SENT, Instant.now(clock));
            messaging.completeOutbox(tenant, work.command().id(), Instant.now(clock));
            return new MessageCommandResult(MessageView.from(updated), false);
        });
    }
    private record Work(com.tino.backend.messaging.domain.model.Message message, MessagingRepository.OutboxCommand command) {}
}
