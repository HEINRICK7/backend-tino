package com.tino.backend.messaging.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.messaging.application.model.ConsentView;
import com.tino.backend.messaging.application.port.out.MessagingRepository;
import com.tino.backend.messaging.domain.model.*;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public final class SetConsent {
    private final BusinessAuthorization authorization; private final MessagingRepository messaging;
    private final UuidGenerator ids; private final Clock clock;
    public SetConsent(BusinessAuthorization authorization, MessagingRepository messaging, UuidGenerator ids, Clock clock) {
        this.authorization = authorization; this.messaging = messaging; this.ids = ids; this.clock = clock;
    }
    public ConsentView execute(UUID userId, BusinessId businessId, UUID customerId, MessageChannel channel,
            MessagePurpose purpose, boolean granted, String recipientRef) {
        if (channel != MessageChannel.WHATSAPP || purpose == null || recipientRef == null
                || recipientRef.isBlank() || recipientRef.length() > 200) throw new IllegalArgumentException("invalid consent");
        return authorization.execute(userId, businessId, tenant -> {
            if (!messaging.customerExists(tenant, customerId)) throw new com.tino.backend.messaging.application.exception.MessagingCustomerNotFoundException();
            var now = Instant.now(clock); var hash = digest(recipientRef);
            var consent = messaging.upsertConsent(tenant, customerId, channel, purpose, granted, hash, now);
            messaging.insertConsentAudit(new MessagingRepository.ConsentAudit(ids.next(), tenant, customerId,
                    channel, purpose, granted, hash, userId, now));
            return new ConsentView(channel.name(), purpose.name(), consent.granted(), consent.version());
        });
    }
    static String digest(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
