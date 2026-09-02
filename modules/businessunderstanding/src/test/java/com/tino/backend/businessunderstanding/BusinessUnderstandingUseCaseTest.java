package com.tino.backend.businessunderstanding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.businessunderstanding.application.port.out.BusinessUnderstandingRepository;
import com.tino.backend.businessunderstanding.application.usecase.ConfirmItemPurpose;
import com.tino.backend.businessunderstanding.application.usecase.GetBusinessUnderstanding;
import com.tino.backend.businessunderstanding.application.usecase.ReplaceBusinessActivities;
import com.tino.backend.businessunderstanding.application.usecase.ReplaceOperatingModes;
import com.tino.backend.businessunderstanding.application.usecase.ResolveItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.ActivityCode;
import com.tino.backend.businessunderstanding.domain.model.BusinessActivity;
import com.tino.backend.businessunderstanding.domain.model.BusinessItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.BusinessOperatingMode;
import com.tino.backend.businessunderstanding.domain.model.BusinessUnderstandingSnapshot;
import com.tino.backend.businessunderstanding.domain.model.ItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeAuthority;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeHint;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeResolutionEvidence;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeSource;
import com.tino.backend.businessunderstanding.domain.model.OperatingMode;
import com.tino.backend.businessunderstanding.domain.model.UsageContext;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessUnderstandingUseCaseTest {
    private static final UUID USER = UUID.randomUUID();
    private static final BusinessId BUSINESS = new BusinessId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
    private static final BusinessAuthorization AUTHORIZATION = new BusinessAuthorization() {
        @Override
        public <T> T execute(UUID user, BusinessId business,
                java.util.function.Function<BusinessId, T> operation) {
            return operation.apply(business);
        }
    };

    @Test
    void understandingProgressesOnlyFromPersistedActivitiesAndModes() {
        var repository = new InMemoryRepository();
        var get = new GetBusinessUnderstanding(AUTHORIZATION, repository);

        assertThat(get.execute(USER, BUSINESS).status().name()).isEqualTo("NOT_STARTED");

        new ReplaceBusinessActivities(AUTHORIZATION, repository, CLOCK).execute(USER, BUSINESS,
                List.of(new BusinessActivity(ActivityCode.CONFEITARIA, null)));
        assertThat(get.execute(USER, BUSINESS).status().name()).isEqualTo("IN_PROGRESS");

        new ReplaceOperatingModes(AUTHORIZATION, repository, CLOCK).execute(USER, BUSINESS,
                List.of(OperatingMode.PRODUCES_GOODS, OperatingMode.BUYS_INPUTS));
        var ready = get.execute(USER, BUSINESS);
        assertThat(ready.status().name()).isEqualTo("READY");
        assertThat(ready.nextAction().name()).isEqualTo("NONE");
    }

    @Test
    void replacementIsIdempotentAndOtherRequiresItsCustomLabel() {
        var repository = new InMemoryRepository();
        var replace = new ReplaceBusinessActivities(AUTHORIZATION, repository, CLOCK);
        var activities = List.of(new BusinessActivity(ActivityCode.OTHER, "Casa de ração"),
                new BusinessActivity(ActivityCode.MERCADINHO, null),
                new BusinessActivity(ActivityCode.MERCADINHO, null));

        replace.execute(USER, BUSINESS, activities);
        replace.execute(USER, BUSINESS, activities);

        assertThat(repository.activities).containsExactly(
                new BusinessActivity(ActivityCode.OTHER, "Casa de ração"),
                new BusinessActivity(ActivityCode.MERCADINHO, null));
        assertThatThrownBy(() -> replace.execute(USER, BUSINESS,
                List.of(new BusinessActivity(ActivityCode.OTHER, null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmedPurposeWinsOverContextSuggestionAndCorrectionIsReused() {
        var repository = new InMemoryRepository();
        new ReplaceBusinessActivities(AUTHORIZATION, repository, CLOCK).execute(USER, BUSINESS,
                List.of(new BusinessActivity(ActivityCode.CONFEITARIA, null)));
        new ReplaceOperatingModes(AUTHORIZATION, repository, CLOCK).execute(USER, BUSINESS,
                List.of(OperatingMode.PRODUCES_GOODS, OperatingMode.RESELLS_GOODS));
        var product = UUID.randomUUID();
        var confirm = new ConfirmItemPurpose(AUTHORIZATION, repository, CLOCK);

        confirm.execute(USER, BUSINESS, product, ItemPurpose.PRODUCTION);
        var result = new ResolveItemPurpose(AUTHORIZATION, repository)
                .execute(USER, BUSINESS, product, "AÇÚCAR 1KG", "MANUAL");

        assertThat(result.purpose()).isEqualTo(ItemPurpose.PRODUCTION);
        assertThat(result.resolution()).isEqualTo("USER_CONFIRMED");
        assertThat(result.needsConfirmation()).isFalse();
        assertThat(result.authority()).isEqualTo(ItemPurposeAuthority.USER_CONFIRMED);

        confirm.execute(USER, BUSINESS, product, ItemPurpose.RESALE);
        assertThat(new ResolveItemPurpose(AUTHORIZATION, repository)
                .execute(USER, BUSINESS, product, "AÇÚCAR 1KG", "MANUAL").purpose())
                .isEqualTo(ItemPurpose.RESALE);
    }

    @Test
    void sameItemCanHaveDifferentPurposesInDifferentUsageContexts() {
        var repository = readyRepository();
        var product = UUID.randomUUID();
        var confirm = new ConfirmItemPurpose(AUTHORIZATION, repository, CLOCK);

        confirm.execute(USER, BUSINESS, product, UsageContext.of("SERVICE_CONSUMPTION"),
                ItemPurpose.SERVICE_INPUT, "Usado durante o atendimento");
        confirm.execute(USER, BUSINESS, product, UsageContext.of("DIRECT_SALE"),
                ItemPurpose.RESALE, "Vendido separadamente");

        var resolver = new ResolveItemPurpose(AUTHORIZATION, repository);
        assertThat(resolver.execute(USER, BUSINESS, product, "SHAMPOO",
                UsageContext.of("SERVICE_CONSUMPTION"), "MANUAL").purpose())
                .isEqualTo(ItemPurpose.SERVICE_INPUT);
        assertThat(resolver.execute(USER, BUSINESS, product, "SHAMPOO",
                UsageContext.of("DIRECT_SALE"), "MANUAL").purpose())
                .isEqualTo(ItemPurpose.RESALE);
    }

    @Test
    void automaticEvidenceCannotOverwriteUserConfirmedPurpose() {
        var repository = readyRepository();
        var product = UUID.randomUUID();
        repository.upsertAutomaticPurpose(purpose(product, UsageContext.LEGACY,
                ItemPurpose.PRODUCTION, ItemPurposeSource.SYSTEM_SUGGESTED, "SYSTEM", "Context suggestion"));
        repository.upsertAutomaticPurpose(purpose(product, UsageContext.LEGACY,
                ItemPurpose.RESALE, ItemPurposeSource.LEARNED, "SYSTEM", "Observed confirmation pattern"));
        new ConfirmItemPurpose(AUTHORIZATION, repository, CLOCK)
                .execute(USER, BUSINESS, product, ItemPurpose.SERVICE_INPUT);
        repository.upsertAutomaticPurpose(purpose(product, UsageContext.LEGACY,
                ItemPurpose.RESALE, ItemPurposeSource.LEARNED, "SYSTEM", "Late automatic update"));

        var stored = repository.findPurposeByProduct(BUSINESS, product, UsageContext.LEGACY).orElseThrow();
        assertThat(stored.purpose()).isEqualTo(ItemPurpose.SERVICE_INPUT);
        assertThat(stored.source()).isEqualTo(ItemPurposeSource.USER_CONFIRMED);
    }

    @Test
    void sameCanonicalItemIsIsolatedAcrossBusinesses() {
        var repository = new InMemoryRepository();
        var otherBusiness = new BusinessId(UUID.randomUUID());
        var first = purpose(BUSINESS, "SHAMPOO", UsageContext.of("DIRECT_SALE"),
                ItemPurpose.RESALE, ItemPurposeSource.LEARNED);
        var second = purpose(otherBusiness, "SHAMPOO", UsageContext.of("DIRECT_SALE"),
                ItemPurpose.SERVICE_INPUT, ItemPurposeSource.LEARNED);
        repository.upsertAutomaticPurpose(first);
        repository.upsertAutomaticPurpose(second);

        assertThat(repository.findPurposeByCanonicalKey(BUSINESS, "SHAMPOO",
                UsageContext.of("DIRECT_SALE")).orElseThrow().purpose()).isEqualTo(ItemPurpose.RESALE);
        assertThat(repository.findPurposeByCanonicalKey(otherBusiness, "SHAMPOO",
                UsageContext.of("DIRECT_SALE")).orElseThrow().purpose()).isEqualTo(ItemPurpose.SERVICE_INPUT);
    }

    @Test
    void evidenceIsKeptWithClassifierReasonContextAndTime() {
        var repository = readyRepository();
        var context = UsageContext.of("DIRECT_SALE");
        var purpose = purpose(UUID.randomUUID(), context, ItemPurpose.RESALE,
                ItemPurposeSource.SYSTEM_SUGGESTED, "SYSTEM", "Item was observed in a sale");
        repository.upsertAutomaticPurpose(purpose);

        var stored = repository.findPurposeByProduct(BUSINESS, purpose.productId(), context).orElseThrow();
        assertThat(stored.evidenceClassifiedBy()).isEqualTo("SYSTEM");
        assertThat(stored.evidenceReason()).isEqualTo("Item was observed in a sale");
        assertThat(stored.evidenceAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void multipleOperatingModesProduceAnUnknownAmbiguousPurpose() {
        var repository = new InMemoryRepository();
        new ReplaceBusinessActivities(AUTHORIZATION, repository, CLOCK).execute(USER, BUSINESS,
                List.of(new BusinessActivity(ActivityCode.CONFEITARIA, null)));
        new ReplaceOperatingModes(AUTHORIZATION, repository, CLOCK).execute(USER, BUSINESS,
                List.of(OperatingMode.PRODUCES_GOODS, OperatingMode.RESELLS_GOODS));

        var result = new ResolveItemPurpose(AUTHORIZATION, repository)
                .execute(USER, BUSINESS, null, "AÇÚCAR 1KG", "MANUAL");

        assertThat(result.purpose()).isEqualTo(ItemPurpose.UNKNOWN);
        assertThat(result.resolution()).isEqualTo("AMBIGUOUS");
        assertThat(result.suggestions()).containsExactly(ItemPurpose.PRODUCTION, ItemPurpose.RESALE);
        assertThat(result.needsConfirmation()).isTrue();
    }

    @Test
    void resolutionUsesBusinessContextAndExplicitCatalogHintWithoutReadingProductName() {
        var bakery = readyRepository(List.of(ActivityCode.PADARIA),
                List.of(OperatingMode.PRODUCES_GOODS, OperatingMode.RESELLS_GOODS, OperatingMode.BUYS_INPUTS));
        var market = readyRepository(List.of(ActivityCode.MERCADINHO),
                List.of(OperatingMode.RESELLS_GOODS, OperatingMode.BUYS_INPUTS));
        var bakeryResolver = new ResolveItemPurpose(AUTHORIZATION, bakery);

        var bakeryResult = bakeryResolver.execute(USER, BUSINESS, null, "FARINHA DE TRIGO",
                UsageContext.of("PURCHASE"), List.of(new ItemPurposeHint(
                        ItemPurpose.PRODUCTION, "CATALOG", "catalog category indicates a production input")), "NFE");
        var marketResult = new ResolveItemPurpose(AUTHORIZATION, market).execute(USER, BUSINESS, null,
                "FARINHA DE TRIGO", UsageContext.of("PURCHASE"), List.of(), "NFE");

        assertThat(bakeryResult.purpose()).isEqualTo(ItemPurpose.PRODUCTION);
        assertThat(bakeryResult.authority()).isEqualTo(ItemPurposeAuthority.SYSTEM_SUGGESTED);
        assertThat(bakeryResult.evidence()).extracting(ItemPurposeResolutionEvidence::signal)
                .contains("USAGE_CONTEXT", "ITEM_HINT", "BUSINESS_ACTIVITY", "OPERATING_MODE");
        assertThat(marketResult.purpose()).isEqualTo(ItemPurpose.RESALE);
        assertThat(marketResult.authority()).isEqualTo(ItemPurposeAuthority.SYSTEM_SUGGESTED);
    }

    @Test
    void salonItemRemainsAmbiguousUntilUsageContextOrHintDisambiguatesIt() {
        var repository = readyRepository(List.of(ActivityCode.SALAO_BELEZA),
                List.of(OperatingMode.PROVIDES_SERVICES, OperatingMode.RESELLS_GOODS, OperatingMode.BUYS_INPUTS));
        var resolver = new ResolveItemPurpose(AUTHORIZATION, repository);

        var ambiguous = resolver.execute(USER, BUSINESS, null, "SHAMPOO",
                UsageContext.of("PURCHASE"), List.of(), "NFE");
        var service = resolver.execute(USER, BUSINESS, null, "SHAMPOO",
                UsageContext.of("SERVICE_CONSUMPTION"), List.of(), "SERVICE");
        var sale = resolver.execute(USER, BUSINESS, null, "SHAMPOO",
                UsageContext.of("DIRECT_SALE"), List.of(), "SALE");

        assertThat(ambiguous.purpose()).isEqualTo(ItemPurpose.UNKNOWN);
        assertThat(ambiguous.needsConfirmation()).isTrue();
        assertThat(ambiguous.suggestions()).containsExactly(ItemPurpose.SERVICE_INPUT, ItemPurpose.RESALE);
        assertThat(service.purpose()).isEqualTo(ItemPurpose.SERVICE_INPUT);
        assertThat(sale.purpose()).isEqualTo(ItemPurpose.RESALE);
    }

    @Test
    void productNameAloneDoesNotCreateAUniversalPurposeRule() {
        var repository = readyRepository(List.of(ActivityCode.PADARIA),
                List.of(OperatingMode.PRODUCES_GOODS, OperatingMode.RESELLS_GOODS));
        var resolver = new ResolveItemPurpose(AUTHORIZATION, repository);

        var flour = resolver.execute(USER, BUSINESS, null, "FARINHA DE TRIGO",
                UsageContext.of("PURCHASE"), List.of(), "NFE");
        var shampoo = resolver.execute(USER, BUSINESS, null, "SHAMPOO",
                UsageContext.of("PURCHASE"), List.of(), "NFE");

        assertThat(flour.purpose()).isEqualTo(ItemPurpose.UNKNOWN);
        assertThat(shampoo.purpose()).isEqualTo(ItemPurpose.UNKNOWN);
        assertThat(flour.suggestions()).containsExactlyElementsOf(shampoo.suggestions());
    }

    @Test
    void confirmedHistoryOutranksProductAndCanonicalAutomaticHistory() {
        var repository = readyRepository();
        var product = UUID.randomUUID();
        var context = UsageContext.of("PURCHASE");
        repository.upsertAutomaticPurpose(purpose(product, context, ItemPurpose.PRODUCTION,
                ItemPurposeSource.LEARNED, "SYSTEM", "learned for this product"));
        repository.upsertAutomaticPurpose(purpose(BUSINESS, "FARINHA DE TRIGO", context,
                ItemPurpose.RESALE, ItemPurposeSource.SYSTEM_SUGGESTED));
        new ConfirmItemPurpose(AUTHORIZATION, repository, CLOCK).execute(USER, BUSINESS, product, context,
                ItemPurpose.SERVICE_INPUT, "confirmed for this context");

        var result = new ResolveItemPurpose(AUTHORIZATION, repository).execute(USER, BUSINESS, product,
                "FARINHA DE TRIGO", context, List.of(), "NFE");

        assertThat(result.purpose()).isEqualTo(ItemPurpose.SERVICE_INPUT);
        assertThat(result.authority()).isEqualTo(ItemPurposeAuthority.USER_CONFIRMED);
        assertThat(result.needsConfirmation()).isFalse();
        assertThat(result.evidence()).extracting(ItemPurposeResolutionEvidence::signal)
                .containsExactly("HISTORY");
    }

    @Test
    void learnedHistoryIsReusedWithLearnedAuthorityAndStillAllowsConfirmation() {
        var repository = readyRepository(List.of(ActivityCode.MERCADINHO),
                List.of(OperatingMode.RESELLS_GOODS));
        repository.upsertAutomaticPurpose(purpose(BUSINESS, "CAFE", UsageContext.of("PURCHASE"),
                ItemPurpose.RESALE, ItemPurposeSource.LEARNED));

        var result = new ResolveItemPurpose(AUTHORIZATION, repository).execute(USER, BUSINESS, null,
                "café", UsageContext.of("PURCHASE"), List.of(), "NFE");

        assertThat(result.purpose()).isEqualTo(ItemPurpose.RESALE);
        assertThat(result.authority()).isEqualTo(ItemPurposeAuthority.LEARNED);
        assertThat(result.needsConfirmation()).isTrue();
        assertThat(result.evidence()).extracting(ItemPurposeResolutionEvidence::signal)
                .containsExactly("HISTORY");
    }

    @Test
    void conflictingSemanticHintsKeepTheDecisionUnknown() {
        var repository = readyRepository(List.of(ActivityCode.PADARIA),
                List.of(OperatingMode.PRODUCES_GOODS, OperatingMode.RESELLS_GOODS));

        var result = new ResolveItemPurpose(AUTHORIZATION, repository).execute(USER, BUSINESS, null,
                "item", UsageContext.of("PURCHASE"), List.of(
                        new ItemPurposeHint(ItemPurpose.PRODUCTION, "CATALOG_A", "production category"),
                        new ItemPurposeHint(ItemPurpose.RESALE, "CATALOG_B", "retail category")), "NFE");

        assertThat(result.purpose()).isEqualTo(ItemPurpose.UNKNOWN);
        assertThat(result.needsConfirmation()).isTrue();
        assertThat(result.suggestions()).containsExactly(ItemPurpose.PRODUCTION, ItemPurpose.RESALE);
    }

    @Test
    void purposeCannotBeResolvedBeforeBusinessUnderstandingIsReady() {
        var repository = new InMemoryRepository();
        assertThatThrownBy(() -> new ResolveItemPurpose(AUTHORIZATION, repository)
                .execute(USER, BUSINESS, null, "AÇÚCAR 1KG", "MANUAL"))
                .isInstanceOf(com.tino.backend.businessunderstanding.application.exception.BusinessNotReadyException.class);
    }

    @Test
    void documentedBusinessScenariosAreRepresentableWithoutSingleBusinessType() {
        var docesESonhos = ready(List.of(ActivityCode.CONFEITARIA, ActivityCode.ENCOMENDAS),
                List.of(OperatingMode.PRODUCES_GOODS, OperatingMode.BUYS_INPUTS, OperatingMode.RESELLS_GOODS));
        var mixedRetail = ready(List.of(ActivityCode.MERCADINHO, ActivityCode.ACOUGUE, ActivityCode.VERDUREIRA),
                List.of(OperatingMode.RESELLS_GOODS, OperatingMode.PRODUCES_GOODS, OperatingMode.BUYS_INPUTS));
        var salon = ready(List.of(ActivityCode.SALAO_BELEZA),
                List.of(OperatingMode.PROVIDES_SERVICES, OperatingMode.RESELLS_GOODS, OperatingMode.BUYS_INPUTS));
        var workshop = ready(List.of(ActivityCode.OFICINA),
                List.of(OperatingMode.PROVIDES_SERVICES, OperatingMode.RESELLS_GOODS, OperatingMode.BUYS_INPUTS));

        assertThat(List.of(docesESonhos, mixedRetail, salon, workshop))
                .allMatch(snapshot -> snapshot.status().name().equals("READY"));
        assertThat(docesESonhos.activities()).extracting(BusinessActivity::code)
                .containsExactly(ActivityCode.CONFEITARIA, ActivityCode.ENCOMENDAS);
        assertThat(mixedRetail.activities()).extracting(BusinessActivity::code)
                .containsExactly(ActivityCode.MERCADINHO, ActivityCode.ACOUGUE, ActivityCode.VERDUREIRA);
        assertThat(salon.operatingModes()).extracting(BusinessOperatingMode::mode)
                .containsExactly(OperatingMode.PROVIDES_SERVICES, OperatingMode.RESELLS_GOODS,
                        OperatingMode.BUYS_INPUTS);
        assertThat(workshop.operatingModes()).extracting(BusinessOperatingMode::mode)
                .containsExactly(OperatingMode.PROVIDES_SERVICES, OperatingMode.RESELLS_GOODS,
                        OperatingMode.BUYS_INPUTS);
    }

    private static BusinessUnderstandingSnapshot ready(List<ActivityCode> activities, List<OperatingMode> modes) {
        return new BusinessUnderstandingSnapshot(
                activities.stream().map(code -> new BusinessActivity(code, null)).toList(),
                modes.stream().map(BusinessOperatingMode::declared).toList());
    }

    private static InMemoryRepository readyRepository() {
        return readyRepository(List.of(ActivityCode.CONFEITARIA),
                List.of(OperatingMode.PRODUCES_GOODS, OperatingMode.RESELLS_GOODS,
                        OperatingMode.PROVIDES_SERVICES));
    }

    private static InMemoryRepository readyRepository(List<ActivityCode> activities, List<OperatingMode> modes) {
        var repository = new InMemoryRepository();
        new ReplaceBusinessActivities(AUTHORIZATION, repository, CLOCK).execute(USER, BUSINESS,
                activities.stream().map(code -> new BusinessActivity(code, null)).toList());
        new ReplaceOperatingModes(AUTHORIZATION, repository, CLOCK).execute(USER, BUSINESS,
                modes);
        return repository;
    }

    private static BusinessItemPurpose purpose(UUID productId, UsageContext context, ItemPurpose purpose,
            ItemPurposeSource source, String classifier, String reason) {
        return new BusinessItemPurpose(UUID.randomUUID(), BUSINESS, productId, null, context, purpose, source,
                new java.math.BigDecimal("0.60"), 1, classifier, reason, CLOCK.instant(), CLOCK.instant(),
                CLOCK.instant(), CLOCK.instant(), CLOCK.instant());
    }

    private static BusinessItemPurpose purpose(BusinessId businessId, String key, UsageContext context,
            ItemPurpose purpose, ItemPurposeSource source) {
        return new BusinessItemPurpose(UUID.randomUUID(), businessId, null, key, context, purpose, source,
                new java.math.BigDecimal("0.60"), 1, "SYSTEM", "Observed evidence", CLOCK.instant(),
                CLOCK.instant(), CLOCK.instant(), CLOCK.instant(), CLOCK.instant());
    }

    private static final class InMemoryRepository implements BusinessUnderstandingRepository {
        private final List<BusinessActivity> activities = new ArrayList<>();
        private final List<BusinessOperatingMode> modes = new ArrayList<>();
        private final List<BusinessItemPurpose> purposes = new ArrayList<>();

        @Override public List<BusinessActivity> findActivities(BusinessId businessId) { return List.copyOf(activities); }
        @Override public List<BusinessOperatingMode> findOperatingModes(BusinessId businessId) { return List.copyOf(modes); }

        @Override public void replaceActivities(BusinessId businessId, List<BusinessActivity> values, Instant now) {
            activities.clear(); activities.addAll(values);
        }

        @Override public void replaceOperatingModes(BusinessId businessId, List<BusinessOperatingMode> values, Instant now) {
            modes.clear(); modes.addAll(values);
        }

        @Override public Optional<BusinessItemPurpose> findPurposeByProduct(BusinessId businessId, UUID productId,
                UsageContext context) {
            return purposes.stream().filter(item -> businessId.equals(item.businessId())
                    && productId.equals(item.productId()) && context.equals(item.usageContext())).findFirst();
        }

        @Override public Optional<BusinessItemPurpose> findPurposeByCanonicalKey(BusinessId businessId, String key,
                UsageContext context) {
            return purposes.stream().filter(item -> businessId.equals(item.businessId())
                    && key.equals(item.canonicalItemKey()) && context.equals(item.usageContext())).findFirst();
        }

        @Override public void upsertAutomaticPurpose(BusinessItemPurpose purpose) {
            var current = find(purpose);
            if (current.isPresent()
                    && current.get().source().authority().rank() > purpose.source().authority().rank()) {
                return;
            }
            save(purpose, current.orElse(null));
        }

        @Override public void upsertConfirmedPurpose(BusinessItemPurpose purpose) {
            save(purpose, find(purpose).orElse(null));
        }

        private Optional<BusinessItemPurpose> find(BusinessItemPurpose purpose) {
            return purpose.productId() == null
                    ? findPurposeByCanonicalKey(purpose.businessId(), purpose.canonicalItemKey(), purpose.usageContext())
                    : findPurposeByProduct(purpose.businessId(), purpose.productId(), purpose.usageContext());
        }

        private void save(BusinessItemPurpose purpose, BusinessItemPurpose current) {
            purposes.removeIf(item -> sameIdentity(item, purpose));
            if (current == null) {
                purposes.add(purpose);
                return;
            }
            purposes.add(new BusinessItemPurpose(purpose.id(), purpose.businessId(), purpose.productId(),
                    purpose.canonicalItemKey(), purpose.usageContext(), purpose.purpose(), purpose.source(),
                    purpose.confidence(), current.evidenceCount() + 1, purpose.evidenceClassifiedBy(),
                    purpose.evidenceReason(), purpose.evidenceAt(), current.firstObservedAt(),
                    purpose.lastObservedAt(), current.createdAt(), purpose.updatedAt()));
        }

        private static boolean sameIdentity(BusinessItemPurpose left, BusinessItemPurpose right) {
            return left.businessId().equals(right.businessId())
                    && left.usageContext().equals(right.usageContext())
                    && (left.productId() != null ? left.productId().equals(right.productId())
                            : left.canonicalItemKey().equals(right.canonicalItemKey()));
        }
    }
}
