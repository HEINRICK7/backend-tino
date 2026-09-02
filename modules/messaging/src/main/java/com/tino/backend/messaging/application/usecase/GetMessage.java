package com.tino.backend.messaging.application.usecase;
import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.messaging.application.exception.MessageNotFoundException;
import com.tino.backend.messaging.application.model.MessageView;
import com.tino.backend.messaging.application.port.out.MessagingRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.UUID;
public final class GetMessage {
    private final BusinessAuthorization authorization; private final MessagingRepository messaging;
    public GetMessage(BusinessAuthorization authorization, MessagingRepository messaging) { this.authorization = authorization; this.messaging = messaging; }
    public MessageView execute(UUID userId, BusinessId businessId, UUID messageId) { return authorization.execute(userId, businessId,
            tenant -> MessageView.from(messaging.find(tenant, messageId).orElseThrow(MessageNotFoundException::new))); }
}
