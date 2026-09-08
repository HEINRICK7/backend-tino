package com.tino.backend.messaging.application.service;

import com.tino.backend.messaging.application.model.MessageCardModel;
import com.tino.backend.messaging.application.model.RenderedWhatsAppMessage;
import com.tino.backend.messaging.application.model.WhatsAppStatementSnapshot;
import com.tino.backend.messaging.application.port.out.WhatsAppMetrics;
import com.tino.backend.messaging.application.port.out.WhatsAppCardRenderer;
import com.tino.backend.messaging.domain.model.WhatsAppMessageType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class RenderWhatsAppMessage {
    public static final String TEMPLATE_VERSION = "v1";
    private final WhatsAppTextComposer textComposer;
    private final WhatsAppCardRenderer cardRenderer;
    private final WhatsAppMetrics metrics;

    public RenderWhatsAppMessage(WhatsAppTextComposer textComposer, WhatsAppCardRenderer cardRenderer) {
        this(textComposer, cardRenderer, WhatsAppMetrics.noop());
    }

    public RenderWhatsAppMessage(WhatsAppTextComposer textComposer, WhatsAppCardRenderer cardRenderer,
            WhatsAppMetrics metrics) {
        this.textComposer = Objects.requireNonNull(textComposer, "textComposer");
        this.cardRenderer = Objects.requireNonNull(cardRenderer, "cardRenderer");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public RenderedWhatsAppMessage execute(WhatsAppMessageType type, WhatsAppStatementSnapshot snapshot) {
        metrics.renderStarted();
        try {
            var media = cardRenderer.render(new MessageCardModel(type, snapshot.statement()));
            var text = textComposer.compose(type, snapshot);
            var templateId = type.templateId();
            var canonical = templateId + "\u0000" + TEMPLATE_VERSION + "\u0000"
                    + text + "\u0000" + media.sha256() + "\u0000" + snapshot.accountVersion();
            return new RenderedWhatsAppMessage(templateId, TEMPLATE_VERSION, text, media, List.of(), sha256(canonical));
        } catch (RuntimeException exception) {
            metrics.renderFailed();
            throw exception;
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
