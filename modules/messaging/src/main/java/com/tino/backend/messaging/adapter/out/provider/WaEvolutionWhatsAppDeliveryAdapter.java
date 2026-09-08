package com.tino.backend.messaging.adapter.out.provider;

import com.tino.backend.messaging.application.port.out.WhatsAppDeliveryPort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/** Java-side adapter for the private Go delivery contract. */
public final class WaEvolutionWhatsAppDeliveryAdapter implements WhatsAppDeliveryPort {
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String token;
    private final Duration timeout;

    public WaEvolutionWhatsAppDeliveryAdapter(URI endpoint, String token, Duration timeout,
            HttpClient http, ObjectMapper mapper) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.token = token == null ? "" : token;
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.http = Objects.requireNonNull(http, "http");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public DeliveryResult send(WhatsAppDeliveryCommand command) {
        if (token.isBlank()) {
            throw new IllegalStateException("WhatsApp delivery token is missing");
        }
        try {
            var body = mapper.writeValueAsString(Map.of(
                    "message_id", command.messageId().toString(),
                    "idempotency_key", command.idempotencyKey(),
                    "recipient", command.recipientPhone(),
                    "text", command.text(),
                    "mime_type", command.mimeType(),
                    "filename", command.filename(),
                    "media_base64", Base64.getEncoder().encodeToString(command.media())));
            var request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Tino-Internal-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("WhatsApp delivery provider unavailable: " + response.statusCode());
            }
            var root = mapper.readTree(response.body());
            var providerMessageId = root.path("provider_message_id").stringValue();
            if (providerMessageId == null || providerMessageId.isBlank()) {
                throw new IllegalStateException("WhatsApp provider response has no message id");
            }
            return new DeliveryResult(root.path("provider").stringValue(), providerMessageId);
        } catch (IOException exception) {
            throw new IllegalStateException("WhatsApp delivery provider unavailable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WhatsApp delivery was interrupted", exception);
        }
    }
}
