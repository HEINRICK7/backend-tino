package com.tino.backend.messaging.application.model;

import com.tino.backend.messaging.domain.model.WhatsAppMessageType;
import java.time.Instant;
import java.util.UUID;

public record WhatsAppPreviewView(
        UUID previewId,
        WhatsAppMessageType messageType,
        String text,
        PreviewMediaView media,
        String renderedHash,
        Instant expiresAt) {
    public record PreviewMediaView(String mimeType, String url) {}
}
