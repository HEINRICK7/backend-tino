package com.tino.backend.messaging.application.model;

import java.util.List;
import java.util.Objects;

public record RenderedWhatsAppMessage(
        String templateId,
        String templateVersion,
        String text,
        RenderedMedia media,
        List<InteractiveAction> actions,
        String renderedHash) {
    public RenderedWhatsAppMessage {
        if (templateId == null || templateId.isBlank() || templateVersion == null || templateVersion.isBlank()
                || text == null || text.isBlank() || renderedHash == null || !renderedHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid rendered WhatsApp message");
        }
        Objects.requireNonNull(media, "media");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
    }
}
