package com.tino.backend.fiscal.adapter.out.serpro;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class SerproOAuthClient {
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final URI tokenUri;
    private final String consumerKey;
    private final String consumerSecret;
    private final Duration timeout;
    private final Clock clock;
    private Token cached;

    public SerproOAuthClient(HttpClient httpClient, ObjectMapper mapper, URI tokenUri,
            String consumerKey, String consumerSecret, Duration timeout, Clock clock) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
        this.tokenUri = Objects.requireNonNull(tokenUri);
        this.consumerKey = Objects.requireNonNull(consumerKey);
        this.consumerSecret = Objects.requireNonNull(consumerSecret);
        this.timeout = Objects.requireNonNull(timeout);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized String accessToken() {
        if (cached != null && cached.expiresAt().isAfter(clock.instant().plusSeconds(30))) return cached.value();
        if (consumerKey.isBlank() || consumerSecret.isBlank()) {
            throw new SerproAuthenticationException("SERPRO Trial credentials are not configured");
        }
        var basic = Base64.getEncoder().encodeToString((consumerKey + ":" + consumerSecret)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(tokenUri).timeout(timeout)
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SerproAuthenticationException("SERPRO token request rejected");
            }
            var body = mapper.readTree(response.body());
            var value = body == null ? null : body.get("access_token");
            var expires = body == null ? null : body.get("expires_in");
            if (value == null || !value.isString() || expires == null) {
                throw new SerproAuthenticationException("SERPRO token response is invalid");
            }
            cached = new Token(value.stringValue(), clock.instant().plusSeconds(Long.parseLong(expires.toString())));
            return cached.value();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SerproAuthenticationException("SERPRO token request interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof SerproAuthenticationException authentication) throw authentication;
            throw new SerproAuthenticationException("SERPRO token request failed", exception);
        }
    }

    public synchronized void invalidate() {
        cached = null;
    }

    private record Token(String value, Instant expiresAt) {}
}
