package com.tino.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
class M28WhatsAppRendererHttpApiTest {
    private static final String OWNER = "m28-http-owner";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000f28");
    @Container static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();
    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> M2PostgresTestContainer.APP);
        registry.add("spring.datasource.password", POSTGRES::appPassword);
        registry.add("spring.flyway.user", () -> M2PostgresTestContainer.MIGRATOR);
        registry.add("spring.flyway.password", POSTGRES::migratorPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "http://127.0.0.1:65535/realms/test");
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword()).locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("INSERT INTO public.users (id, external_subject, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', 'ACTIVE', now(), now()) ON CONFLICT DO NOTHING".formatted(USER_ID, OWNER));
        }
    }

    @Test
    void previewUsesAuthoritativeCreditAndSendIsIdempotent() throws Exception {
        var business = createBusiness();
        var customer = createCustomer(business);
        var credit = mockMvc.perform(post("/api/v1/businesses/{businessId}/customers/{customerId}/credit/entries", business, customer)
                        .with(authentication(principal())).header("Idempotency-Key", "m28-credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"CREDIT\",\"amount\":693.50,\"reason\":\"PURCHASE\"}"))
                .andExpect(status().isCreated()).andReturn();
        var account = UUID.fromString(mapper.readTree(credit.getResponse().getContentAsString()).path("account_id").stringValue());

        var preview = mockMvc.perform(post("/api/v1/debt-accounts/{accountId}/whatsapp-preview", account)
                        .with(authentication(principal())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageType\":\"DEBT_STATEMENT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("R$ 693,50")))
                .andExpect(jsonPath("$.media.url").exists()).andReturn();
        var previewId = UUID.fromString(mapper.readTree(preview.getResponse().getContentAsString()).path("preview_id").stringValue());

        mockMvc.perform(get("/api/v1/whatsapp-previews/{previewId}/media", previewId).with(authentication(principal())))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));

        var sent = mockMvc.perform(post("/api/v1/debt-accounts/{accountId}/whatsapp-send", account)
                        .with(authentication(principal())).header("Idempotency-Key", "m28-send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previewId\":\"" + previewId + "\",\"messageType\":\"DEBT_STATEMENT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message.status").value("QUEUED")).andReturn();
        var messageId = UUID.fromString(mapper.readTree(sent.getResponse().getContentAsString()).path("message").path("message_id").stringValue());

        mockMvc.perform(post("/api/v1/debt-accounts/{accountId}/whatsapp-send", account)
                        .with(authentication(principal())).header("Idempotency-Key", "m28-send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previewId\":\"" + previewId + "\",\"messageType\":\"DEBT_STATEMENT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        mockMvc.perform(post("/api/v1/businesses/{businessId}/whatsapp-messages/{messageId}/process", business, messageId)
                        .with(authentication(principal())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"));
        mockMvc.perform(get("/api/v1/whatsapp-messages/{messageId}", messageId).with(authentication(principal())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void sendRejectsPreviewWhenCreditVersionChanges() throws Exception {
        var business = createBusiness();
        var customer = createCustomer(business);
        var creditPath = "/api/v1/businesses/%s/customers/%s/credit/entries".formatted(business, customer);
        var credit = mockMvc.perform(post(creditPath).with(authentication(principal())).header("Idempotency-Key", "m28-stale-credit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"CREDIT\",\"amount\":100.00,\"reason\":\"PURCHASE\"}"))
                .andExpect(status().isCreated()).andReturn();
        var account = UUID.fromString(mapper.readTree(credit.getResponse().getContentAsString()).path("account_id").stringValue());
        var preview = mockMvc.perform(post("/api/v1/debt-accounts/{accountId}/whatsapp-preview", account)
                        .with(authentication(principal())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageType\":\"DEBT_STATEMENT\"}"))
                .andExpect(status().isOk()).andReturn();
        var previewId = mapper.readTree(preview.getResponse().getContentAsString()).path("preview_id").stringValue();
        mockMvc.perform(post(creditPath).with(authentication(principal())).header("Idempotency-Key", "m28-stale-credit-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"CREDIT\",\"amount\":10.00,\"reason\":\"PURCHASE\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/debt-accounts/{accountId}/whatsapp-send", account)
                        .with(authentication(principal())).header("Idempotency-Key", "m28-stale-send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previewId\":\"" + previewId + "\",\"messageType\":\"DEBT_STATEMENT\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WHATSAPP_PREVIEW_STALE"));
    }

    private UUID createBusiness() throws Exception {
        var result = mockMvc.perform(post("/api/v1/businesses").with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"trade_name\":\"M28 HTTP\",\"vertical\":\"OTHER\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").stringValue());
    }

    private UUID createCustomer(UUID businessId) throws Exception {
        var result = mockMvc.perform(post("/api/v1/businesses/{businessId}/customers", businessId)
                        .with(authentication(principal())).header("Idempotency-Key", "m28-customer")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Gerlane de Araújo\",\"phone\":\"86999999999\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").stringValue());
    }

    private static AuthenticatedPrincipalAuthenticationToken principal() {
        return new AuthenticatedPrincipalAuthenticationToken(new AuthenticatedPrincipal(new ExternalSubject(OWNER)), List.of());
    }
}
