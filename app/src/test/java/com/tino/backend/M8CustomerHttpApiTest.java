package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** HTTP proof for the minimal M8 customer contract and privacy-safe errors. */
@Testcontainers
@SpringBootTest
class M8CustomerHttpApiTest {
    private static final String SUBJECT = "m8-http-owner";
    private static final String OTHER_SUBJECT = "m8-http-other";

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

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
            statement.execute("TRUNCATE TABLE public.business_item_purposes, public.business_operating_modes, public.business_activities, public.purchase_receipt_confirmation_idempotency, public.receiving_events, public.purchase_price_observations, public.purchase_receipt_items, public.purchase_receipts, public.receiving_purchase_preview_idempotency, public.receiving_purchase_preview_items, public.receiving_purchase_previews, public.purchase_document_items, public.purchase_documents, public.external_product_price_options, public.external_product_mappings, public.external_business_connections, public.inventory_movements, public.inventory_balances, public.goods_receipt_items, public.goods_receipts, public.goods_receipt_preview_items, public.goods_receipt_previews, public.packaging_conversions, public.supplier_product_mappings, public.product_identifiers, public.products, public.nfe_retrieval_idempotency_keys, public.nfe_items, public.nfe_document_versions, public.nfe_documents, public.message_delivery_evidence, public.message_outbox, public.messages, public.message_consent_audit, public.message_consents, public.reconciliation_items, public.reconciliation_runs, public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, public.credit_idempotency_keys, "
                    + "public.credit_ledger_entries, public.credit_accounts, public.customer_idempotency_keys, public.customers, "
                    + "public.sync_event_rejections, public.sync_outbox, public.sync_changes, "
                    + "public.sync_event_claims, public.device_installations, public.business_memberships, "
                    + "public.businesses, public.users");
        }
    }

    @Test
    void customerEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/businesses/{businessId}/customers", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReplayUpdateAndListUseMinimalCustomerContract() throws Exception {
        var auth = principal(SUBJECT);
        var businessId = createBusiness(auth);
        var customer = createCustomer(auth, businessId, "request-1", "Maria", "Mari", "55119999");
        var replay = createCustomer(auth, businessId, "request-1", "Maria", "Mari", "55119999");

        assertThat(replay.path("id").asText()).isEqualTo(customer.path("id").asText());
        var customerId = UUID.fromString(customer.path("id").asText());

        mockMvc.perform(put("/api/v1/businesses/{businessId}/customers/{customerId}", businessId, customerId)
                        .with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Maria Silva\",\"nickname\":\"Mari\",\"phone\":\"55118888\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maria Silva"));
        mockMvc.perform(get("/api/v1/businesses/{businessId}/customers", businessId)
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(customerId.toString()))
                .andExpect(jsonPath("$[0].phone").value("55118888"));
    }

    @Test
    void conflictingIdempotencyAndForeignBusinessAreSafe() throws Exception {
        var owner = principal(SUBJECT);
        var foreign = principal(OTHER_SUBJECT);
        var businessId = createBusiness(foreign);

        mockMvc.perform(post("/api/v1/businesses/{businessId}/customers", businessId)
                        .with(authentication(owner)).header("Idempotency-Key", "foreign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Foreign\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("customer access denied"));

        var ownBusiness = createBusiness(owner);
        createCustomer(owner, ownBusiness, "same-key", "Maria", null, null);
        mockMvc.perform(post("/api/v1/businesses/{businessId}/customers", ownBusiness)
                        .with(authentication(owner)).header("Idempotency-Key", "same-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Joao\"}"))
                .andExpect(status().isConflict());
    }

    private UUID createBusiness(Authentication auth) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/businesses").with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"M8 HTTP\",\"vertical\":\"OTHER\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("id").asText());
    }

    private JsonNode createCustomer(Authentication auth, UUID businessId, String key, String name,
            String nickname, String phone) throws Exception {
        var nicknameJson = nickname == null ? "null" : "\"" + nickname + "\"";
        var phoneJson = phone == null ? "null" : "\"" + phone + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/businesses/{businessId}/customers", businessId)
                        .with(authentication(auth)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"nickname\":" + nicknameJson
                                + ",\"phone\":" + phoneJson + "}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isIn(200, 201);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static Authentication principal(String subject) {
        return new AuthenticatedPrincipalAuthenticationToken(
                new AuthenticatedPrincipal(new ExternalSubject(subject)), List.of());
    }
}
