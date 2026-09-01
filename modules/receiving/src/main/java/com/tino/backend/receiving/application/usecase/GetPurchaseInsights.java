package com.tino.backend.receiving.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.receiving.application.port.out.PurchaseHistoryRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class GetPurchaseInsights {
    private final BusinessAuthorization authorization;
    private final PurchaseHistoryRepository history;
    private final Clock clock;

    public GetPurchaseInsights(BusinessAuthorization authorization, PurchaseHistoryRepository history, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.history = Objects.requireNonNull(history);
        this.clock = Objects.requireNonNull(clock);
    }

    public InsightResult execute(UUID userId, BusinessId businessId, String period) {
        var window = GetPurchaseHistory.Window.from(period, clock);
        return authorization.execute(userId, businessId, authorized -> {
            var currentEntries = history.findEntries(authorized, window.from(), window.to());
            var previousFrom = switch (window.period()) {
                case "WEEK" -> window.from().minus(java.time.Duration.ofDays(7));
                case "MONTH" -> window.from().atZone(java.time.ZoneOffset.UTC).minusMonths(1).toInstant();
                case "YEAR" -> window.from().atZone(java.time.ZoneOffset.UTC).minusYears(1).toInstant();
                default -> throw new IllegalArgumentException("period must be WEEK, MONTH or YEAR");
            };
            var previousEntries = history.findEntries(authorized, previousFrom, window.from());
            var currentFacts = history.findPriceFacts(authorized, window.from(), window.to());
            var previousFacts = history.findPriceFacts(authorized, previousFrom, window.from());
            var insights = new ArrayList<Insight>();
            addSpendComparison(insights, currentEntries, previousEntries);
            addCostChanges(insights, currentFacts, previousFacts);
            addMarginImpact(insights, currentFacts, previousFacts);
            addPurchaseFrequency(insights, currentFacts);
            return new InsightResult(window.period(), insights);
        });
    }

    private static void addSpendComparison(List<Insight> insights,
            List<PurchaseHistoryRepository.PurchaseHistoryEntry> current,
            List<PurchaseHistoryRepository.PurchaseHistoryEntry> previous) {
        if (previous.isEmpty()) return;
        var currentTotal = total(current);
        var previousTotal = total(previous);
        if (currentTotal.compareTo(previousTotal) == 0) return;
        var evidence = new ArrayList<UUID>();
        current.stream().map(PurchaseHistoryRepository.PurchaseHistoryEntry::receiptId).forEach(evidence::add);
        previous.stream().map(PurchaseHistoryRepository.PurchaseHistoryEntry::receiptId).forEach(evidence::add);
        var direction = currentTotal.compareTo(previousTotal) > 0 ? "aumentou" : "diminuiu";
        insights.add(new Insight("SPEND_COMPARISON",
                "O gasto em compras " + direction + " de R$ " + previousTotal.toPlainString()
                        + " para R$ " + currentTotal.toPlainString() + ".", evidence));
    }

    private static void addCostChanges(List<Insight> insights,
            List<PurchaseHistoryRepository.PurchasePriceFact> current,
            List<PurchaseHistoryRepository.PurchasePriceFact> previous) {
        var previousByProduct = previous.stream().collect(Collectors.groupingBy(
                PurchaseHistoryRepository.PurchasePriceFact::productId,
                Collectors.maxBy(Comparator.comparing(PurchaseHistoryRepository.PurchasePriceFact::observedAt))));
        var currentByProduct = current.stream().collect(Collectors.groupingBy(
                PurchaseHistoryRepository.PurchasePriceFact::productId,
                Collectors.maxBy(Comparator.comparing(PurchaseHistoryRepository.PurchasePriceFact::observedAt))));
        currentByProduct.forEach((productId, currentOptional) -> {
            var previousOptional = previousByProduct.get(productId);
            if (currentOptional.isEmpty() || previousOptional == null || previousOptional.isEmpty()) return;
            var now = currentOptional.get();
            var before = previousOptional.get();
            if (now.unitPrice().compareTo(before.unitPrice()) == 0) return;
            var direction = now.unitPrice().compareTo(before.unitPrice()) > 0 ? "subiu" : "caiu";
            insights.add(new Insight("COST_CHANGE", "O custo de " + now.productName() + " " + direction
                    + " de R$ " + before.unitPrice().toPlainString() + " para R$ " + now.unitPrice().toPlainString() + ".",
                    List.of(before.observationId(), before.receiptId(), now.observationId(), now.receiptId())));
        });
    }

    private static void addPurchaseFrequency(List<Insight> insights,
            List<PurchaseHistoryRepository.PurchasePriceFact> current) {
        current.stream().collect(Collectors.groupingBy(PurchaseHistoryRepository.PurchasePriceFact::productId))
                .forEach((productId, facts) -> {
                    if (facts.size() < 3) return;
                    var evidence = facts.stream().flatMap(fact -> java.util.stream.Stream.of(
                                    fact.observationId(), fact.receiptId())).toList();
                    insights.add(new Insight("PURCHASE_FREQUENCY", "Você comprou " + facts.get(0).productName()
                            + " " + facts.size() + " vezes neste período.", evidence));
                });
    }

    private static void addMarginImpact(List<Insight> insights,
            List<PurchaseHistoryRepository.PurchasePriceFact> current,
            List<PurchaseHistoryRepository.PurchasePriceFact> previous) {
        var previousByProduct = previous.stream().collect(Collectors.groupingBy(
                PurchaseHistoryRepository.PurchasePriceFact::productId,
                Collectors.maxBy(Comparator.comparing(PurchaseHistoryRepository.PurchasePriceFact::observedAt))));
        var currentByProduct = current.stream().collect(Collectors.groupingBy(
                PurchaseHistoryRepository.PurchasePriceFact::productId,
                Collectors.maxBy(Comparator.comparing(PurchaseHistoryRepository.PurchasePriceFact::observedAt))));
        currentByProduct.forEach((productId, currentOptional) -> {
            var previousOptional = previousByProduct.get(productId);
            if (currentOptional.isEmpty() || previousOptional == null || previousOptional.isEmpty()) return;
            var now = currentOptional.get();
            var before = previousOptional.get();
            if (now.salePrice() == null || now.unitPrice().compareTo(before.unitPrice()) == 0) return;
            var previousMargin = now.salePrice().subtract(before.unitPrice());
            var currentMargin = now.salePrice().subtract(now.unitPrice());
            var direction = currentMargin.compareTo(previousMargin) < 0 ? "caiu" : "subiu";
            insights.add(new Insight("MARGIN_IMPACT",
                    "O custo de " + now.productName() + " mudou de R$ " + before.unitPrice().toPlainString()
                            + " para R$ " + now.unitPrice().toPlainString() + ". Com o preço de venda atual de R$ "
                            + now.salePrice().toPlainString() + ", a margem bruta estimada " + direction + " de R$ "
                            + previousMargin.toPlainString() + " para R$ " + currentMargin.toPlainString() + ".",
                    List.of(before.observationId(), before.receiptId(), now.observationId(), now.receiptId(), productId)));
        });
    }

    private static BigDecimal total(List<PurchaseHistoryRepository.PurchaseHistoryEntry> entries) {
        return entries.stream().map(PurchaseHistoryRepository.PurchaseHistoryEntry::total)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record InsightResult(String period, List<Insight> insights) {}
    public record Insight(String type, String message, List<UUID> evidenceIds) {}
}
