package com.tino.backend.fiscal.adapter.out.serpro;

import com.tino.backend.fiscal.application.model.NfeRetrievalResult;
import com.tino.backend.fiscal.application.port.out.NfeParser;
import com.tino.backend.fiscal.application.port.out.NfeRetrievalPort;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import com.tino.backend.fiscal.domain.model.RawNfePayload;
import com.tino.backend.fiscal.domain.model.RetrievalStatus;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;

/** SERPRO-only adapter. It does not know catalog, receiving, inventory or persistence. */
public final class SerproNfeAdapter implements NfeRetrievalPort {
    private final HttpClient httpClient;
    private final SerproOAuthClient oauth;
    private final NfeParser parser;
    private final URI baseUri;
    private final Duration timeout;
    private final String requestTag;
    private final RetryDelayer retryDelayer;
    private final NfeMetrics metrics;

    public SerproNfeAdapter(HttpClient httpClient, SerproOAuthClient oauth, NfeParser parser,
            URI baseUri, Duration timeout, String requestTag, RetryDelayer retryDelayer) {
        this(httpClient, oauth, parser, baseUri, timeout, requestTag, retryDelayer, NfeMetrics.noop());
    }

    public SerproNfeAdapter(HttpClient httpClient, SerproOAuthClient oauth, NfeParser parser,
            URI baseUri, Duration timeout, String requestTag, RetryDelayer retryDelayer, NfeMetrics metrics) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.oauth = Objects.requireNonNull(oauth);
        this.parser = Objects.requireNonNull(parser);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.requestTag = requestTag == null ? "" : requestTag;
        if (this.requestTag.length() > 32) throw new IllegalArgumentException("SERPRO request tag must be at most 32 characters");
        this.retryDelayer = Objects.requireNonNull(retryDelayer);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public NfeRetrievalResult retrieve(NfeAccessKey accessKey) {
        var sample = metrics.start();
        try {
            String token;
            try {
                token = oauth.accessToken();
            } catch (SerproAuthenticationException exception) {
                metrics.providerError();
                return NfeRetrievalResult.failure(RetrievalStatus.FAILED, "AUTHENTICATION_FAILED", null);
            }

            var transientRetryUsed = false;
            var tokenRefreshUsed = false;
            while (true) {
                try {
                metrics.callStarted();
                var response = send(accessKey, token);
                if (response.statusCode() == 401 && !tokenRefreshUsed) {
                    oauth.invalidate();
                    token = oauth.accessToken();
                    tokenRefreshUsed = true;
                    continue;
                }
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    metrics.success();
                    var raw = new RawNfePayload(response.body(), "serpro", "consulta-nfe");
                    try {
                        return NfeRetrievalResult.success(raw, parser.parse(response.body(), accessKey));
                    } catch (RuntimeException exception) {
                        return NfeRetrievalResult.failure(RetrievalStatus.FAILED, "INVALID_PROVIDER_PAYLOAD", raw);
                    }
                }
                if (isRetryable(response.statusCode()) && !transientRetryUsed) {
                    transientRetryUsed = true;
                    retryDelayer.delay();
                    continue;
                }
                return NfeRetrievalResult.failure(
                        response.statusCode() == 404 ? RetrievalStatus.NOT_FOUND : RetrievalStatus.FAILED,
                        "SERPRO_HTTP_" + response.statusCode(), null);
            } catch (HttpTimeoutException exception) {
                metrics.providerError();
                return NfeRetrievalResult.failure(RetrievalStatus.OUTCOME_UNKNOWN, "PROVIDER_TIMEOUT", null);
            } catch (InterruptedException exception) {
                metrics.providerError();
                Thread.currentThread().interrupt();
                return NfeRetrievalResult.failure(RetrievalStatus.OUTCOME_UNKNOWN, "PROVIDER_INTERRUPTED", null);
            } catch (IOException exception) {
                metrics.providerError();
                return NfeRetrievalResult.failure(RetrievalStatus.OUTCOME_UNKNOWN, "PROVIDER_IO_UNKNOWN", null);
            }
            }
        } finally {
            metrics.stop(sample);
        }
    }

    private HttpResponse<String> send(NfeAccessKey key, String token) throws IOException, InterruptedException {
        var uri = URI.create(baseUri.toString().replaceAll("/$", "") + "/nfe/" + key.value());
        var requestBuilder = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token);
        if (!requestTag.isBlank()) requestBuilder.header("X-Request-Tag", requestTag);
        return httpClient.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static boolean isRetryable(int status) {
        return status == 408 || status == 500 || status == 504;
    }

    @FunctionalInterface
    public interface RetryDelayer {
        void delay();

        static RetryDelayer production() {
            return () -> {
                try { Thread.sleep(50L + java.util.concurrent.ThreadLocalRandom.current().nextLong(100L)); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            };
        }
    }
}
