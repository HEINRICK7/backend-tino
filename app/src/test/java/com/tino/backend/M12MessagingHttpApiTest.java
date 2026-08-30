package com.tino.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tino.backend.identity.adapter.in.security.AuthenticatedPrincipalAuthenticationToken;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.domain.model.ExternalSubject;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class M12MessagingHttpApiTest {
    private static final String OWNER = "m12-http-owner";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000f21");
    @Container static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();
    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> M2PostgresTestContainer.APP);
        registry.add("spring.datasource.password", POSTGRES::appPassword);
        registry.add("spring.flyway.user", () -> M2PostgresTestContainer.MIGRATOR);
        registry.add("spring.flyway.password", POSTGRES::migratorPassword);
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
            statement.execute("TRUNCATE TABLE public.inventory_movements, public.inventory_balances, public.goods_receipt_items, public.goods_receipts, public.goods_receipt_preview_items, public.goods_receipt_previews, public.packaging_conversions, public.supplier_product_mappings, public.product_identifiers, public.products, public.nfe_retrieval_idempotency_keys, public.nfe_items, public.nfe_document_versions, public.nfe_documents, public.message_delivery_evidence, public.message_outbox, public.messages, "
                    + "public.message_consent_audit, public.message_consents, public.reconciliation_items, "
                    + "public.reconciliation_runs, public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, "
                    + "public.credit_idempotency_keys, public.credit_ledger_entries, public.credit_accounts, "
                    + "public.customer_idempotency_keys, public.customers, public.sync_event_rejections, "
                    + "public.sync_outbox, public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
            statement.execute("INSERT INTO public.users (id, external_subject, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', 'ACTIVE', now(), now())".formatted(USER_ID, OWNER));
        }
    }

    @Test
    void consentQueueProcessReplayAndGetAreExposedWithoutProviderNetwork() throws Exception {
        UUID businessId = createBusiness();
        UUID customerId = createCustomer(businessId);
        String messages = "/api/v1/businesses/%s/customers/%s/messages".formatted(businessId, customerId);
        mockMvc.perform(post(messages).with(authentication(principal())).header("Idempotency-Key", "before-consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"WHATSAPP\",\"purpose\":\"TRANSACTIONAL\",\"template\":\"PAYMENT_UPDATE\"}"))
                .andExpect(status().is(422));
        String consentPath = "/api/v1/businesses/%s/customers/%s/messaging/consent".formatted(businessId, customerId);
        mockMvc.perform(put(consentPath).with(authentication(principal())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"WHATSAPP\",\"purpose\":\"TRANSACTIONAL\",\"granted\":true,\"recipient_ref\":\"5511999999999\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.granted").value(true));
        MvcResult created = mockMvc.perform(post(messages).with(authentication(principal()))
                        .header("Idempotency-Key", "message-http").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"WHATSAPP\",\"purpose\":\"TRANSACTIONAL\",\"template\":\"PAYMENT_UPDATE\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.message.status").value("QUEUED")).andReturn();
        String messageId = new tools.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString())
                .path("message").path("id").stringValue();
        mockMvc.perform(post(messages).with(authentication(principal())).header("Idempotency-Key", "message-http")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"WHATSAPP\",\"purpose\":\"TRANSACTIONAL\",\"template\":\"PAYMENT_UPDATE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        mockMvc.perform(post("/api/v1/businesses/{businessId}/messages/{messageId}/process", businessId, messageId)
                        .with(authentication(principal())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message.status").value("SENT"));
        mockMvc.perform(get("/api/v1/businesses/{businessId}/messages/{messageId}", businessId, messageId)
                        .with(authentication(principal())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"));
    }

    private UUID createBusiness() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/businesses").with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"trade_name\":\"M12 HTTP\",\"vertical\":\"OTHER\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString())
                .path("id").stringValue());
    }
    private UUID createCustomer(UUID businessId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/businesses/{businessId}/customers", businessId)
                        .with(authentication(principal())).header("Idempotency-Key", "customer-http")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"M12 Customer\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString())
                .path("id").stringValue());
    }
    private static AuthenticatedPrincipalAuthenticationToken principal() {
        return new AuthenticatedPrincipalAuthenticationToken(new AuthenticatedPrincipal(new ExternalSubject(OWNER)), List.of());
    }
}
