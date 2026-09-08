package com.tino.backend.messaging.adapter.out.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tino.backend.messaging.application.port.out.WhatsAppDeliveryPort.WhatsAppDeliveryCommand;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WaEvolutionWhatsAppDeliveryAdapterTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void forwardsAuthenticatedMediaPayloadToPrivateGoContract() throws Exception {
        var authorization = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        server = server(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("X-Tino-Internal-Token"));
            try {
                body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            respond(exchange, 202, "{\"status\":\"ACCEPTED\",\"provider\":\"WA_EVOLUTION\",\"provider_message_id\":\"evo-1\"}");
        });
        var adapter = adapter("secret");

        var result = adapter.send(command());

        assertThat(result.provider()).isEqualTo("WA_EVOLUTION");
        assertThat(result.providerMessageId()).isEqualTo("evo-1");
        assertThat(authorization).hasValue("secret");
        assertThat(body).hasValueSatisfying(payload -> {
            assertThat(payload).contains("\"message_id\":\"00000000-0000-7000-8000-000000000001\"");
            assertThat(payload).contains("\"mime_type\":\"image/png\"");
            assertThat(payload).contains(Base64.getEncoder().encodeToString(command().media()));
        });
    }

    @Test
    void mapsProviderFailureToAnExceptionWithoutReturningFakeSuccess() throws Exception {
        server = server(exchange -> respond(exchange, 503, "provider failure details"));

        assertThatThrownBy(() -> adapter("secret").send(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("503");
    }

    private WaEvolutionWhatsAppDeliveryAdapter adapter(String token) {
        return new WaEvolutionWhatsAppDeliveryAdapter(URI.create("http://localhost:" + server.getAddress().getPort() + "/internal/v1/messages/whatsapp"),
                token, Duration.ofSeconds(2), HttpClient.newHttpClient(), new ObjectMapper());
    }

    private static WhatsAppDeliveryCommand command() {
        return new WhatsAppDeliveryCommand(java.util.UUID.fromString("00000000-0000-7000-8000-000000000001"),
                "send-1", "+5586995922924", "Olá, Gerlane.", "image/png", "card.png",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 'i', 'm', 'a', 'g', 'e'});
    }

    private HttpServer server(java.util.function.Consumer<HttpExchange> handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try { handler.accept(exchange); }
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
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
