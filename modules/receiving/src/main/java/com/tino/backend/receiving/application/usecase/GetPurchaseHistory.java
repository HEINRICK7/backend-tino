package com.tino.backend.receiving.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.receiving.application.port.out.PurchaseHistoryRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Objects;

public final class GetPurchaseHistory {
    private final BusinessAuthorization authorization;
    private final PurchaseHistoryRepository history;
    private final Clock clock;

    public GetPurchaseHistory(BusinessAuthorization authorization, PurchaseHistoryRepository history, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.history = Objects.requireNonNull(history);
        this.clock = Objects.requireNonNull(clock);
    }

    public HistoryResult list(UUID userId, BusinessId businessId, String period) {
        var window = Window.from(period, clock);
        return authorization.execute(userId, businessId, authorized -> {
            var entries = history.findEntries(authorized, window.from(), window.to());
            return new HistoryResult(window.period(), window.from(), window.to(), entries,
                    entries.stream().mapToInt(PurchaseHistoryRepository.PurchaseHistoryEntry::itemCount).sum(),
                    entries.stream().mapToInt(PurchaseHistoryRepository.PurchaseHistoryEntry::newProductCount).sum(),
                    entries.stream().map(PurchaseHistoryRepository.PurchaseHistoryEntry::total)
                            .filter(Objects::nonNull).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        });
    }

    public PurchaseHistoryRepository.PurchaseHistoryDetail detail(UUID userId, BusinessId businessId, UUID receiptId) {
        return authorization.execute(userId, businessId, authorized -> history.findDetail(authorized, receiptId)
                .orElseThrow(() -> new IllegalArgumentException("purchase receipt not found")));
    }

    public record HistoryResult(String period, Instant from, Instant to,
            List<PurchaseHistoryRepository.PurchaseHistoryEntry> entries, int itemCount,
            int newProductCount, java.math.BigDecimal total) {}

    static record Window(String period, Instant from, Instant to) {
        static Window from(String raw, Clock clock) {
            var period = raw == null ? "MONTH" : raw.trim().toUpperCase(Locale.ROOT);
            var now = ZonedDateTime.now(clock).withZoneSameInstant(ZoneOffset.UTC);
            var start = switch (period) {
                case "WEEK" -> now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                        .toLocalDate().atStartOfDay(ZoneOffset.UTC);
                case "MONTH" -> now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC);
                case "YEAR" -> now.with(TemporalAdjusters.firstDayOfYear()).toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC);
                default -> throw new IllegalArgumentException("period must be WEEK, MONTH or YEAR");
            };
            var end = switch (period) {
                case "WEEK" -> start.plusWeeks(1);
                case "MONTH" -> start.plusMonths(1);
                case "YEAR" -> start.plusYears(1);
                default -> throw new IllegalArgumentException("period must be WEEK, MONTH or YEAR");
            };
            return new Window(period, start.toInstant(), end.toInstant());
        }
    }
}
