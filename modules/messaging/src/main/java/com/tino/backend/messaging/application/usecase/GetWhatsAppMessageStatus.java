package com.tino.backend.messaging.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.messaging.application.exception.WhatsAppMessageNotFoundException;
import com.tino.backend.messaging.application.model.WhatsAppDeliveryView;
import com.tino.backend.messaging.application.port.out.WhatsAppMessageRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.UUID;

public final class GetWhatsAppMessageStatus {
    private final BusinessAuthorization authorization;
    private final WhatsAppMessageRepository messages;

    public GetWhatsAppMessageStatus(BusinessAuthorization authorization, WhatsAppMessageRepository messages) {
        this.authorization = authorization;
        this.messages = messages;
    }

    public WhatsAppDeliveryView execute(UUID userId, BusinessId businessId, UUID messageId) {
        return authorization.execute(userId, businessId, tenant -> messages.findDelivery(tenant, messageId)
                .map(GetWhatsAppMessageStatus::view).orElseThrow(WhatsAppMessageNotFoundException::new));
    }

    private static WhatsAppDeliveryView view(WhatsAppMessageRepository.DeliveryRecord delivery) {
        return new WhatsAppDeliveryView(delivery.id(), delivery.status(), delivery.providerMessageId(),
                delivery.attemptCount(), delivery.sentAt(), delivery.failedAt());
    }
}
