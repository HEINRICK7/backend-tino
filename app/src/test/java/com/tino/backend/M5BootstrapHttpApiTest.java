package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tino.backend.identity.adapter.in.security.AuthenticatedPrincipalAuthenticationToken;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.domain.model.ExternalSubject;
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
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** HTTP and composition gates for the read-only M5 Bootstrap Context. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class M5BootstrapHttpApiTest {
    private static final String ISSUER = "https://issuer.example.test/realms/tino";
    private static final String CLIENT_ID = "tino-android";
    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final HttpServer JWKS_SERVER = startJwksServer();

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @LocalServerPort
    private int port;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> M2PostgresTestContainer.APP);
        registry.add("spring.datasource.password", POSTGRES::appPassword);
        registry.add("spring.flyway.user", () -> M2PostgresTestContainer.MIGRATOR);
        registry.add("spring.flyway.password", POSTGRES::migratorPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                M5BootstrapHttpApiTest::jwksUri);
        registry.add("tino.security.oidc.client-id", () -> CLIENT_ID);
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS_SERVER.stop(0);
    }

    @BeforeEach
    void clearData() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword());
                var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.purchase_receipt_confirmation_idempotency, public.receiving_events, public.purchase_price_observations, public.purchase_receipt_items, public.purchase_receipts, public.receiving_purchase_preview_idempotency, public.receiving_purchase_preview_items, public.receiving_purchase_previews, public.purchase_document_items, public.purchase_documents, public.external_product_price_options, public.external_product_mappings, public.external_business_connections, public.inventory_movements, public.inventory_balances, public.goods_receipt_items, public.goods_receipts, public.goods_receipt_preview_items, public.goods_receipt_previews, public.packaging_conversions, public.supplier_product_mappings, public.product_identifiers, public.products, public.nfe_retrieval_idempotency_keys, public.nfe_items, public.nfe_document_versions, public.nfe_documents, public.message_delivery_evidence, public.message_outbox, public.messages, public.message_consent_audit, public.message_consents, public.reconciliation_items, public.reconciliation_runs, public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, public.credit_idempotency_keys, "
                    + "public.credit_ledger_entries, public.credit_accounts, public.customer_idempotency_keys, public.customers, "
                    + "public.sync_event_rejections, public.sync_outbox, "
                    + "public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
        }
    }

    @Test
    void testM5_022_apiRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testM5_023_invalidJwtIsRejectedBeforeBootstrap() throws Exception {
        assertThat(bearerRequest("invalid-" + UUID.randomUUID()).statusCode()).isEqualTo(401);
    }

    @Test
    void testM5_024_expiredJwtIsRejectedBeforeBootstrap() throws Exception {
        assertThat(bearerRequest(token("m5-expired", ISSUER, List.of(CLIENT_ID), null, -60))
                .statusCode()).isEqualTo(401);
    }

    @Test
    void testM5_025_wrongIssuerIsRejectedEvenWithValidSignature() throws Exception {
        assertThat(bearerRequest(token(
                "m5-wrong-issuer", "https://wrong.example.test", List.of(CLIENT_ID), null, 60))
                .statusCode()).isEqualTo(401);
    }

    @Test
    void testM5_026_invalidAudienceAndAuthorizedPartyAreRejected() throws Exception {
        assertThat(bearerRequest(token(
                "m5-wrong-client", ISSUER, List.of("another-client"), "another-client", 60))
                .statusCode()).isEqualTo(401);
    }

    @Test
    void testM5_027_missingSubjectIsRejectedBeforeBootstrap() throws Exception {
        assertThat(bearerRequest(token(null, ISSUER, List.of(CLIENT_ID), null, 60))
                .statusCode()).isEqualTo(401);
    }

    @Test
    void testM5_028_foreignBusinessIsNotDisclosed() throws Exception {
        var businessA = createBusiness("m5-http-a", "Business A");
        var businessB = createBusiness("m5-http-b", "Business B");

        var result = bootstrap("m5-http-a", businessB, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(businessB.toString())
                .doesNotContain("Business B");
        assertThat(businessA).isNotEqualTo(businessB);
    }

    @Test
    void testM5_029_foreignInstallationIsNotDisclosed() throws Exception {
        var businessA = createBusiness("m5-http-install-a", "Installation A");
        var businessB = createBusiness("m5-http-install-b", "Installation B");
        registerInstallation("m5-http-install-b", businessB, "foreign-installation");

        var result = bootstrap("m5-http-install-a", businessA, "foreign-installation");
        var body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body.path("state").asText()).isEqualTo("LOCAL_BUSINESS_LINK_REQUIRED");
        assertThat(body.toString())
                .doesNotContain(businessB.toString())
                .doesNotContain("foreign-installation");
    }

    @Test
    void testM5_030_responseContainsNoUnnecessaryPersonalData() throws Exception {
        var business = createBusiness("m5-http-privacy", "Privacy Business");
        registerInstallation("m5-http-privacy", business, "privacy-installation");

        var body = bootstrapBody("m5-http-privacy", business, "privacy-installation");

        assertThat(body.toString())
                .doesNotContain("externalSubject", "external_subject", "email", "phone")
                .doesNotContain("m5-http-privacy");
    }

    @Test
    void testM5_031_responseContainsNoJwtClaimsOrTokens() throws Exception {
        var business = createBusiness("m5-http-claims", "Claims Business");
        var body = bootstrapBody("m5-http-claims", business, null);

        assertThat(body.toString())
                .doesNotContain("iss", "aud", "azp", "exp", "Authorization", "Bearer");
    }

    @Test
    void testM5_032_businessSummaryUsesApprovedFieldsOnly() throws Exception {
        var business = createBusiness("m5-http-business-summary", "Summary Business");
        var body = bootstrapBody("m5-http-business-summary", business, null);
        var selected = body.path("selected_business");

        assertThat(selected.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "trade_name", "vertical", "status", "role", "data_source_type");
    }

    @Test
    void testM5_033_installationSummaryUsesApprovedFieldsOnly() throws Exception {
        var business = createBusiness("m5-http-install-summary", "Install Summary");
        registerInstallation("m5-http-install-summary", business, "summary-installation");
        var body = bootstrapBody("m5-http-install-summary", business, "summary-installation");
        var installation = body.path("installation");

        assertThat(installation.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "installation_id", "business_id", "status");
    }

    @Test
    void testM5_034_bootstrapDoesNotChangePersistentCounts() throws Exception {
        var business = createBusiness("m5-http-read-only", "Read Only");
        registerInstallation("m5-http-read-only", business, "read-only-installation");
        var before = counts();

        bootstrap("m5-http-read-only", business, "read-only-installation");

        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void testM5_035_repeatedRequestReturnsStableContext() throws Exception {
        var business = createBusiness("m5-http-repeat", "Repeated");
        registerInstallation("m5-http-repeat", business, "repeat-installation");

        var first = bootstrapBody("m5-http-repeat", business, "repeat-installation");
        var second = bootstrapBody("m5-http-repeat", business, "repeat-installation");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void testM5_045_httpBusinessRequiredIsSuccessfulState() throws Exception {
        var body = bootstrapBody("m5-http-no-business", null, null);

        assertThat(body.path("state").asText()).isEqualTo("BUSINESS_REQUIRED");
    }

    @Test
    void testM5_046_httpLocalBusinessLinkRequiredIsSuccessfulState() throws Exception {
        var business = createBusiness("m5-http-local-link", "Local Link");
        var body = bootstrapBody("m5-http-local-link", business, null);

        assertThat(body.path("state").asText()).isEqualTo("LOCAL_BUSINESS_LINK_REQUIRED");
        assertThat(body.path("selected_business").path("id").asText())
                .isEqualTo(business.toString());
    }

    @Test
    void testM5_047_httpReadyRequiresActiveSameBusinessInstallation() throws Exception {
        var business = createBusiness("m5-http-ready", "Ready");
        registerInstallation("m5-http-ready", business, "ready-installation");
        var body = bootstrapBody("m5-http-ready", business, "ready-installation");

        assertThat(body.path("state").asText()).isEqualTo("READY");
        assertThat(body.path("installation").path("business_id").asText())
                .isEqualTo(business.toString());
    }

    @Test
    void testM5_048_businessIdorIsDenied() throws Exception {
        createBusiness("m5-http-idor-a", "IDOR A");
        var businessB = createBusiness("m5-http-idor-b", "IDOR B");

        var result = bootstrap("m5-http-idor-a", businessB, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(businessB.toString());
    }

    @Test
    void testM5_049_installationIdorCannotGrantContext() throws Exception {
        var businessA = createBusiness("m5-http-install-idor-a", "Install IDOR A");
        var businessB = createBusiness("m5-http-install-idor-b", "Install IDOR B");
        registerInstallation("m5-http-install-idor-b", businessB, "idor-installation");

        var body = bootstrapBody("m5-http-install-idor-a", businessA, "idor-installation");

        assertThat(body.path("state").asText()).isEqualTo("LOCAL_BUSINESS_LINK_REQUIRED");
        assertThat(body.path("installation").isMissingNode() || body.path("installation").isNull())
                .isTrue();
    }

    @Test
    void testM5_050_responseContainsNoSyncData() throws Exception {
        var business = createBusiness("m5-http-no-sync", "No Sync");
        var body = bootstrapBody("m5-http-no-sync", business, null);

        assertThat(body.toString()).doesNotContain("sync", "cursor", "pending_events", "changes");
    }

    @Test
    void testM5_051_responseContainsNoBusinessDomainData() throws Exception {
        var business = createBusiness("m5-http-no-domain", "No Domain");
        var body = bootstrapBody("m5-http-no-domain", business, null);

        assertThat(body.toString()).doesNotContain(
                "products", "customers", "stock", "credit", "transactions", "suppliers");
    }

    @Test
    void testM5_052_readOnlyBootstrapCreatesNoSideEffectEvent() throws Exception {
        var business = createBusiness("m5-http-no-event", "No Event");
        var before = counts();

        bootstrap("m5-http-no-event", business, null);

        assertThat(counts()).isEqualTo(before);
    }

    private JsonNode bootstrapBody(String subject, UUID businessId, String installationId)
            throws Exception {
        var result = bootstrap(subject, businessId, installationId);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult bootstrap(String subject, UUID businessId, String installationId)
            throws Exception {
        var request = objectMapper.createObjectNode();
        if (businessId != null) {
            request.put("requested_business_id", businessId.toString());
        }
        if (installationId != null) {
            request.put("installation_external_id", installationId);
        }
        return mockMvc.perform(post("/api/v1/bootstrap")
                        .with(authentication(principal(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andReturn();
    }

    private UUID createBusiness(String subject, String tradeName) throws Exception {
        var result = mockMvc.perform(post("/api/v1/businesses")
                        .with(authentication(principal(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"" + tradeName
                                + "\",\"vertical\":\"OTHER\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).path("id").asText());
    }

    private void registerInstallation(String subject, UUID businessId, String installationId)
            throws Exception {
        mockMvc.perform(post("/api/v1/businesses/{businessId}/installations", businessId)
                        .with(authentication(principal(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installation_id\":\"" + installationId + "\"}"))
                .andExpect(status().isCreated());
    }

    private static Authentication principal(String subject) {
        return new AuthenticatedPrincipalAuthenticationToken(
                new AuthenticatedPrincipal(new ExternalSubject(subject)), List.of());
    }

    private Counts counts() throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword());
                var statement = connection.createStatement()) {
            return new Counts(
                    count(statement, "users"),
                    count(statement, "businesses"),
                    count(statement, "business_memberships"),
                    count(statement, "device_installations"));
        }
    }

    private static long count(java.sql.Statement statement, String table) throws Exception {
        try (var result = statement.executeQuery("SELECT count(*) FROM public." + table)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private HttpResponse<String> bearerRequest(String token) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/bootstrap"))
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
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
                .jwkPostProcessor(builder -> builder.keyID("m5-test-key"))
                .build();
    }

    private static String jwksUri() {
        return "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks";
    }

    private static HttpServer startJwksServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var publicJwk = new RSAKey.Builder((RSAPublicKey) KEY_PAIR.getPublic())
                    .keyID("m5-test-key")
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

    private record Counts(long users, long businesses, long memberships, long installations) {}
}
