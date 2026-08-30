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

/** HTTP contract and exactly-once proof for the Android NF-e receiving flow. */
@Testcontainers
@SpringBootTest
class NfeAndroidContractHttpApiTest {
    private static final String ACCESS_KEY = "53160911510448000171550010000106771000187760";
    private static final String OWNER = "nfe-android-contract-owner";
    private static final String OTHER = "nfe-android-contract-other";

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @Autowired private WebApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
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
        registry.add("tino.fiscal.mode", () -> "fixture");
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
                var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.inventory_movements, public.inventory_balances, "
                    + "public.goods_receipt_items, public.goods_receipts, public.goods_receipt_preview_items, "
                    + "public.goods_receipt_previews, public.packaging_conversions, public.supplier_product_mappings, "
                    + "public.product_identifiers, public.products, public.nfe_retrieval_idempotency_keys, "
                    + "public.nfe_items, public.nfe_document_versions, public.nfe_documents, "
                    + "public.sync_event_rejections, public.sync_outbox, public.sync_changes, public.sync_event_claims, "
                    + "public.device_installations, public.customers, public.business_memberships, public.businesses, public.users CASCADE");
        }
    }

    @Test
    void openApiUsesRuntimeSnakeCaseStableEnumsAndAllMobileRoutes() throws Exception {
        var root = mapper.readTree(mockMvc.perform(get("/openapi"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(root.path("paths").has("/api/v1/businesses/{businessId}/products")).isTrue();
        assertThat(root.path("paths").has("/api/v1/businesses/{businessId}/goods-receipts/{receiptId}")).isTrue();
        var nfeRequest = root.path("components").path("schemas").path("NfeRequest");
        assertThat(nfeRequest.path("required").toString()).contains("access_key");
        assertThat(nfeRequest.path("properties").has("access_key")).isTrue();
        var nfeResponse = root.path("components").path("schemas").path("NfeResponse");
        assertThat(nfeResponse.path("properties").has("retrieval_status")).isTrue();
        var retrievalSchema = nfeResponse.path("properties").path("retrieval_status");
        assertThat(retrievalSchema.path("enum").toString()).contains("SUCCESS", "OUTCOME_UNKNOWN");
        var schemas = root.path("components").path("schemas");
        assertThat(schemas.has("ProductResolutionStatus")).isTrue();
        assertThat(schemas.has("DecisionAction")).isTrue();
        var preview = schemas.path("PreviewResponse");
        assertThat(preview.path("properties").path("status").path("enum").toString())
                .contains("REVIEW_REQUIRED", "CONFIRMED");
        var receipt = schemas.path("ReceiptResponse");
        assertThat(receipt.path("properties").path("status").path("enum").toString())
                .contains("CONFIRMED", "CANCELLED");
        var confirm = root.path("components").path("schemas").path("ConfirmRequest");
        assertThat(confirm.path("properties").has("preview_version")).isTrue();
        var retrieve = root.path("paths").path("/api/v1/businesses/{businessId}/nfe-documents").path("post");
        assertThat(retrieve.path("parameters").toString()).contains("Idempotency-Key");
        assertThat(retrieve.path("parameters").toString()).contains("\"required\":true");
    }

    @Test
    void retrievalPreviewSearchConfirmationReplayAndResultAreMobileSafe() throws Exception {
        var auth = principal(OWNER);
        var businessId = createBusiness(auth);
        var first = retrieve(auth, businessId, "retrieve-1");
        var documentId = first.path("document_id").asText();
        var previewId = first.path("preview").path("preview_id").asText();
        assertThat(first.path("retrieval_status").asText()).isEqualTo("SUCCESS");
        assertThat(first.path("fiscal_status").asText()).isEqualTo("AUTHORIZED");
        assertThat(first.path("preview").path("summary").path("new_candidate_items").asInt()).isEqualTo(1);
        assertThat(first.toString()).doesNotContain("nfeProc", "canonical_payload", "parser_version");
        assertThat(first.path("preview").path("items").get(0).path("purchase_quantity").decimalValue())
                .isEqualByComparingTo("5");
        assertThat(first.path("preview").path("items").get(0).path("stock_quantity").isNull()).isTrue();

        mockMvc.perform(post("/api/v1/businesses/{businessId}/goods-receipts/{previewId}/confirm",
                        businessId, previewId).with(authentication(auth)).header("Idempotency-Key", "confirm-stale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preview_version\":99,\"items\":[{\"line_number\":1,"
                                + "\"action\":\"CREATE_PRODUCT\",\"base_unit\":\"RS\"}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_PREVIEW"));
        mockMvc.perform(post("/api/v1/businesses/{businessId}/goods-receipts/{previewId}/confirm",
                        businessId, previewId).with(authentication(auth)).header("Idempotency-Key", "confirm-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preview_version\":0,\"items\":[{\"line_number\":1,"
                                + "\"action\":\"USE_EXISTING\"}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PRODUCT_SELECTION"));
        var confirmation = mockMvc.perform(post("/api/v1/businesses/{businessId}/goods-receipts/{previewId}/confirm",
                        businessId, previewId).with(authentication(auth)).header("Idempotency-Key", "confirm-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preview_version\":0,\"items\":[{\"line_number\":1,"
                                + "\"action\":\"CREATE_PRODUCT\",\"base_unit\":\"RS\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.item_count").value(1))
                .andExpect(jsonPath("$.items[0].quantity_added").value(5))
                .andReturn();
        var receipt = mapper.readTree(confirmation.getResponse().getContentAsString());
        var receiptId = receipt.path("receipt_id").asText();

        mockMvc.perform(post("/api/v1/businesses/{businessId}/goods-receipts/{previewId}/confirm",
                        businessId, previewId).with(authentication(auth)).header("Idempotency-Key", "confirm-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preview_version\":0,\"items\":[{\"line_number\":1,"
                                + "\"action\":\"CREATE_PRODUCT\",\"base_unit\":\"RS\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receipt_id").value(receiptId));

        mockMvc.perform(get("/api/v1/businesses/{businessId}/goods-receipts/{receiptId}", businessId, receiptId)
                        .with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receipt_id").value(receiptId))
                .andExpect(jsonPath("$.items[0].product_name").value("SULFITE A4 75GR BOREAL (5000FLS)"))
                .andExpect(jsonPath("$.items[0].quantity_added").value(5));

        mockMvc.perform(get("/api/v1/businesses/{businessId}/products", businessId)
                        .param("q", "SULFITE").with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("SULFITE A4 75GR BOREAL (5000FLS)"));
        var productId = UUID.fromString(receipt.path("items").get(0).path("product_id").asText());
        seedGtin(businessId, productId, "7894900011517");
        mockMvc.perform(get("/api/v1/businesses/{businessId}/products", businessId)
                        .param("gtin", "7894900011517").with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].product_id").value(productId.toString()));

        assertThat(count("inventory_movements", businessId)).isEqualTo(1);
        assertThat(decimal("inventory_balances", "quantity", businessId)).isEqualByComparingTo("5");
        assertThat(mapper.readTree(retrieve(auth, businessId, "retrieve-1").toString())
                .path("document_id").asText()).isEqualTo(documentId);
    }

    @Test
    void stableErrorsAndTenantBoundaryAreEnforced() throws Exception {
        var auth = principal(OWNER);
        var businessId = createBusiness(auth);
        mockMvc.perform(post("/api/v1/businesses/{businessId}/nfe-documents", businessId)
                        .with(authentication(auth)).header("Idempotency-Key", "bad-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"access_key\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_KEY"))
                .andExpect(jsonPath("$.retryable").value(false));
        mockMvc.perform(post("/api/v1/businesses/{businessId}/nfe-documents", businessId)
                        .with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"access_key\":\"" + ACCESS_KEY + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        var other = principal(OTHER);
        mockMvc.perform(get("/api/v1/businesses/{businessId}/products", businessId)
                        .param("q", "anything").with(authentication(other)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BUSINESS_ACCESS_DENIED"));
    }

    private JsonNode retrieve(Authentication auth, UUID businessId, String idempotencyKey) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/businesses/{businessId}/nfe-documents", businessId)
                        .with(authentication(auth)).header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"access_key\":\"" + ACCESS_KEY + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private UUID createBusiness(Authentication auth) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/businesses").with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"NFE Android Contract\",\"vertical\":\"RETAIL\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private int count(String table, UUID businessId) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
                var tenant = connection.prepareStatement("SELECT set_config('app.business_id', ?, false)")) {
            tenant.setString(1, businessId.toString());
            tenant.executeQuery().close();
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT count(*) FROM public." + table)) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private java.math.BigDecimal decimal(String table, String column, UUID businessId) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
                var tenant = connection.prepareStatement("SELECT set_config('app.business_id', ?, false)")) {
            tenant.setString(1, businessId.toString());
            tenant.executeQuery().close();
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT " + column + " FROM public." + table)) {
                result.next();
                return result.getBigDecimal(1);
            }
        }
    }

    private void seedGtin(UUID businessId, UUID productId, String gtin) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
                var tenant = connection.prepareStatement("SELECT set_config('app.business_id', ?, false)")) {
            tenant.setString(1, businessId.toString());
            tenant.executeQuery().close();
            try (var statement = connection.prepareStatement(
                    "INSERT INTO public.product_identifiers "
                            + "(id, business_id, product_id, identifier_type, identifier_value, source, created_at) "
                            + "VALUES (?, ?, ?, 'GTIN', ?, 'TEST', now())")) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, businessId);
                statement.setObject(3, productId);
                statement.setString(4, gtin);
                statement.executeUpdate();
            }
        }
    }

    private static Authentication principal(String subject) {
        return new AuthenticatedPrincipalAuthenticationToken(
                new AuthenticatedPrincipal(new ExternalSubject(subject)), List.of());
    }
}
