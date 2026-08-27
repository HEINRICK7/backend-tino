package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.usecase.ResolveAuthenticatedUser;
import com.tino.backend.identity.domain.model.User;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(M2SecurityBoundaryTest.ProbeConfiguration.class)
class M2SecurityBoundaryTest {
    private static final String ISSUER = "https://issuer.example.test/realms/tino";
    private static final String CLIENT_ID = "tino-android";
    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final HttpServer JWKS_SERVER = startJwksServer();

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @LocalServerPort
    int port;

    @Autowired
    AtomicInteger identityResolutions;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> M2PostgresTestContainer.APP);
        registry.add("spring.datasource.password", POSTGRES::appPassword);
        registry.add("spring.flyway.user", () -> M2PostgresTestContainer.MIGRATOR);
        registry.add("spring.flyway.password", POSTGRES::migratorPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", M2SecurityBoundaryTest::jwksUri);
        registry.add("tino.security.oidc.client-id", () -> CLIENT_ID);
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS_SERVER.stop(0);
    }

    @BeforeEach
    void resetResolutionProbe() {
        identityResolutions.set(0);
    }

    @Test
    void validSignedJwtWithSubjectAndAudienceReachesProtectedBoundary() throws Exception {
        var response = request(token("subject-valid", ISSUER, List.of(CLIENT_ID), null, 60));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("subject-valid");
        assertThat(identityResolutions).hasValue(1);
    }

    @Test
    void invalidTokenIs401AndDoesNotReachIdentityUseCase() throws Exception {
        var response = request("invalid-" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(identityResolutions).hasValue(0);
    }

    @Test
    void expiredTokenIs401AndDoesNotReachIdentityUseCase() throws Exception {
        var response = request(token("subject-expired", ISSUER, List.of(CLIENT_ID), null, -60));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(identityResolutions).hasValue(0);
    }

    @Test
    void wrongIssuerIs401EvenWhenSignatureIsValid() throws Exception {
        var response = request(token(
                "subject-wrong-issuer", "https://wrong.example.test", List.of(CLIENT_ID), null, 60));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(identityResolutions).hasValue(0);
    }

    @Test
    void wrongAudienceAndAuthorizedPartyAre401EvenWhenSignatureIsValid() throws Exception {
        var response = request(token(
                "subject-wrong-client", ISSUER, List.of("another-client"), "another-client", 60));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(identityResolutions).hasValue(0);
    }

    @Test
    void signedJwtWithoutSubjectIs401AndDoesNotReachIdentityUseCase() throws Exception {
        var response = request(token(null, ISSUER, List.of(CLIENT_ID), null, 60));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(identityResolutions).hasValue(0);
    }

    @Test
    void protectedRouteIsDeniedWithoutBearerToken() throws Exception {
        var response = requestWithoutToken("/m2/technical-protected");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(identityResolutions).hasValue(0);
    }

    @Test
    void authorizedPartyMaySatisfyExplicitClientContract() throws Exception {
        var response = request(token("subject-azp", ISSUER, List.of("resource-api"), CLIENT_ID, 60));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("subject-azp");
        assertThat(identityResolutions).hasValue(1);
    }

    private HttpResponse<String> request(String token) throws Exception {
        return requestWithoutToken("/m2/technical-protected", token);
    }

    private HttpResponse<String> requestWithoutToken(String path) throws Exception {
        return requestWithoutToken(path, null);
    }

    private HttpResponse<String> requestWithoutToken(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", MediaType.TEXT_PLAIN_VALUE)
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(
                builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String token(
            String subject,
            String issuer,
            List<String> audience,
            String authorizedParty,
            long expiryOffsetSeconds) {
        var now = Instant.now();
        var builder = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(audience)
                .issuedAt(expiryOffsetSeconds < 0 ? now.minusSeconds(120) : now.minusSeconds(1))
                .expiresAt(now.plusSeconds(expiryOffsetSeconds));
        if (subject != null) {
            builder.subject(subject);
        }
        if (authorizedParty != null) {
            builder.claim("azp", authorizedParty);
        }
        return encoder().encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
    }

    private static JwtEncoder encoder() {
        return NimbusJwtEncoder.withKeyPair(
                        (RSAPublicKey) KEY_PAIR.getPublic(), (RSAPrivateKey) KEY_PAIR.getPrivate())
                .jwkPostProcessor(builder -> builder.keyID("m2-test-key"))
                .build();
    }

    private static String jwksUri() {
        return "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks";
    }

    private static HttpServer startJwksServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var publicJwk = new RSAKey.Builder((RSAPublicKey) KEY_PAIR.getPublic())
                    .keyID("m2-test-key")
                    .build()
                    .toPublicJWK()
                    .toJSONString();
            var body = ("{\"keys\":[" + publicJwk + "]}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            server.createContext("/jwks", exchange -> write(exchange, body));
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("could not start test JWKS server", exception);
        }
    }

    private static void write(HttpExchange exchange, byte[] body) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("could not create test signing key", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        AtomicInteger identityResolutions() {
            return new AtomicInteger();
        }

        @Bean
        ProtectedIdentityProbe protectedIdentityProbe(
                ResolveAuthenticatedUser resolveAuthenticatedUser,
                AtomicInteger identityResolutions) {
            return new ProtectedIdentityProbe(resolveAuthenticatedUser, identityResolutions);
        }
    }

    @RestController
    static final class ProtectedIdentityProbe {
        private final ResolveAuthenticatedUser resolveAuthenticatedUser;
        private final AtomicInteger identityResolutions;

        private ProtectedIdentityProbe(
                ResolveAuthenticatedUser resolveAuthenticatedUser,
                AtomicInteger identityResolutions) {
            this.resolveAuthenticatedUser = resolveAuthenticatedUser;
            this.identityResolutions = identityResolutions;
        }

        @GetMapping("/m2/technical-protected")
        String resolve(Authentication authentication) {
            var principal = (AuthenticatedPrincipal) authentication.getPrincipal();
            User user = resolveAuthenticatedUser.execute(principal);
            identityResolutions.incrementAndGet();
            return user.externalSubject().value();
        }
    }
}
