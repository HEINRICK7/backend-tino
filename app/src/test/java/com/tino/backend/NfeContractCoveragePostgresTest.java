package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.business.application.model.AuthenticatedUser;
import com.tino.backend.business.application.usecase.CreateBusiness;
import com.tino.backend.business.domain.model.BusinessVertical;
import com.tino.backend.fiscal.adapter.out.serpro.SerproNfeParser;
import com.tino.backend.fiscal.application.model.NfeRetrievalResult;
import com.tino.backend.fiscal.application.port.out.NfeDocumentRepository;
import com.tino.backend.fiscal.application.port.out.NfeParser;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import com.tino.backend.fiscal.domain.model.RawNfePayload;
import com.tino.backend.identity.application.port.out.UserRepository;
import com.tino.backend.identity.domain.model.ExternalSubject;
import com.tino.backend.identity.domain.model.User;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import com.tino.backend.shared.kernel.UuidV7Generator;
import java.io.IOException;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Contract gate from the provider-shaped fixture through canonical persistence. */
@Testcontainers
@SpringBootTest
class NfeContractCoveragePostgresTest {
    private static final String ACCESS_KEY = "53160911510448000171550010000106771000187760";
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @Autowired
    private UserRepository users;

    @Autowired
    private CreateBusiness createBusiness;

    @Autowired
    private NfeDocumentRepository documents;

    @Autowired
    private NfeParser parser;

    @Autowired
    private TenantContextExecutor tenants;

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

    @Test
    void persistsEveryGoodsReceiptFieldAndCanonicalEvidence() throws Exception {
        var identityUserId = new com.tino.backend.identity.domain.model.UserId(new UuidV7Generator().next());
        users.insert(User.active(identityUserId, new ExternalSubject("nfe-contract-coverage"), NOW, NOW));
        var businessUserId = new com.tino.backend.business.domain.model.UserId(identityUserId.value());
        var business = createBusiness.execute(new AuthenticatedUser(businessUserId, true),
                "Contract Coverage", BusinessVertical.RETAIL).business();
        var key = new NfeAccessKey(ACCESS_KEY);
        var rawJson = fixture();
        var canonical = parser.parse(rawJson, key);
        var documentId = new UuidV7Generator().next();

        tenants.execute(business.id(), () -> documents.save(business.id(), documentId, key,
                NfeRetrievalResult.success(new RawNfePayload(rawJson, "serpro", "trial-v1"), canonical), NOW));

        try (var connection = adminConnection();
                var statement = connection.prepareStatement(
                        "SELECT supplier_product_code, gtin, tax_gtin, description, ncm, cest, cfop, "
                                + "commercial_unit, commercial_quantity, commercial_unit_price, product_total, "
                                + "tax_unit, tax_quantity, tax_unit_price, discount, freight, insurance, other_value, "
                                + "included_in_total FROM public.nfe_items WHERE document_id = ?");) {
            statement.setObject(1, documentId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("supplier_product_code")).isEqualTo("346");
                assertThat(result.getString("gtin")).isNull();
                assertThat(result.getString("tax_gtin")).isNull();
                assertThat(result.getString("description")).isEqualTo("SULFITE A4 75GR BOREAL (5000FLS)");
                assertThat(result.getString("ncm")).isEqualTo("48025610");
                assertThat(result.getString("cest")).isEqualTo("9999999");
                assertThat(result.getString("cfop")).isEqualTo("5102");
                assertThat(result.getString("commercial_unit")).isEqualTo("RS");
                assertThat(result.getBigDecimal("commercial_quantity")).isEqualByComparingTo("5");
                assertThat(result.getBigDecimal("commercial_unit_price")).isEqualByComparingTo("149");
                assertThat(result.getBigDecimal("product_total")).isEqualByComparingTo("745");
                assertThat(result.getString("tax_unit")).isEqualTo("RS");
                assertThat(result.getBigDecimal("tax_quantity")).isEqualByComparingTo("5");
                assertThat(result.getBigDecimal("tax_unit_price")).isEqualByComparingTo("149");
                assertThat(result.getBigDecimal("discount")).isEqualByComparingTo("0");
                assertThat(result.getBigDecimal("freight")).isEqualByComparingTo("0");
                assertThat(result.getBigDecimal("insurance")).isEqualByComparingTo("0");
                assertThat(result.getBigDecimal("other_value")).isEqualByComparingTo("0");
                assertThat(result.getBoolean("included_in_total")).isTrue();
                assertThat(result.next()).isFalse();
            }
        }

        try (var connection = adminConnection();
                var statement = connection.prepareStatement(
                        "SELECT raw_payload::text, canonical_payload::text, parser_version "
                                + "FROM public.nfe_document_versions WHERE document_id = ?")) {
            statement.setObject(1, documentId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("raw_payload")).contains("nfeProc");
                assertThat(result.getString("canonical_payload")).contains("15430", "SULFITE A4");
                assertThat(result.getString("parser_version")).isEqualTo(SerproNfeParser.VERSION);
            }
        }
    }

    private static String fixture() throws IOException {
        try (var stream = NfeContractCoveragePostgresTest.class.getResourceAsStream(
                "/serpro/consulta-nfe-trial-official-sanitized.json")) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static java.sql.Connection adminConnection() throws java.sql.SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
