package com.tino.backend.messaging.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.messaging.application.exception.*;
import com.tino.backend.messaging.application.model.*;
import com.tino.backend.messaging.application.port.out.MessagingRepository;
import com.tino.backend.messaging.domain.model.*;
import com.tino.backend.shared.kernel.*;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public final class QueueMessage {
    private final BusinessAuthorization authorization; private final MessagingRepository messaging;
    private final UuidGenerator ids; private final Clock clock;
    public QueueMessage(BusinessAuthorization authorization, MessagingRepository messaging, UuidGenerator ids, Clock clock) {
        this.authorization = authorization; this.messaging = messaging; this.ids = ids; this.clock = clock;
    }
    public MessageCommandResult execute(UUID userId, BusinessId businessId, UUID customerId, MessageChannel channel,
            MessagePurpose purpose, MessageTemplate template, String key, String fingerprint) {
        if (channel != MessageChannel.WHATSAPP || purpose == null || template == null || key == null || key.isBlank()
                || key.length() > 200 || fingerprint == null || fingerprint.length() != 64) throw new IllegalArgumentException("invalid message request");
        return authorization.execute(userId, businessId, tenant -> {
            if (!messaging.customerExists(tenant, customerId)) throw new MessagingCustomerNotFoundException();
            var previous = messaging.findByIdempotency(tenant, key);
            if (previous.isPresent()) {
                if (!previous.orElseThrow().requestFingerprint().equals(fingerprint)) throw new MessagingConflictException();
                return new MessageCommandResult(MessageView.from(previous.orElseThrow()), true);
            }
            var consent = messaging.findConsent(tenant, customerId, channel, purpose)
                    .filter(MessagingRepository.ConsentRecord::granted).orElseThrow(ConsentRequiredException::new);
            var now = Instant.now(clock);
            var message = new Message(ids.next(), tenant, customerId, channel, purpose, template,
                    consent.recipientHash(), key, fingerprint, MessageStatus.QUEUED, "sandbox", null, 0, now, now);
            messaging.insert(message); messaging.enqueue(new MessagingRepository.OutboxCommand(ids.next(), tenant,
                    message.id(), "DELIVER_MESSAGE", "PENDING", 0, now, now));
            return new MessageCommandResult(MessageView.from(message), false);
        });
    }
}
