package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tino.backend.identity.adapter.in.security.AuthenticatedPrincipalAuthenticationToken;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.domain.model.ExternalSubject;
import com.tino.backend.sync.application.port.in.SyncEventHandler;
import com.tino.backend.sync.domain.model.SyncEvent;
import com.tino.backend.sync.domain.model.SyncEventEffects;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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

/** HTTP proof for Android-compatible Sync Push request and response JSON. */
@Testcontainers
@SpringBootTest
@Import(M6SyncPushHttpApiTest.HandlerConfiguration.class)
class M6SyncPushHttpApiTest {
    private static final String SUBJECT = "m6-http-owner";
    private static final String INSTALLATION = "m6-http-device";
    private static final String EVENT_ID = "00000000-0000-7000-8000-00000000021a";

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
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword());
                var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.business_item_purposes, public.business_operating_modes, public.business_activities, public.purchase_receipt_confirmation_idempotency, public.receiving_events, public.purchase_price_observations, public.purchase_receipt_items, public.purchase_receipts, public.receiving_purchase_preview_idempotency, public.receiving_purchase_preview_items, public.receiving_purchase_previews, public.purchase_document_items, public.purchase_documents, public.external_product_price_options, public.external_product_mappings, public.external_business_connections, public.inventory_movements, public.inventory_balances, public.goods_receipt_items, public.goods_receipts, public.goods_receipt_preview_items, public.goods_receipt_previews, public.packaging_conversions, public.supplier_product_mappings, public.product_identifiers, public.products, public.nfe_retrieval_idempotency_keys, public.nfe_items, public.nfe_document_versions, public.nfe_documents, public.message_delivery_evidence, public.message_outbox, public.messages, public.message_consent_audit, public.message_consents, public.reconciliation_items, public.reconciliation_runs, public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, public.credit_idempotency_keys, "
                    + "public.credit_ledger_entries, public.credit_accounts, public.customer_idempotency_keys, public.customers, "
                    + "public.sync_event_rejections, public.sync_outbox, "
                    + "public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
        }
    }

    @Test
    void pushRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/v1/sync/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pushAcceptsAndroidEnvelopeAndReturnsCompatibleResult() throws Exception {
        var auth = principal(SUBJECT);
        var businessId = createBusiness(auth);
        registerInstallation(auth, businessId);

        var result = mockMvc.perform(post("/v1/sync/events")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"business_id\":\"" + businessId + "\",\"events\":["
                                + "{\"event_id\":\"" + EVENT_ID + "\","
                                + "\"store_id\":\"local-store\",\"device_id\":\"" + INSTALLATION + "\","
                                + "\"aggregate_id\":\"aggregate-1\",\"event_type\":\"known\","
                                + "\"schema_version\":1,\"occurred_at\":\"2026-08-29T12:00:00Z\","
                                + "\"payload\":{\"value\":1}}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledged_event_ids[0]").value(EVENT_ID))
                .andExpect(jsonPath("$.already_processed_event_ids").isEmpty())
                .andExpect(jsonPath("$.rejected").isEmpty())
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("acknowledged_event_ids")).hasSize(1);
    }

    private UUID createBusiness(Authentication auth) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/businesses")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"M6 HTTP\",\"vertical\":\"OTHER\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).path("id").asText());
    }

    private void registerInstallation(Authentication auth, UUID businessId) throws Exception {
        mockMvc.perform(post("/api/v1/businesses/{businessId}/installations", businessId)
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installation_id\":\"" + INSTALLATION + "\"}"))
                .andExpect(status().isCreated());
    }

    private static Authentication principal(String subject) {
        return new AuthenticatedPrincipalAuthenticationToken(
                new AuthenticatedPrincipal(new ExternalSubject(subject)), List.of());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HandlerConfiguration {
        @Bean
        SyncEventHandler knownSyncEventHandler() {
            return new SyncEventHandler() {
                @Override public String eventType() { return "known"; }
                @Override public int schemaVersion() { return 1; }
                @Override public SyncEventEffects handle(SyncEvent event) {
                    return new SyncEventEffects("{\"event_id\":\""
                            + event.eventId() + "\"}", "{\"event_id\":\""
                            + event.eventId() + "\"}");
                }
            };
        }
    }
}
