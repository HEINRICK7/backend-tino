package com.tino.backend.payment.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.payment.application.port.in.MerchantPaymentEvidenceReader;
import com.tino.backend.payment.application.port.out.DebtPaymentIntentRepository;
import com.tino.backend.payment.application.port.out.PaymentEvidenceRepository;
import com.tino.backend.payment.domain.model.PaymentEvidence;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Objects;
import java.util.UUID;

public final class ReadPaymentEvidenceQueue implements MerchantPaymentEvidenceReader {
    private static final int MAX_LIMIT = 50;
    private final BusinessAuthorization authorization;
    private final PaymentEvidenceRepository evidence;
    private final DebtPaymentIntentRepository intents;

    public ReadPaymentEvidenceQueue(BusinessAuthorization authorization,
            PaymentEvidenceRepository evidence, DebtPaymentIntentRepository intents) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.intents = Objects.requireNonNull(intents, "intents");
    }

    @Override
    public Result execute(UUID authenticatedUserId, BusinessId businessId, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) throw new IllegalArgumentException("limit must be between 1 and 50");
        return authorization.execute(authenticatedUserId, businessId, authorizedBusiness -> new Result(
                evidence.findForReview(authorizedBusiness, limit).stream()
                        .map(value -> item(authorizedBusiness, value))
                        .flatMap(java.util.Optional::stream)
                        .toList()));
    }

    private java.util.Optional<Item> item(BusinessId businessId, PaymentEvidence value) {
        if (value.paymentIntentId() == null) return java.util.Optional.empty();
        return intents.find(businessId, value.paymentIntentId())
                .map(intent -> new Item(value.id(), intent.id(), intent.customerId(),
                        value.amount().value().movePointRight(2).longValueExact(),
                        value.currency(), value.matchStatus().name(), value.occurredAt(), value.source()));
    }
}
