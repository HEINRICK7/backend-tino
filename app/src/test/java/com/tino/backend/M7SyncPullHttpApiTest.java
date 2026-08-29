package com.tino.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** HTTP proof for M7 cursor and change-envelope compatibility. */
@Testcontainers
@SpringBootTest
@Import(M7SyncPullHttpApiTest.HandlerConfiguration.class)
class M7SyncPullHttpApiTest {
    private static final String SUBJECT = "m7-http-owner";
    private static final String INSTALLATION = "m7-http-device";
    private static final String EVENT_ID = "00000000-0000-7000-8000-00000000051a";

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
            statement.execute("TRUNCATE TABLE public.customer_idempotency_keys, public.customers, "
                    + "public.sync_event_rejections, public.sync_outbox, "
                    + "public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
        }
    }

    @Test
    void pullRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/sync/changes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pullReturnsOrderedChangesAndOpaqueNextCursor() throws Exception {
        var auth = principal(SUBJECT);
        var businessId = createBusiness(auth);
        registerInstallation(auth, businessId);
        push(auth, businessId);

        mockMvc.perform(get("/v1/sync/changes")
                        .with(authentication(auth))
                        .queryParam("business_id", businessId.toString())
                        .queryParam("cursor", "0")
                        .queryParam("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[0].event_id").value(EVENT_ID))
                .andExpect(jsonPath("$.changes[0].store_id").value("local-store"))
                .andExpect(jsonPath("$.changes[0].payload.value").value(1))
                .andExpect(jsonPath("$.next_cursor").isNumber());
    }

    private UUID createBusiness(Authentication auth) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/businesses")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"M7 HTTP\",\"vertical\":\"OTHER\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).path("id").asText());
    }

    private void registerInstallation(Authentication auth, UUID businessId) throws Exception {
        mockMvc.perform(post("/api/v1/businesses/{businessId}/installations", businessId)
                        .with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installation_id\":\"" + INSTALLATION + "\"}"))
                .andExpect(status().isCreated());
    }

    private void push(Authentication auth, UUID businessId) throws Exception {
        mockMvc.perform(post("/v1/sync/events").with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"business_id\":\"" + businessId + "\",\"events\":["
                                + "{\"event_id\":\"" + EVENT_ID + "\","
                                + "\"store_id\":\"local-store\",\"device_id\":\"" + INSTALLATION + "\","
                                + "\"aggregate_id\":\"aggregate-1\",\"event_type\":\"known\","
                                + "\"schema_version\":1,\"occurred_at\":\"2026-08-29T12:00:00Z\","
                                + "\"payload\":{\"value\":1}}]}"))
                .andExpect(status().isOk());
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
                    return new SyncEventEffects(event.payloadJson(), event.payloadJson());
                }
            };
        }
    }
}
