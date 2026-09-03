package com.tino.backend.identity.adapter.out.delivery;

import com.tino.backend.identity.application.port.out.OtpDeliveryPort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Provider adapter for the private Go delivery service backed by wa-evolution.
 * It knows only the internal normalized HTTP contract, never OTP authority.
 */
public final class WaEvolutionOtpDeliveryAdapter implements OtpDeliveryPort {
    private static final Pattern PROVIDER_MESSAGE_ID =
            Pattern.compile("\\\"provider_message_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final HttpClient http;
    private final URI endpoint;
    private final String internalToken;
    private final Duration timeout;

    public WaEvolutionOtpDeliveryAdapter(
            URI endpoint, String internalToken, Duration timeout, HttpClient http) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.internalToken = internalToken == null ? "" : internalToken;
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public OtpDeliveryResult deliver(OtpDeliveryRequest request) {
        if (internalToken.isBlank()) {
            return new OtpDeliveryResult(Status.PERMANENT_FAILURE, Channel.WHATSAPP, null);
        }
        var body = "{\"recipient\":\"" + request.recipient().e164()
                + "\",\"template\":\"" + request.template()
                + "\",\"code\":\"" + request.code()
                + "\",\"expires_minutes\":" + request.expiresMinutes()
                + ",\"correlation_id\":\"" + request.correlationId() + "\"}";
        for (var attempt = 0; attempt < 2; attempt++) {
            var result = send(body);
            if (result.status() != Status.RETRYABLE_FAILURE || attempt == 1) {
                return result;
            }
            pauseWithJitter();
        }
        return new OtpDeliveryResult(Status.RETRYABLE_FAILURE, Channel.WHATSAPP, null);
    }

    private OtpDeliveryResult send(String body) {
        try {
            var request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Tino-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var status = response.statusCode();
            if (status >= 200 && status < 300) {
                var matcher = PROVIDER_MESSAGE_ID.matcher(response.body());
                if (!matcher.find()) {
                    return new OtpDeliveryResult(Status.RETRYABLE_FAILURE, Channel.WHATSAPP, null);
                }
                return new OtpDeliveryResult(Status.ACCEPTED, Channel.WHATSAPP, matcher.group(1));
            }
            return new OtpDeliveryResult(isRetryable(status)
                    ? Status.RETRYABLE_FAILURE
                    : Status.PERMANENT_FAILURE, Channel.WHATSAPP, null);
        } catch (IOException exception) {
            return new OtpDeliveryResult(Status.RETRYABLE_FAILURE, Channel.WHATSAPP, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new OtpDeliveryResult(Status.RETRYABLE_FAILURE, Channel.WHATSAPP, null);
        }
    }

    private static boolean isRetryable(int status) {
        return status == 408 || status == 429 || status == 500 || status == 502
                || status == 503 || status == 504;
    }

    private static void pauseWithJitter() {
        try {
            Thread.sleep(100L + java.util.concurrent.ThreadLocalRandom.current().nextLong(150L));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
