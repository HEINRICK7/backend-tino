package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** HTTP proof for M9 exact money, idempotency, compensation, and authorization. */
@Testcontainers
@SpringBootTest
class M9CreditLedgerHttpApiTest {
    private static final String OWNER = "m9-http-owner";
    private static final String OTHER = "m9-http-other";

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @Autowired private WebApplicationContext webApplicationContext;
    private final ObjectMapper objectMapper = new ObjectMapper();
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
    void clearData() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity()).build();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
                var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.external_product_price_options, public.external_product_mappings, public.external_business_connections, public.inventory_movements, public.inventory_balances, public.goods_receipt_items, public.goods_receipts, public.goods_receipt_preview_items, public.goods_receipt_previews, public.packaging_conversions, public.supplier_product_mappings, public.product_identifiers, public.products, public.nfe_retrieval_idempotency_keys, public.nfe_items, public.nfe_document_versions, public.nfe_documents, public.message_delivery_evidence, public.message_outbox, public.messages, public.message_consent_audit, public.message_consents, public.reconciliation_items, public.reconciliation_runs, public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, public.credit_idempotency_keys, "
                    + "public.credit_ledger_entries, public.credit_accounts, public.customer_idempotency_keys, "
                    + "public.customers, public.sync_event_rejections, public.sync_outbox, public.sync_changes, "
                    + "public.sync_event_claims, public.device_installations, public.business_memberships, "
                    + "public.businesses, public.users");
        }
    }

    @Test
    void creditEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/businesses/{businessId}/customers/{customerId}/credit",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void appendReplayBalanceAndCompensationUseThePublicContract() throws Exception {
        var auth = principal(OWNER);
        var businessId = createBusiness(auth);
        var customerId = createCustomer(auth, businessId);
        var base = "/api/v1/businesses/%s/customers/%s/credit".formatted(businessId, customerId);

        var first = mockMvc.perform(post(base + "/entries").with(authentication(auth))
                        .header("Idempotency-Key", "grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"CREDIT\",\"amount\":\"100.00\","
                                + "\"reason\":\"MANUAL_GRANT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(100.00))
                .andExpect(jsonPath("$.replayed").value(false))
                .andReturn();
        var entryId = objectMapper.readTree(first.getResponse().getContentAsString()).path("entry_id").asText();

        mockMvc.perform(post(base + "/entries").with(authentication(auth))
                        .header("Idempotency-Key", "grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"CREDIT\",\"amount\":\"100.00\","
                                + "\"reason\":\"MANUAL_GRANT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry_id").value(entryId))
                .andExpect(jsonPath("$.replayed").value(true));

        mockMvc.perform(get(base).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00))
                .andExpect(jsonPath("$.currency").value("BRL"));

        mockMvc.perform(post(base + "/entries/{entryId}/compensation", entryId)
                        .with(authentication(auth)).header("Idempotency-Key", "compensate"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("DEBIT"))
                .andExpect(jsonPath("$.compensates_entry_id").value(entryId))
                .andExpect(jsonPath("$.balance").value(0.00));
    }

    @Test
    void foreignBusinessAndInvalidPrecisionAreRejectedSafely() throws Exception {
        var owner = principal(OWNER);
        var foreign = principal(OTHER);
        var foreignBusiness = createBusiness(foreign);
        var foreignCustomer = createCustomer(foreign, foreignBusiness);
        var base = "/api/v1/businesses/%s/customers/%s/credit/entries"
                .formatted(foreignBusiness, foreignCustomer);

        mockMvc.perform(post(base).with(authentication(owner)).header("Idempotency-Key", "foreign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"CREDIT\",\"amount\":\"1.00\","
                                + "\"reason\":\"OTHER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("credit access denied"));

        var ownBusiness = createBusiness(owner);
        var ownCustomer = createCustomer(owner, ownBusiness);
        mockMvc.perform(post("/api/v1/businesses/{businessId}/customers/{customerId}/credit/entries",
                        ownBusiness, ownCustomer)
                        .with(authentication(owner)).header("Idempotency-Key", "precision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"CREDIT\",\"amount\":\"1.001\","
                                + "\"reason\":\"OTHER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CREDIT_REQUEST"));
    }

    private UUID createBusiness(Authentication auth) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/businesses").with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"M9 HTTP\",\"vertical\":\"OTHER\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private UUID createCustomer(Authentication auth, UUID businessId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/businesses/{businessId}/customers", businessId)
                        .with(authentication(auth)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M9 Customer\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private static Authentication principal(String subject) {
        return new AuthenticatedPrincipalAuthenticationToken(
                new AuthenticatedPrincipal(new ExternalSubject(subject)), List.of());
    }
}
