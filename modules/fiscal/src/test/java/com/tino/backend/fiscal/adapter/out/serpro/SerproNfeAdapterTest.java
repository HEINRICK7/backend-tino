package com.tino.backend.fiscal.adapter.out.serpro;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tino.backend.fiscal.application.model.NfeRetrievalResult;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import com.tino.backend.fiscal.domain.model.RetrievalStatus;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SerproNfeAdapterTest {
    private static final String KEY = "53160911510448000171550010000106771000187760";
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpServer server;

    @AfterEach
    void stopServer() { if (server != null) server.stop(0); }

    @Test
    void authenticatesWithClientCredentialsAndMapsTrialResponse() throws Exception {
        var tokenRequests = new AtomicInteger();
        var queryRequests = new AtomicInteger();
        var receivedAuthorization = new AtomicReference<String>();
        server = server(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/token")) {
                tokenRequests.incrementAndGet();
                assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                        .isEqualTo("Basic " + Base64.getEncoder().encodeToString("key:secret".getBytes()));
                assertThat(readBody(exchange)).isEqualTo("grant_type=client_credentials");
                respond(exchange, 200, "{\"access_token\":\"trial-token\",\"expires_in\":3600}");
            } else {
                queryRequests.incrementAndGet();
                receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                assertThat(exchange.getRequestHeaders().getFirst("X-Request-Tag")).isEqualTo("tino-nfe");
                assertThat(exchange.getRequestURI().getPath()).isEqualTo("/consulta/nfe/" + KEY);
                respond(exchange, 200, fixture());
            }
        });
        var result = adapter(server, () -> {}).retrieve(new NfeAccessKey(KEY));

        assertThat(result.retrievalStatus()).isEqualTo(RetrievalStatus.SUCCESS);
        assertThat(result.document().items()).hasSize(1);
        assertThat(result.document().number()).isEqualTo("15430");
        assertThat(result.rawPayload().json()).contains("nfeProc");
        assertThat(receivedAuthorization.get()).isEqualTo("Bearer trial-token");
        assertThat(tokenRequests).hasValue(1);
        assertThat(queryRequests).hasValue(1);
    }

    @Test
    void refreshesTokenOnceAfterUnauthorizedResponse() throws Exception {
        var tokenRequests = new AtomicInteger();
        var queryRequests = new AtomicInteger();
        server = server(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/token")) {
                var token = tokenRequests.incrementAndGet() == 1 ? "expired-token" : "fresh-token";
                respond(exchange, 200, "{\"access_token\":\"" + token + "\",\"expires_in\":3600}");
            } else if (queryRequests.incrementAndGet() == 1) {
                respond(exchange, 401, "{}");
            } else {
                respond(exchange, 200, fixture());
            }
        });
        var result = adapter(server, () -> {}).retrieve(new NfeAccessKey(KEY));

        assertThat(result.retrievalStatus()).isEqualTo(RetrievalStatus.SUCCESS);
        assertThat(tokenRequests).hasValue(2);
        assertThat(queryRequests).hasValue(2);
    }

    @Test
    void retriesDocumentedTransientResponsesAtMostOnce() throws Exception {
        var queryRequests = new AtomicInteger();
        server = server(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/token")) respond(exchange, 200, "{\"access_token\":\"token\",\"expires_in\":3600}");
            else if (queryRequests.incrementAndGet() == 1) respond(exchange, 500, "{}");
            else respond(exchange, 200, fixture());
        });
        var result = adapter(server, () -> {}).retrieve(new NfeAccessKey(KEY));

        assertThat(result.retrievalStatus()).isEqualTo(RetrievalStatus.SUCCESS);
        assertThat(queryRequests).hasValue(2);
    }

    @Test
    void classifiesNotFoundAndTransportTimeoutWithoutLeakingPayload() throws Exception {
        server = server(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/token")) respond(exchange, 200, "{\"access_token\":\"token\",\"expires_in\":3600}");
            else respond(exchange, 404, "sensitive response body");
        });
        var result = adapter(server, () -> {}).retrieve(new NfeAccessKey(KEY));

        assertThat(result.retrievalStatus()).isEqualTo(RetrievalStatus.NOT_FOUND);
        assertThat(result.rawPayload()).isNull();
        assertThat(result.failureCode()).isEqualTo("SERPRO_HTTP_404");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 406, 408, 500, 504})
    void classifiesDocumentedNonSuccessCodesAndLimitsRetry(int status) throws Exception {
        var queryRequests = new AtomicInteger();
        server = server(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/token")) respond(exchange, 200, "{\"access_token\":\"token\",\"expires_in\":3600}");
            else { queryRequests.incrementAndGet(); respond(exchange, status, "sensitive response body"); }
        });
        var result = adapter(server, () -> {}).retrieve(new NfeAccessKey(KEY));

        assertThat(result.retrievalStatus()).isEqualTo(RetrievalStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo("SERPRO_HTTP_" + status);
        assertThat(result.rawPayload()).isNull();
        assertThat(queryRequests).hasValue(status == 408 || status == 500 || status == 504 ? 2 : 1);
    }

    @Test
    void classifiesInvalidProviderPayloadWithoutExposingItAsCanonical() throws Exception {
        server = server(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/token")) respond(exchange, 200, "{\"access_token\":\"token\",\"expires_in\":3600}");
            else respond(exchange, 200, "{\"secret\":\"must-not-be-a-canonical-document\"}");
        });
        var result = adapter(server, () -> {}).retrieve(new NfeAccessKey(KEY));

        assertThat(result.retrievalStatus()).isEqualTo(RetrievalStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo("INVALID_PROVIDER_PAYLOAD");
        assertThat(result.document()).isNull();
        assertThat(result.rawPayload().json()).contains("secret");
    }

    @Test
    void classifiesTransportTimeoutAsUnknownOutcome() throws Exception {
        server = server(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/token")) respond(exchange, 200, "{\"access_token\":\"token\",\"expires_in\":3600}");
            else {
                try { Thread.sleep(2_000L); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                respond(exchange, 200, fixture());
            }
        });
        var result = adapter(server, () -> {}, Duration.ofMillis(50)).retrieve(new NfeAccessKey(KEY));

        assertThat(result.retrievalStatus()).isEqualTo(RetrievalStatus.OUTCOME_UNKNOWN);
        assertThat(result.failureCode()).isEqualTo("PROVIDER_TIMEOUT");
    }

    private SerproNfeAdapter adapter(HttpServer httpServer, SerproNfeAdapter.RetryDelayer delayer) {
        return adapter(httpServer, delayer, Duration.ofSeconds(2));
    }

    private SerproNfeAdapter adapter(HttpServer httpServer, SerproNfeAdapter.RetryDelayer delayer,
            Duration timeout) {
        var port = httpServer.getAddress().getPort();
        var mapper = new ObjectMapper();
        var oauth = new SerproOAuthClient(client, mapper, URI.create("http://localhost:" + port + "/token"),
                "key", "secret", timeout, Clock.systemUTC());
        return new SerproNfeAdapter(client, oauth, new SerproNfeParser(mapper),
                URI.create("http://localhost:" + port + "/consulta"), timeout, "tino-nfe", delayer);
    }

    private HttpServer server(java.util.function.Consumer<HttpExchange> handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try { handler.accept(exchange); }
            catch (RuntimeException exception) { respond(exchange, 500, "{}"); throw exception; }
            finally { exchange.close(); }
        });
        server.start();
        return server;
    }

    private static void respond(HttpExchange exchange, int status, String body) {
        try {
            var bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    private static String readBody(HttpExchange exchange) {
        try { return new String(exchange.getRequestBody().readAllBytes()); }
        catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    private static String fixture() {
        try (var stream = SerproNfeAdapterTest.class.getResourceAsStream("/serpro/consulta-nfe-trial-official-sanitized.json")) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }
}
