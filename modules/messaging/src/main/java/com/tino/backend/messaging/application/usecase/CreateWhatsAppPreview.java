package com.tino.backend.messaging.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.messaging.application.exception.WhatsAppMessageNotFoundException;
import com.tino.backend.messaging.application.exception.WhatsAppRenderFailedException;
import com.tino.backend.messaging.application.model.WhatsAppPreviewView;
import com.tino.backend.messaging.application.model.RenderedWhatsAppMessage;
import com.tino.backend.messaging.application.port.out.WhatsAppCardRenderer;
import com.tino.backend.messaging.application.port.out.WhatsAppMessageRepository;
import com.tino.backend.messaging.application.port.out.WhatsAppStatementDataSource;
import com.tino.backend.messaging.application.service.RenderWhatsAppMessage;
import com.tino.backend.messaging.application.service.WhatsAppPhoneNumbers;
import com.tino.backend.messaging.domain.model.WhatsAppMessageType;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class CreateWhatsAppPreview {
    private static final Duration TTL = Duration.ofMinutes(15);
    private final BusinessAuthorization authorization;
    private final WhatsAppStatementDataSource statements;
    private final WhatsAppMessageRepository messages;
    private final RenderWhatsAppMessage renderer;
    private final UuidGenerator ids;
    private final Clock clock;

    public CreateWhatsAppPreview(BusinessAuthorization authorization, WhatsAppStatementDataSource statements,
            WhatsAppMessageRepository messages, RenderWhatsAppMessage renderer, UuidGenerator ids, Clock clock) {
        this.authorization = authorization;
        this.statements = statements;
        this.messages = messages;
        this.renderer = renderer;
        this.ids = ids;
        this.clock = clock;
    }

    public WhatsAppPreviewView execute(UUID userId, BusinessId businessId, UUID accountId,
            WhatsAppMessageType messageType) {
        if (messageType == null) {
            throw new IllegalArgumentException("messageType is required");
        }
        return authorization.execute(userId, businessId, tenant -> {
            var snapshot = statements.find(tenant, accountId).orElseThrow(WhatsAppMessageNotFoundException::new);
            WhatsAppPhoneNumbers.normalize(snapshot.recipientPhone());
            RenderedWhatsAppMessage rendered;
            try {
                rendered = renderer.execute(messageType, snapshot);
            } catch (RuntimeException exception) {
                if (exception instanceof WhatsAppRenderFailedException failed) {
                    throw failed;
                }
                throw new WhatsAppRenderFailedException(exception);
            }
            var now = Instant.now(clock);
            var preview = new WhatsAppMessageRepository.PreviewRecord(ids.next(), tenant, snapshot.accountId(),
                    snapshot.customerId(), messageType, rendered.templateId(), rendered.templateVersion(),
                    rendered.renderedHash(), snapshot.accountVersion(), rendered.text(), rendered.media().mimeType(),
                    rendered.media().content(), rendered.media().filename(), now, now.plus(TTL));
            messages.deleteExpiredPreviews(tenant, now);
            messages.insertPreview(preview);
            messages.insertAudit(new WhatsAppMessageRepository.AuditRecord(ids.next(), tenant, null,
                    "PREVIEW_CREATED", messageType, rendered.templateId(), rendered.templateVersion(),
                    rendered.renderedHash(), userId, null, now));
            return new WhatsAppPreviewView(preview.id(), messageType, rendered.text(),
                    new WhatsAppPreviewView.PreviewMediaView(rendered.media().mimeType(),
                            "/api/v1/whatsapp-previews/" + preview.id() + "/media"), rendered.renderedHash(), preview.expiresAt());
        });
    }
}
