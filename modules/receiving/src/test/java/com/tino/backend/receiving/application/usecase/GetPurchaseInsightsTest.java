package com.tino.backend.receiving.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.receiving.application.port.out.PurchaseHistoryRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetPurchaseInsightsTest {
    private static final BusinessId BUSINESS = new BusinessId(UUID.randomUUID());
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID PREVIOUS_RECEIPT = UUID.randomUUID();
    private static final UUID CURRENT_RECEIPT = UUID.randomUUID();
    private static final UUID PREVIOUS_OBSERVATION = UUID.randomUUID();
    private static final UUID CURRENT_OBSERVATION = UUID.randomUUID();
    private static final UUID CURRENT_OBSERVATION_2 = UUID.randomUUID();
    private static final UUID CURRENT_OBSERVATION_3 = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @Test
    void everyGeneratedInsightCarriesPersistedEvidenceIds() {
        var repository = new FakeHistory();
        BusinessAuthorization authorization = new BusinessAuthorization() {
            @Override
            public <T> T execute(UUID user, BusinessId business, java.util.function.Function<BusinessId, T> operation) {
                return operation.apply(business);
            }
        };
        var useCase = new GetPurchaseInsights(authorization, repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = useCase.execute(UUID.randomUUID(), BUSINESS, "MONTH");
        var persistedIds = List.of(PREVIOUS_RECEIPT, CURRENT_RECEIPT, PREVIOUS_OBSERVATION, CURRENT_OBSERVATION,
                CURRENT_OBSERVATION_2, CURRENT_OBSERVATION_3, PRODUCT);

        assertThat(result.insights()).extracting(GetPurchaseInsights.Insight::type)
                .contains("SPEND_COMPARISON", "COST_CHANGE", "MARGIN_IMPACT", "PURCHASE_FREQUENCY");
        assertThat(result.insights()).allSatisfy(insight -> {
            assertThat(insight.evidenceIds()).isNotEmpty();
            assertThat(insight.evidenceIds()).allMatch(persistedIds::contains);
        });
        assertThat(result.insights().stream().filter(insight -> insight.type().equals("MARGIN_IMPACT")).findFirst())
                .get().extracting(GetPurchaseInsights.Insight::message)
                .asString().contains("margem bruta estimada caiu");
    }

    private static final class FakeHistory implements PurchaseHistoryRepository {
        @Override
        public List<PurchaseHistoryEntry> findEntries(BusinessId businessId, Instant from, Instant to) {
            if (from.equals(Instant.parse("2026-08-01T00:00:00Z"))) {
                return List.of(new PurchaseHistoryEntry(PREVIOUS_RECEIPT, from.plusSeconds(3600), "Fornecedor",
                        new BigDecimal("80.00"), 1, 0, new BigDecimal("1")));
            }
            return List.of(new PurchaseHistoryEntry(CURRENT_RECEIPT, from.plusSeconds(3600), "Fornecedor",
                    new BigDecimal("90.00"), 3, 0, new BigDecimal("3")));
        }

        @Override
        public java.util.Optional<PurchaseHistoryDetail> findDetail(BusinessId businessId, UUID receiptId) {
            return java.util.Optional.empty();
        }

        @Override
        public List<PurchasePriceFact> findPriceFacts(BusinessId businessId, Instant from, Instant to) {
            if (from.equals(Instant.parse("2026-08-01T00:00:00Z"))) {
                return List.of(new PurchasePriceFact(PREVIOUS_OBSERVATION, PREVIOUS_RECEIPT, PRODUCT, "Café",
                        new BigDecimal("8.00"), BigDecimal.ONE, "UN", from.plusSeconds(3600), new BigDecimal("12.00")));
            }
            return List.of(
                    new PurchasePriceFact(CURRENT_OBSERVATION, CURRENT_RECEIPT, PRODUCT, "Café",
                            new BigDecimal("9.00"), BigDecimal.ONE, "UN", from.plusSeconds(3600), new BigDecimal("12.00")),
                    new PurchasePriceFact(CURRENT_OBSERVATION_2, CURRENT_RECEIPT, PRODUCT, "Café",
                            new BigDecimal("9.00"), BigDecimal.ONE, "UN", from.plusSeconds(7200), new BigDecimal("12.00")),
                    new PurchasePriceFact(CURRENT_OBSERVATION_3, CURRENT_RECEIPT, PRODUCT, "Café",
                            new BigDecimal("9.00"), BigDecimal.ONE, "UN", from.plusSeconds(10800), new BigDecimal("12.00")));
        }
    }
}
