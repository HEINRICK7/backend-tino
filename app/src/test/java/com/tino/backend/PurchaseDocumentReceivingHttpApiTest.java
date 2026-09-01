package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tino.backend.identity.adapter.in.security.AuthenticatedPrincipalAuthenticationToken;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.domain.model.ExternalSubject;
import java.math.BigDecimal;
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

/** N2 proof: canonical NFC-e input becomes a tenant-scoped preview only. */
@Testcontainers
@SpringBootTest
class PurchaseDocumentReceivingHttpApiTest {
    private static final String ACCESS_KEY = "22260831838128000748650120002104021782591975";
    private static final String OWNER = "purchase-document-owner";
    private static final String OTHER = "purchase-document-other";

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
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
                var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.purchase_receipt_confirmation_idempotency, "
                    + "public.receiving_events, public.purchase_price_observations, public.purchase_receipt_items, "
                    + "public.purchase_receipts, public.receiving_purchase_preview_idempotency, "
                    + "public.receiving_purchase_preview_items, public.receiving_purchase_previews, "
                    + "public.purchase_document_items, public.purchase_documents, "
                    + "public.external_product_price_options, public.external_product_mappings, "
                    + "public.inventory_movements, public.inventory_balances, public.goods_receipt_items, "
                    + "public.goods_receipts, public.goods_receipt_preview_items, public.goods_receipt_previews, "
                    + "public.packaging_conversions, public.supplier_product_mappings, public.product_identifiers, "
                    + "public.products, public.nfe_retrieval_idempotency_keys, public.nfe_items, "
                    + "public.nfe_document_versions, public.nfe_documents, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users CASCADE");
        }
    }

    @Test
    void canonicalNfceCreatesPreviewWithoutOperationalMutationAndDeduplicatesByAccessKey() throws Exception {
        var owner = principal(OWNER);
        var businessId = createBusiness(owner);

        var first = preview(owner, businessId, "preview-1", requestBody());
        var previewId = first.path("preview_id").asText();
        assertThat(first.path("status").asText()).isEqualTo("REVIEW_READY");
        assertThat(first.path("source").asText()).isEqualTo("NFCE");
        assertThat(first.path("document_type").asText()).isEqualTo("NFCE");
        assertThat(first.path("access_key").asText()).isEqualTo(ACCESS_KEY);
        assertThat(first.path("issuer").path("name").asText()).isEqualTo("GRUPO VANGUARDA");
        assertThat(first.path("items").size()).isEqualTo(1);
        assertThat(first.path("items").get(0).path("description").asText())
                .isEqualTo("QUEIJO MUSS ISIS 150G FAT");
        assertThat(first.path("items").get(0).path("unit_price").decimalValue())
                .isEqualByComparingTo("10.790");
        assertThat(first.toString()).doesNotContain("html", "cookie", "hCaptcha", "token");

        var sameDocument = preview(owner, businessId, "preview-2", requestBody());
        assertThat(sameDocument.path("preview_id").asText()).isEqualTo(previewId);
        assertThat(count("purchase_documents", businessId)).isEqualTo(1);
        assertThat(count("purchase_document_items", businessId)).isEqualTo(1);
        assertThat(count("receiving_purchase_previews", businessId)).isEqualTo(1);
        assertThat(count("receiving_purchase_preview_items", businessId)).isEqualTo(1);
        assertThat(count("products", businessId)).isZero();
        assertThat(count("inventory_movements", businessId)).isZero();
        assertThat(count("inventory_balances", businessId)).isZero();
        assertThat(count("goods_receipts", businessId)).isZero();
    }

    @Test
    void idempotencyKeyCannotBeReusedForAnotherCanonicalPayload() throws Exception {
        var owner = principal(OWNER);
        var businessId = createBusiness(owner);
        preview(owner, businessId, "same-key", requestBody());

        mockMvc.perform(post("/api/v1/businesses/{businessId}/receiving/purchase-documents/preview", businessId)
                        .with(authentication(owner)).header("Idempotency-Key", "same-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("DESCRICAO ALTERADA")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(count("purchase_documents", businessId)).isEqualTo(1);
        assertThat(count("receiving_purchase_previews", businessId)).isEqualTo(1);
    }

    @Test
    void previewReturnsExplicitDeterministicMatchingStates() throws Exception {
        var owner = principal(OWNER);
        var businessId = createBusiness(owner);
        var productId = UUID.randomUUID();
        seedProduct(businessId, productId, "Produto GTIN", "UN", "4006381333931");

        var body = "{"
                + "\"source\":\"NFCE\",\"document_type\":\"NFCE\","
                + "\"access_key\":\"" + ACCESS_KEY + "\","
                + "\"issuer\":{\"name\":\"GRUPO VANGUARDA\",\"tax_id\":\"31838128000748\"},"
                + "\"items\":["
                + "{\"line_number\":1,\"external_code\":\"A\",\"gtin\":\"4006381333931\",\"description\":\"Outro\",\"quantity\":1,\"unit\":\"UN\",\"unit_price\":1,\"total_price\":1},"
                + "{\"line_number\":2,\"external_code\":\"B\",\"gtin\":null,\"description\":\"Produto GTIN\",\"quantity\":1,\"unit\":\"UN\",\"unit_price\":2,\"total_price\":2}"
                + "],\"total\":3}";

        var response = preview(owner, businessId, "matching-1", body);
        assertThat(response.path("items").get(0).path("match_status").asText()).isEqualTo("EXACT_MATCH");
        assertThat(response.path("items").get(0).path("matched_product_id").asText()).isEqualTo(productId.toString());
        assertThat(response.path("items").get(1).path("match_status").asText()).isEqualTo("HIGH_CONFIDENCE_MATCH");
        assertThat(response.path("summary").path("matched").asLong()).isEqualTo(2);
        assertThat(count("products", businessId)).isEqualTo(1);
    }

    @Test
    void confirmationIsTransactionalAndIdempotentAcrossOperationalEffects() throws Exception {
        var owner = principal(OWNER);
        var businessId = createBusiness(owner);
        var productId = UUID.randomUUID();
        seedProduct(businessId, productId, "Produto GTIN", "UN", "4006381333931");
        var preview = preview(owner, businessId, "confirm-preview", matchingBody(productId));
        var previewId = UUID.fromString(preview.path("preview_id").asText());

        var first = confirm(owner, businessId, previewId, "confirm-1", productId.toString(), 0);
        var receiptId = first.path("receipt_id").asText();
        assertThat(first.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(first.path("item_count").asInt()).isEqualTo(1);
        assertThat(count("purchase_receipts", businessId)).isEqualTo(1);
        assertThat(count("purchase_receipt_items", businessId)).isEqualTo(1);
        assertThat(count("purchase_price_observations", businessId)).isEqualTo(1);
        assertThat(count("receiving_events", businessId)).isEqualTo(1);
        assertThat(count("inventory_movements", businessId)).isEqualTo(1);
        assertThat(count("inventory_balances", businessId)).isEqualTo(1);

        var repeated = confirm(owner, businessId, previewId, "confirm-1", productId.toString(), 0);
        assertThat(repeated.path("receipt_id").asText()).isEqualTo(receiptId);
        var repeatedWithAnotherKey = confirm(owner, businessId, previewId, "confirm-2", productId.toString(), 0);
        assertThat(repeatedWithAnotherKey.path("receipt_id").asText()).isEqualTo(receiptId);
        assertThat(count("purchase_receipts", businessId)).isEqualTo(1);
        assertThat(count("inventory_movements", businessId)).isEqualTo(1);
        assertThat(count("purchase_price_observations", businessId)).isEqualTo(1);
    }

    @Test
    void confirmationRollsBackEveryEffectWhenALaterItemIsInvalid() throws Exception {
        var owner = principal(OWNER);
        var businessId = createBusiness(owner);
        var productId = UUID.randomUUID();
        seedProduct(businessId, productId, "Produto GTIN", "UN", "4006381333931");
        var preview = preview(owner, businessId, "rollback-preview", twoItemBody());
        var previewId = UUID.fromString(preview.path("preview_id").asText());

        var body = "{\"preview_version\":0,\"items\":["
                + "{\"line_number\":1,\"action\":\"USE_EXISTING\",\"product_id\":\"" + productId
                + "\",\"base_unit\":\"UN\",\"conversion_factor\":null},"
                + "{\"line_number\":2,\"action\":\"USE_EXISTING\",\"product_id\":\""
                + UUID.randomUUID() + "\",\"base_unit\":\"UN\",\"conversion_factor\":null}]}";

        mockMvc.perform(post("/api/v1/businesses/{businessId}/receiving/purchase-documents/{previewId}/confirm",
                        businessId, previewId)
                        .with(authentication(owner)).header("Idempotency-Key", "rollback-confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_SELECTION"));
        assertThat(count("purchase_receipts", businessId)).isZero();
        assertThat(count("purchase_receipt_items", businessId)).isZero();
        assertThat(count("purchase_price_observations", businessId)).isZero();
        assertThat(count("receiving_events", businessId)).isZero();
        assertThat(count("inventory_movements", businessId)).isZero();
        assertThat(count("inventory_balances", businessId)).isZero();
        assertThat(count("products", businessId)).isEqualTo(1);
    }

    @Test
    void confirmationRejectsStalePreviewWithoutOperationalEffects() throws Exception {
        var owner = principal(OWNER);
        var businessId = createBusiness(owner);
        var productId = UUID.randomUUID();
        seedProduct(businessId, productId, "Produto GTIN", "UN", "4006381333931");
        var preview = preview(owner, businessId, "stale-preview", matchingBody(productId));
        var previewId = UUID.fromString(preview.path("preview_id").asText());

        var body = "{\"preview_version\":99,\"items\":["
                + "{\"line_number\":1,\"action\":\"USE_EXISTING\",\"product_id\":\"" + productId
                + "\",\"base_unit\":\"UN\",\"conversion_factor\":null}]}";
        mockMvc.perform(post("/api/v1/businesses/{businessId}/receiving/purchase-documents/{previewId}/confirm",
                        businessId, previewId)
                        .with(authentication(owner)).header("Idempotency-Key", "stale-confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_PREVIEW"));
        assertThat(count("purchase_receipts", businessId)).isZero();
        assertThat(count("inventory_movements", businessId)).isZero();
    }

    @Test
    void confirmationCreatesOnlyTheMinimalNewProductAndLearnsPurchaseConversion() throws Exception {
        var owner = principal(OWNER);
        var businessId = createBusiness(owner);
        var preview = preview(owner, businessId, "new-product-preview", newProductBody());
        var previewId = UUID.fromString(preview.path("preview_id").asText());
        assertThat(preview.path("items").get(0).path("match_status").asText()).isEqualTo("NEW_PRODUCT");

        var body = "{\"preview_version\":0,\"items\":["
                + "{\"line_number\":1,\"action\":\"CREATE_PRODUCT\",\"product_id\":null,"
                + "\"base_unit\":\"UN\",\"conversion_factor\":12}]}";
        var response = mapper.readTree(mockMvc.perform(post(
                        "/api/v1/businesses/{businessId}/receiving/purchase-documents/{previewId}/confirm",
                        businessId, previewId)
                        .with(authentication(owner)).header("Idempotency-Key", "new-product-confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(response.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(count("products", businessId)).isEqualTo(1);
        assertThat(count("supplier_product_mappings", businessId)).isEqualTo(1);
        assertThat(count("packaging_conversions", businessId)).isEqualTo(1);
        assertThat(count("purchase_receipt_items", businessId)).isEqualTo(1);
        assertThat(count("inventory_movements", businessId)).isEqualTo(1);
        assertThat(decimal("inventory_balances", "quantity", businessId)).isEqualByComparingTo("24");
    }

    @Test
    void purchaseHistoryReconstructsTheConfirmedFactsForWeekMonthAndYear() throws Exception {
        var owner = principal(OWNER);
        var businessId = createBusiness(owner);
        var productId = UUID.randomUUID();
        seedProduct(businessId, productId, "Produto GTIN", "UN", "4006381333931");
        var preview = preview(owner, businessId, "history-preview", matchingBody(productId));
        var receipt = confirm(owner, businessId, UUID.fromString(preview.path("preview_id").asText()),
                "history-confirm", productId.toString(), 0);
        var receiptId = receipt.path("receipt_id").asText();

        for (var period : List.of("WEEK", "MONTH", "YEAR")) {
            mockMvc.perform(get("/api/v1/businesses/{businessId}/receiving/purchase-documents/purchase-history", businessId)
                            .with(authentication(owner)).param("period", period))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period").value(period))
                    .andExpect(jsonPath("$.purchase_count").value(1))
                    .andExpect(jsonPath("$.item_count").value(1))
                    .andExpect(jsonPath("$.purchases[0].receipt_id").value(receiptId));
        }

        mockMvc.perform(get("/api/v1/businesses/{businessId}/receiving/purchase-documents/purchase-history/{receiptId}", businessId, receiptId)
                        .with(authentication(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_key").value(ACCESS_KEY))
                .andExpect(jsonPath("$.items[0].description").value("Produto GTIN"))
                .andExpect(jsonPath("$.items[0].stock_quantity").value(1.0));

        mockMvc.perform(get("/api/v1/businesses/{businessId}/receiving/purchase-documents/purchase-history-insights", businessId)
                        .with(authentication(owner)).param("period", "MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("MONTH"))
                .andExpect(jsonPath("$.insights").isArray());
    }

    @Test
    void authenticationTenantAndCanonicalValidationAreEnforced() throws Exception {
        var owner = principal(OWNER);
        var businessId = createBusiness(owner);
        var other = principal(OTHER);

        mockMvc.perform(post("/api/v1/businesses/{businessId}/receiving/purchase-documents/preview", businessId)
                        .with(authentication(other)).header("Idempotency-Key", "foreign")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BUSINESS_ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/businesses/{businessId}/receiving/purchase-documents/purchase-history", businessId)
                        .with(authentication(other)).param("period", "WEEK"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BUSINESS_ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/businesses/{businessId}/receiving/purchase-documents/purchase-history-insights", businessId)
                        .with(authentication(other)).param("period", "WEEK"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BUSINESS_ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/businesses/{businessId}/receiving/purchase-documents/preview", businessId)
                        .with(authentication(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/businesses/{businessId}/receiving/purchase-documents/preview", businessId)
                        .with(authentication(owner)).header("Idempotency-Key", "bad-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody().replace("\"source\":\"NFCE\"", "\"source\":\"NFE\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/businesses/{businessId}/receiving/purchase-documents/preview", businessId)
                        .with(authentication(owner)).header("Idempotency-Key", "bad-key-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody().replace(ACCESS_KEY, ACCESS_KEY.substring(0, 43) + "0")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(count("purchase_documents", businessId)).isZero();
    }

    private JsonNode preview(Authentication auth, UUID businessId, String idempotencyKey, String body) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/businesses/{businessId}/receiving/purchase-documents/preview", businessId)
                        .with(authentication(auth)).header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode confirm(Authentication auth, UUID businessId, UUID previewId, String idempotencyKey,
            String productId, long version) throws Exception {
        var body = "{\"preview_version\":" + version + ",\"items\":["
                + "{\"line_number\":1,\"action\":\"USE_EXISTING\",\"product_id\":\"" + productId
                + "\",\"base_unit\":\"UN\",\"conversion_factor\":null}]}";
        return mapper.readTree(mockMvc.perform(post("/api/v1/businesses/{businessId}/receiving/purchase-documents/{previewId}/confirm", businessId, previewId)
                        .with(authentication(auth)).header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private UUID createBusiness(Authentication auth) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/businesses").with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"NFC-e N2\",\"vertical\":\"RETAIL\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private static String requestBody() {
        return requestBody("QUEIJO MUSS ISIS 150G FAT");
    }

    private static String requestBody(String description) {
        return "{"
                + "\"source\":\"NFCE\",\"document_type\":\"NFCE\","
                + "\"access_key\":\"" + ACCESS_KEY + "\","
                + "\"issued_at\":\"2026-08-29T08:04:14-03:00\","
                + "\"issuer\":{\"name\":\"GRUPO VANGUARDA\",\"tax_id\":\"31838128000748\"},"
                + "\"items\":[{\"line_number\":1,\"external_code\":\"249886\","
                + "\"gtin\":null,\"description\":\"" + description + "\",\"quantity\":1,"
                + "\"unit\":\"UN\",\"unit_price\":10.790,\"total_price\":10.790}],"
                + "\"total\":65.11} ";
    }

    private static String matchingBody(UUID productId) {
        return "{"
                + "\"source\":\"NFCE\",\"document_type\":\"NFCE\","
                + "\"access_key\":\"" + ACCESS_KEY + "\","
                + "\"issued_at\":\"2026-08-29T08:04:14-03:00\","
                + "\"issuer\":{\"name\":\"GRUPO VANGUARDA\",\"tax_id\":\"31838128000748\"},"
                + "\"items\":[{\"line_number\":1,\"external_code\":\"249886\",\"gtin\":\"4006381333931\","
                + "\"description\":\"Produto GTIN\",\"quantity\":1,\"unit\":\"UN\",\"unit_price\":10.790,\"total_price\":10.790}],"
                + "\"total\":10.790}";
    }

    private static String twoItemBody() {
        return "{"
                + "\"source\":\"NFCE\",\"document_type\":\"NFCE\","
                + "\"access_key\":\"" + ACCESS_KEY + "\","
                + "\"issued_at\":\"2026-08-29T08:04:14-03:00\","
                + "\"issuer\":{\"name\":\"GRUPO VANGUARDA\",\"tax_id\":\"31838128000748\"},"
                + "\"items\":["
                + "{\"line_number\":1,\"external_code\":\"249886\",\"gtin\":\"4006381333931\","
                + "\"description\":\"Produto GTIN\",\"quantity\":1,\"unit\":\"UN\",\"unit_price\":10.790,\"total_price\":10.790},"
                + "{\"line_number\":2,\"external_code\":\"novo\",\"gtin\":null,"
                + "\"description\":\"Produto novo\",\"quantity\":1,\"unit\":\"UN\",\"unit_price\":2.000,\"total_price\":2.000}],"
                + "\"total\":12.790}";
    }

    private static String newProductBody() {
        return "{"
                + "\"source\":\"NFCE\",\"document_type\":\"NFCE\","
                + "\"access_key\":\"" + ACCESS_KEY + "\","
                + "\"issued_at\":\"2026-08-29T08:04:14-03:00\","
                + "\"issuer\":{\"name\":\"GRUPO VANGUARDA\",\"tax_id\":\"31838128000748\"},"
                + "\"items\":[{\"line_number\":1,\"external_code\":\"cx-novo\",\"gtin\":null,"
                + "\"description\":\"Produto novo\",\"quantity\":2,\"unit\":\"CX\",\"unit_price\":10.000,\"total_price\":20.000}],"
                + "\"total\":20.000}";
    }

    private int count(String table, UUID businessId) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
                var tenant = connection.prepareStatement("SELECT set_config('app.business_id', ?, false)")) {
            tenant.setString(1, businessId.toString());
            tenant.executeQuery().close();
            try (var statement = connection.createStatement(); var result = statement.executeQuery(
                    "SELECT count(*) FROM public." + table)) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private BigDecimal decimal(String table, String column, UUID businessId) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
                var statement = connection.prepareStatement("SELECT " + column + " FROM public." + table
                        + " WHERE business_id = ?")) {
            setTenant(connection, businessId);
            statement.setObject(1, businessId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBigDecimal(1);
            }
        }
    }

    private void seedProduct(UUID businessId, UUID productId, String name, String baseUnit, String gtin) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
                var product = connection.prepareStatement("INSERT INTO public.products "
                        + "(id,business_id,name,base_unit,status,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE',now(),now())")) {
            setTenant(connection, businessId);
            product.setObject(1, productId);
            product.setObject(2, businessId);
            product.setString(3, name);
            product.setString(4, baseUnit);
            product.executeUpdate();
        }
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
                var identifier = connection.prepareStatement("INSERT INTO public.product_identifiers "
                        + "(id,business_id,product_id,identifier_type,identifier_value,source,created_at) VALUES (?,?,?,'GTIN',?,'NFCE',now())")) {
            setTenant(connection, businessId);
            identifier.setObject(1, UUID.randomUUID());
            identifier.setObject(2, businessId);
            identifier.setObject(3, productId);
            identifier.setString(4, gtin);
            identifier.executeUpdate();
        }
    }

    private void setTenant(java.sql.Connection connection, UUID businessId) throws Exception {
        try (var tenant = connection.prepareStatement("SELECT set_config('app.business_id', ?, false)")) {
            tenant.setString(1, businessId.toString());
            tenant.executeQuery().close();
        }
    }

    private static Authentication principal(String subject) {
        return new AuthenticatedPrincipalAuthenticationToken(
                new AuthenticatedPrincipal(new ExternalSubject(subject)), List.of());
    }
}
