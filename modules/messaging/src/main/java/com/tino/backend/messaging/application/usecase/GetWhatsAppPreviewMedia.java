package com.tino.backend.messaging.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.messaging.application.exception.WhatsAppPreviewNotFoundException;
import com.tino.backend.messaging.application.port.out.WhatsAppMessageRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public final class GetWhatsAppPreviewMedia {
    private final BusinessAuthorization authorization;
    private final WhatsAppMessageRepository messages;
    private final Clock clock;

    public GetWhatsAppPreviewMedia(BusinessAuthorization authorization, WhatsAppMessageRepository messages, Clock clock) {
        this.authorization = authorization;
        this.messages = messages;
        this.clock = clock;
    }

    public WhatsAppMessageRepository.PreviewRecord execute(UUID userId, BusinessId businessId, UUID previewId) {
        return authorization.execute(userId, businessId, tenant -> messages.findPreview(tenant, previewId)
                .filter(preview -> Instant.now(clock).isBefore(preview.expiresAt()))
                .orElseThrow(WhatsAppPreviewNotFoundException::new));
    }
}
