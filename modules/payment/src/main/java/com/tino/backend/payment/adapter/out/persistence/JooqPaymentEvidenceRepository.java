package com.tino.backend.payment.adapter.out.persistence;

import com.tino.backend.payment.application.port.out.PaymentEvidenceRepository;
import com.tino.backend.payment.domain.model.PaymentAmount;
import com.tino.backend.payment.domain.model.PaymentEvidence;
import com.tino.backend.payment.domain.model.PaymentEvidenceMatchStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class JooqPaymentEvidenceRepository implements PaymentEvidenceRepository {
    private final DSLContext dsl;

    public JooqPaymentEvidenceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotency(BusinessId businessId, String key) {
        var row = dsl.fetchOne("""
                SELECT request_fingerprint, evidence_id
                  FROM public.customer_payment_evidence_idempotency_keys
                 WHERE business_id = ? AND idempotency_key = ?
                """, businessId.value(), key);
        return row == null ? Optional.empty() : Optional.of(new IdempotencyRecord(
                row.get("request_fingerprint", String.class), row.get("evidence_id", UUID.class)));
    }

    @Override
    public boolean claimIdempotency(BusinessId businessId, String key, String fingerprint,
            UUID evidenceId, Instant createdAt) {
        return dsl.execute("""
                INSERT INTO public.customer_payment_evidence_idempotency_keys
                    (business_id, idempotency_key, request_fingerprint, evidence_id, created_at)
                VALUES (?, ?, ?, ?, CAST(? AS TIMESTAMPTZ))
                ON CONFLICT (business_id, idempotency_key) DO NOTHING
                """, businessId.value(), key, fingerprint, evidenceId, time(createdAt)) == 1;
    }

    @Override
    public Optional<PaymentEvidence> find(BusinessId businessId, UUID evidenceId) {
        var row = dsl.fetchOne("""
                SELECT id, business_id, payment_intent_id, amount, currency, pix_txid, source,
                       source_package, evidence_hash, occurred_at, received_at, match_status
                  FROM public.customer_payment_evidence
                 WHERE business_id = ? AND id = ?
                """, businessId.value(), evidenceId);
        return row == null ? Optional.empty() : Optional.of(toEvidence(row));
    }

    @Override
    public Optional<PaymentEvidence> findByHash(BusinessId businessId, String evidenceHash) {
        var row = dsl.fetchOne("""
                SELECT id, business_id, payment_intent_id, amount, currency, pix_txid, source,
                       source_package, evidence_hash, occurred_at, received_at, match_status
                  FROM public.customer_payment_evidence
                 WHERE business_id = ? AND evidence_hash = ?
                """, businessId.value(), evidenceHash);
        return row == null ? Optional.empty() : Optional.of(toEvidence(row));
    }

    @Override
    public Optional<PaymentEvidence> findMatchedByIntent(BusinessId businessId, UUID paymentIntentId) {
        var row = dsl.fetchOne("""
                SELECT id, business_id, payment_intent_id, amount, currency, pix_txid, source,
                       source_package, evidence_hash, occurred_at, received_at, match_status
                  FROM public.customer_payment_evidence
                 WHERE business_id = ? AND payment_intent_id = ? AND match_status = 'MATCHED'
                 ORDER BY occurred_at DESC, id DESC
                 LIMIT 1
                """, businessId.value(), paymentIntentId);
        return row == null ? Optional.empty() : Optional.of(toEvidence(row));
    }

    @Override
    public List<PaymentEvidence> findForReview(BusinessId businessId, int limit) {
        var rows = dsl.fetch("""
                SELECT e.id, e.business_id, e.payment_intent_id, e.amount, e.currency, e.pix_txid,
                       e.source, e.source_package, e.evidence_hash, e.occurred_at, e.received_at,
                       e.match_status
                  FROM public.customer_payment_evidence e
                  JOIN public.customer_payment_intents i
                    ON i.business_id = e.business_id AND i.id = e.payment_intent_id
                 WHERE e.business_id = ? AND e.match_status = 'MATCHED'
                   AND i.status IN ('EVIDENCE_FOUND', 'AWAITING_MERCHANT_CONFIRMATION')
                 ORDER BY e.occurred_at ASC, e.id ASC
                 LIMIT ?
                """, businessId.value(), limit);
        var values = new ArrayList<PaymentEvidence>(rows.size());
        for (Record row : rows) values.add(toEvidence(row));
        return values;
    }

    @Override
    public void insert(PaymentEvidence evidence) {
        dsl.execute("""
                INSERT INTO public.customer_payment_evidence
                    (id, business_id, payment_intent_id, amount, currency, pix_txid, source,
                     source_package, evidence_hash, occurred_at, received_at, match_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS TIMESTAMPTZ),
                        CAST(? AS TIMESTAMPTZ), ?)
                """, evidence.id(), evidence.businessId().value(), evidence.paymentIntentId(),
                evidence.amountValue(), evidence.currency(), evidence.pixTxid(), evidence.source(),
                evidence.sourcePackage(), evidence.evidenceHash(), time(evidence.occurredAt()),
                time(evidence.receivedAt()), evidence.matchStatus().name());
    }

    private static PaymentEvidence toEvidence(Record row) {
        return new PaymentEvidence(row.get("id", UUID.class),
                new BusinessId(row.get("business_id", UUID.class)),
                row.get("payment_intent_id", UUID.class),
                new PaymentAmount(row.get("amount", BigDecimal.class)),
                row.get("currency", String.class).trim(), row.get("pix_txid", String.class),
                row.get("source", String.class), row.get("source_package", String.class),
                row.get("evidence_hash", String.class),
                row.get("occurred_at", OffsetDateTime.class).toInstant(),
                row.get("received_at", OffsetDateTime.class).toInstant(),
                PaymentEvidenceMatchStatus.valueOf(row.get("match_status", String.class)));
    }

    private static OffsetDateTime time(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
