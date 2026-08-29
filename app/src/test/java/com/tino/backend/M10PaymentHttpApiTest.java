package com.tino.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.adapter.in.security.AuthenticatedPrincipalAuthenticationToken;
import com.tino.backend.identity.domain.model.ExternalSubject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.sql.DriverManager;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class M10PaymentHttpApiTest {
    private static final String OWNER = "m10-http-owner";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000c01");
    @Container static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();
    @Autowired private WebApplicationContext context;
    @Autowired private tools.jackson.databind.ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> M2PostgresTestContainer.APP);
        registry.add("spring.datasource.password", POSTGRES::appPassword);
        registry.add("spring.flyway.user", () -> M2PostgresTestContainer.MIGRATOR);
        registry.add("spring.flyway.password", POSTGRES::migratorPassword);
        registry.add("tino.payment.sandbox-webhook-secret", () -> "m10-test-secret");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://127.0.0.1:65535/realms/test");
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword()).locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.reconciliation_items, public.reconciliation_runs, public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, "
                    + "public.credit_idempotency_keys, public.credit_ledger_entries, public.credit_accounts, "
                    + "public.customer_idempotency_keys, public.customers, public.sync_event_rejections, "
                    + "public.sync_outbox, public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
        }
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("INSERT INTO public.users (id, external_subject, status, created_at, updated_at) "
                    + "VALUES ('%s', '%s', 'ACTIVE', now(), now())".formatted(USER_ID, OWNER));
        }
    }

    @Test
    void paymentRequiresAuthenticationAndSupportsCreationAndProcessing() throws Exception {
        UUID businessId = createBusiness();
        UUID customerId = createCustomer(businessId);
        var base = "/api/v1/businesses/%s/customers/%s/payments".formatted(businessId, customerId);

        mockMvc.perform(post(base).header("Idempotency-Key", "no-auth")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"1.00\"}"))
                .andExpect(status().isUnauthorized());
        MvcResult created = mockMvc.perform(post(base).with(authentication(principal()))
                        .header("Idempotency-Key", "payment-http")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"7.50\",\"external_reference\":\"order-http\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.payment.status").value("CREATED"))
                .andExpect(jsonPath("$.payment.amount").value(7.50)).andReturn();
        UUID paymentId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .path("payment").path("id").stringValue());
        mockMvc.perform(post("/api/v1/businesses/{businessId}/payments/{paymentId}/process", businessId, paymentId)
                        .with(authentication(principal())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.status").value("AUTHORIZED"));
    }

    @Test
    void forgedWebhookIsRejectedAndSignedWebhookIsAcceptedOnce() throws Exception {
        UUID businessId = createBusiness();
        UUID customerId = createCustomer(businessId);
        MvcResult created = mockMvc.perform(post("/api/v1/businesses/{businessId}/customers/{customerId}/payments",
                        businessId, customerId).with(authentication(principal())).header("Idempotency-Key", "webhook-http")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"8.00\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID paymentId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .path("payment").path("id").stringValue());
        mockMvc.perform(post("/api/v1/businesses/{businessId}/payments/{paymentId}/process", businessId, paymentId)
                        .with(authentication(principal())))
                .andExpect(status().isOk());
        String body = "{\"payment_id\":\"%s\",\"provider_payment_id\":\"sandbox-payment-%s\",\"status\":\"CAPTURED\"}"
                .formatted(paymentId, paymentId);
        var webhook = post("/api/v1/businesses/{businessId}/payment-webhooks/sandbox", businessId)
                .header("X-Provider-Event-Id", "http-event-1").contentType(MediaType.APPLICATION_JSON).content(body);
        mockMvc.perform(webhook.header("X-Provider-Signature", "bad"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/businesses/{businessId}/payment-webhooks/sandbox", businessId)
                        .header("X-Provider-Event-Id", "http-event-1")
                        .header("X-Provider-Signature", hmac(body)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.status").value("CAPTURED"));
        mockMvc.perform(post("/api/v1/businesses/{businessId}/payment-webhooks/sandbox", businessId)
                        .header("X-Provider-Event-Id", "http-event-1")
                        .header("X-Provider-Signature", hmac(body)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.status").value("CAPTURED"));
    }

    private UUID createBusiness() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/businesses").with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"trade_name\":\"M10 HTTP\",\"vertical\":\"OTHER\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("id").stringValue());
    }
    private UUID createCustomer(UUID businessId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/businesses/{businessId}/customers", businessId)
                        .with(authentication(principal())).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"M10 Customer\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("id").stringValue());
    }
    private static AuthenticatedPrincipalAuthenticationToken principal() {
        return new AuthenticatedPrincipalAuthenticationToken(new AuthenticatedPrincipal(new ExternalSubject(OWNER)), List.of());
    }
    private static String hmac(String body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("m10-test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
