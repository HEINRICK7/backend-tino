package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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

/** HTTP boundary gates for authenticated Device installation registration. */
@Testcontainers
@SpringBootTest
class M4DeviceHttpApiTest {
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
                .apply(springSecurity())
                .build();
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword());
                var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, public.credit_idempotency_keys, "
                    + "public.credit_ledger_entries, public.credit_accounts, public.customer_idempotency_keys, public.customers, "
                    + "public.sync_event_rejections, public.sync_outbox, "
                    + "public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
        }
    }

    @Test
    void testM4_029_registrationRequiresAuthentication() throws Exception {
        var businessId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/businesses/{businessId}/installations", businessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installation_id\":\"m4-http-unauthenticated\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testM4_030_crossBusinessRegistrationIsDeniedWithoutDisclosure() throws Exception {
        var businessA = createBusiness("m4-http-owner-a", "HTTP Business A");
        var businessB = createBusiness("m4-http-owner-b", "HTTP Business B");

        var result = mockMvc.perform(post(
                        "/api/v1/businesses/{businessId}/installations", businessB)
                        .with(authentication(principal("m4-http-owner-a")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installation_id\":\"m4-http-cross\"}"))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(businessB.toString())
                .doesNotContain("HTTP Business B");
        assertThat(businessA).isNotEqualTo(businessB);
    }

    @Test
    void testM4_031_registrationIsIdempotentOverHttp() throws Exception {
        var businessId = createBusiness("m4-http-idempotent", "HTTP Idempotent");
        var auth = principal("m4-http-idempotent");

        var first = register(auth, businessId, "m4-http-same");
        var second = register(auth, businessId, "m4-http-same");

        assertThat(first.path("id").asText()).isEqualTo(second.path("id").asText());
        assertThat(first.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(adminCount("device_installations")).isEqualTo(1L);
    }

    @Test
    void testM4_authenticatedRegistrationDerivesRegisteredUserAndBusinessFromContext() throws Exception {
        var owner = principal("m4-http-derived-owner");
        var businessId = createBusiness("m4-http-derived-owner", "HTTP Derived");

        mockMvc.perform(post("/api/v1/businesses/{businessId}/installations", businessId)
                        .with(authentication(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installation_id\":\"m4-http-derived\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.business_id").value(businessId.toString()))
                .andExpect(jsonPath("$.installation_id").value("m4-http-derived"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(adminValue("select count(*) from public.device_installations "
                + "where business_id = '" + businessId + "' and registered_by_user_id in "
                + "(select id from public.users where external_subject = 'm4-http-derived-owner')",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void testM4_clientCannotSupplyStoreOrUserAuthority() throws Exception {
        var businessId = createBusiness("m4-http-client-fields", "HTTP Client Fields");

        mockMvc.perform(post("/api/v1/businesses/{businessId}/installations", businessId)
                        .with(authentication(principal("m4-http-client-fields")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installation_id\":\"m4-http-invalid-fields\","
                                + "\"store_id\":\"client-store\","
                                + "\"registered_by_user_id\":\"00000000-0000-7000-8000-000000000001\"}"))
                .andExpect(status().isBadRequest());
        assertThat(adminCount("device_installations")).isZero();
    }

    private JsonNode register(Authentication auth, UUID businessId, String installationId)
            throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/businesses/{businessId}/installations", businessId)
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installation_id\":\"" + installationId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UUID createBusiness(String subject, String tradeName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/businesses")
                        .with(authentication(principal(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"" + tradeName
                                + "\",\"vertical\":\"OTHER\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).path("id").asText());
    }

    private static Authentication principal(String subject) {
        return new AuthenticatedPrincipalAuthenticationToken(
                new AuthenticatedPrincipal(new ExternalSubject(subject)), List.of());
    }

    private static long adminCount(String table) throws Exception {
        return adminValue("select count(*) from public." + table, Long.class);
    }

    private static <T> T adminValue(String sql, Class<T> type) throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getObject(1, type);
        }
    }
}
