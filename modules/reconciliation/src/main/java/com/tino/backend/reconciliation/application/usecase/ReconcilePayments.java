package com.tino.backend.reconciliation.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.payment.application.port.out.PaymentRepository;
import com.tino.backend.payment.domain.model.Payment;
import com.tino.backend.payment.domain.model.PaymentStatus;
import com.tino.backend.reconciliation.application.exception.ReconciliationConflictException;
import com.tino.backend.reconciliation.application.model.ReconciliationItemView;
import com.tino.backend.reconciliation.application.model.ReconciliationCommandResult;
import com.tino.backend.reconciliation.application.model.ReconciliationRunView;
import com.tino.backend.reconciliation.application.port.out.ReconciliationRepository;
import com.tino.backend.reconciliation.domain.model.ReconciliationClassification;
import com.tino.backend.reconciliation.domain.model.ReconciliationRunState;
import com.tino.backend.reconciliation.domain.model.SettlementEntry;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ReconcilePayments {
    private final BusinessAuthorization authorization;
    private final ReconciliationRepository reconciliations;
    private final PaymentRepository payments;
    private final UuidGenerator ids;
    private final Clock clock;

    public ReconcilePayments(BusinessAuthorization authorization, ReconciliationRepository reconciliations,
            PaymentRepository payments, UuidGenerator ids, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.reconciliations = Objects.requireNonNull(reconciliations, "reconciliations");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ReconciliationCommandResult execute(UUID userId, BusinessId businessId, String provider,
            String idempotencyKey, String fingerprint, List<SettlementEntry> entries) {
        if (!"sandbox".equals(provider) || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 200 || fingerprint == null || fingerprint.length() != 64
                || entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("invalid reconciliation request");
        }
        return authorization.execute(userId, businessId, authorizedBusiness -> {
            var previous = reconciliations.findByIdempotency(authorizedBusiness, idempotencyKey);
            if (previous.isPresent()) {
                if (!previous.orElseThrow().fingerprint().equals(fingerprint)) {
                    throw new ReconciliationConflictException();
                }
                return new ReconciliationCommandResult(view(authorizedBusiness, previous.orElseThrow()), true);
            }
            var now = Instant.now(clock);
            var run = new ReconciliationRepository.RunRecord(ids.next(), authorizedBusiness, provider,
                    idempotencyKey, fingerprint, ReconciliationRunState.PROCESSING, entries.size(), 0, 0, now, null);
            reconciliations.insertRun(run);
            int matched = 0;
            int discrepancies = 0;
            for (var entry : entries) {
                var existing = reconciliations.findItem(authorizedBusiness, run.id(), provider,
                        entry.providerEventId());
                if (existing.isPresent()) {
                    discrepancies++;
                    continue;
                }
                var payment = payments.findByProviderPaymentId(authorizedBusiness, provider,
                        entry.providerPaymentId());
                var classification = classify(payment, entry);
                if (classification == ReconciliationClassification.MATCHED) matched++;
                else discrepancies++;
                reconciliations.insertItem(new ReconciliationRepository.ItemRecord(ids.next(), authorizedBusiness,
                        run.id(), provider, entry.providerEventId(), entry.providerPaymentId(),
                        payment.map(Payment::id).orElse(null), entry.amount().value(), entry.currency(),
                        entry.status().name(), classification, entry.payloadSha256(), now));
            }
            reconciliations.completeRun(authorizedBusiness, run.id(), matched, discrepancies,
                    ReconciliationRunState.COMPLETED, Instant.now(clock));
            return new ReconciliationCommandResult(
                    view(authorizedBusiness, reconciliations.findById(authorizedBusiness, run.id()).orElseThrow()), false);
        });
    }

    private static ReconciliationClassification classify(java.util.Optional<Payment> payment,
            SettlementEntry entry) {
        if (payment.isEmpty()) return ReconciliationClassification.MISSING_PAYMENT;
        var value = payment.orElseThrow();
        if (!value.amount().value().equals(entry.amount().value())) {
            return ReconciliationClassification.AMOUNT_MISMATCH;
        }
        if (value.status() != PaymentStatus.CAPTURED || entry.status() != PaymentStatus.CAPTURED) {
            return ReconciliationClassification.STATUS_MISMATCH;
        }
        return ReconciliationClassification.MATCHED;
    }

    private ReconciliationRunView view(BusinessId businessId, ReconciliationRepository.RunRecord run) {
        var items = reconciliations.findItems(businessId, run.id()).stream()
                .map(item -> new ReconciliationItemView(item.id(), item.providerEventId(), item.providerPaymentId(),
                        item.paymentId(), item.amount(), item.currency(), item.providerStatus(),
                        item.classification().name())).toList();
        return new ReconciliationRunView(run.id(), run.businessId().value(), run.provider(), run.state().name(),
                run.totalCount(), run.matchedCount(), run.discrepancyCount(), run.createdAt(), run.completedAt(), items);
    }
}
