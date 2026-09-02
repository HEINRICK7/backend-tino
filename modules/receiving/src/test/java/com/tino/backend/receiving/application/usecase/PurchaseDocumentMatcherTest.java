package com.tino.backend.receiving.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.receiving.application.model.PurchaseDocument;
import com.tino.backend.receiving.application.model.PurchaseDocumentMatch.Status;
import com.tino.backend.receiving.application.port.out.PurchaseDocumentProductLookup;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurchaseDocumentMatcherTest {
    private static final BusinessId BUSINESS = new BusinessId(UUID.randomUUID());
    private static final UUID GTIN_PRODUCT = UUID.randomUUID();
    private static final UUID MAPPED_PRODUCT = UUID.randomUUID();
    private static final UUID SIMILAR_PRODUCT = UUID.randomUUID();
    private static final String GTIN = "4006381333931";

    @Test
    void appliesGtinThenLearnedMappingThenNormalizedDescription() {
        var matcher = new PurchaseDocumentMatcher(new FakeCatalog());
        var matches = matcher.match(BUSINESS, document(List.of(
                item(1, "OUTRO", GTIN, null),
                item(2, "Código mapeado", null, "MAP-7"),
                item(3, "Cafe Coado", null, null))));

        assertThat(matches).extracting("status")
                .containsExactly(Status.EXACT_MATCH, Status.HIGH_CONFIDENCE_MATCH, Status.HIGH_CONFIDENCE_MATCH);
        assertThat(matches.get(0).productId()).isEqualTo(GTIN_PRODUCT);
        assertThat(matches.get(1).productId()).isEqualTo(MAPPED_PRODUCT);
    }

    @Test
    void neverAutoAcceptsAnApproximateDescriptionAndMarksUnknownAsNewProduct() {
        var matcher = new PurchaseDocumentMatcher(new FakeCatalog());
        var matches = matcher.match(BUSINESS, document(List.of(
                item(1, "Cafe Torrado Extra", null, null),
                item(2, "Açúcar cristal", null, null))));

        assertThat(matches.get(0).status()).isEqualTo(Status.REVIEW_REQUIRED);
        assertThat(matches.get(0).productId()).isEqualTo(SIMILAR_PRODUCT);
        assertThat(matches.get(0).requiresUserAction()).isTrue();
        assertThat(matches.get(1).status()).isEqualTo(Status.NEW_PRODUCT);
        assertThat(matches.get(1).productId()).isNull();
    }

    private static PurchaseDocument document(List<PurchaseDocument.Item> items) {
        return new PurchaseDocument(PurchaseDocument.Source.NFCE, PurchaseDocument.DocumentType.NFCE,
                "22260831838128000748650120002104021782591975", null,
                new PurchaseDocument.Issuer("Fornecedor", "31838128000748"), items, BigDecimal.ONE);
    }

    private static PurchaseDocument.Item item(int line, String description, String gtin, String code) {
        return new PurchaseDocument.Item(line, code, gtin, description, BigDecimal.ONE, "UN", BigDecimal.ONE, BigDecimal.ONE);
    }

    private static final class FakeCatalog implements PurchaseDocumentProductLookup {
        private final List<ProductCandidate> products = List.of(
                new ProductCandidate(GTIN_PRODUCT, "Produto GTIN", "UN", GTIN),
                new ProductCandidate(MAPPED_PRODUCT, "Produto mapeado", "UN", null),
                new ProductCandidate(SIMILAR_PRODUCT, "Cafe Torrado", "UN", null, List.of("Cafe Coado")));

        @Override
        public List<ProductCandidate> findByGtin(BusinessId businessId, String gtin) {
            return products.stream().filter(product -> gtin.equals(product.gtin())).toList();
        }

        @Override
        public Optional<ProductCandidate> findById(BusinessId businessId, UUID productId) {
            return products.stream().filter(product -> product.productId().equals(productId)).findFirst();
        }

        @Override
        public Optional<ProductCandidate> findByIssuerAndExternalCode(BusinessId businessId, String issuerTaxId, String externalCode) {
            return "MAP-7".equals(externalCode) ? Optional.of(products.get(1)) : Optional.empty();
        }

        @Override
        public List<ProductCandidate> findActive(BusinessId businessId) {
            return products;
        }
    }
}
