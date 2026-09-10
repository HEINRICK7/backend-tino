package com.tino.backend.payment.adapter.out.persistence;

import com.tino.backend.payment.application.port.out.DebtPaymentIntentRepository;
import com.tino.backend.payment.domain.model.DebtPaymentIntent;
import com.tino.backend.payment.domain.model.DebtPaymentIntentStatus;
import com.tino.backend.payment.domain.model.PaymentAmount;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqDebtPaymentIntentRepository implements DebtPaymentIntentRepository {
    private final DSLContext dsl;

    public JooqDebtPaymentIntentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotency(BusinessId businessId, String key) {
        var row = dsl.fetchOne("""
                SELECT request_fingerprint, payment_intent_id
                  FROM public.customer_payment_intent_idempotency_keys
                 WHERE business_id = ? AND idempotency_key = ?
                """, businessId.value(), key);
        return row == null ? Optional.empty() : Optional.of(new IdempotencyRecord(
                row.get("request_fingerprint", String.class), row.get("payment_intent_id", UUID.class)));
    }

    @Override
    public boolean claimIdempotency(BusinessId businessId, String key, String fingerprint,
            UUID paymentIntentId, Instant createdAt) {
        return dsl.execute("""
                INSERT INTO public.customer_payment_intent_idempotency_keys
                    (business_id, idempotency_key, request_fingerprint, payment_intent_id, created_at)
                VALUES (?, ?, ?, ?, CAST(? AS TIMESTAMPTZ))
                ON CONFLICT (business_id, idempotency_key) DO NOTHING
                """, businessId.value(), key, fingerprint, paymentIntentId, time(createdAt)) == 1;
    }

    @Override
    public void insert(DebtPaymentIntent intent) {
        dsl.execute("""
                INSERT INTO public.customer_payment_intents
                    (id, business_id, customer_id, amount, currency, pix_txid, status,
                     created_at, expires_at, updated_at)
                VALUES (?, ?, ?, ?, 'BRL', ?, ?, CAST(? AS TIMESTAMPTZ),
                        CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))
                """, intent.id(), intent.businessId().value(), intent.customerId(),
                intent.amount().value(), intent.pixTxid(), intent.status().name(),
                time(intent.createdAt()), time(intent.expiresAt()), time(intent.updatedAt()));
    }

    @Override
    public Optional<DebtPaymentIntent> find(BusinessId businessId, UUID paymentIntentId) {
        return findById(businessId, paymentIntentId, false);
    }

    @Override
    public Optional<DebtPaymentIntent> findForUpdate(BusinessId businessId, UUID paymentIntentId) {
        return findById(businessId, paymentIntentId, true);
    }

    @Override
    public Optional<DebtPaymentIntent> findByTxid(BusinessId businessId, String pixTxid) {
        var row = dsl.fetchOne("""
                SELECT id, business_id, customer_id, amount, pix_txid, status,
                       created_at, expires_at, updated_at
                  FROM public.customer_payment_intents
                 WHERE business_id = ? AND pix_txid = ?
                """, businessId.value(), pixTxid);
        return row == null ? Optional.empty() : Optional.of(toIntent(row));
    }

    @Override
    public Optional<DebtPaymentIntent> findSingleOpenByAmount(BusinessId businessId,
            BigDecimal amount, Instant occurredAt) {
        var rows = dsl.fetch("""
                SELECT id, business_id, customer_id, amount, pix_txid, status,
                       created_at, expires_at, updated_at
                  FROM public.customer_payment_intents
                 WHERE business_id = ? AND amount = ? AND status = 'PENDING'
                   AND created_at <= CAST(? AS TIMESTAMPTZ)
                   AND expires_at >= CAST(? AS TIMESTAMPTZ)
                 ORDER BY created_at DESC, id DESC
                 LIMIT 2
                """, businessId.value(), amount, time(occurredAt), time(occurredAt));
        return rows.size() == 1 ? Optional.of(toIntent(rows.get(0))) : Optional.empty();
    }

    @Override
    public Optional<DebtPaymentIntent> findOpenByCustomerAndAmount(BusinessId businessId,
            UUID customerId, BigDecimal amount, Instant now) {
        var row = dsl.fetchOne("""
                SELECT id, business_id, customer_id, amount, pix_txid, status,
                       created_at, expires_at, updated_at
                  FROM public.customer_payment_intents
                 WHERE business_id = ? AND customer_id = ? AND amount = ?
                   AND status IN ('PENDING', 'EVIDENCE_FOUND', 'AWAITING_MERCHANT_CONFIRMATION')
                   AND expires_at > CAST(? AS TIMESTAMPTZ)
                 ORDER BY created_at DESC, id DESC
                 LIMIT 1
                """, businessId.value(), customerId, amount, time(now));
        return row == null ? Optional.empty() : Optional.of(toIntent(row));
    }

    @Override
    public int markEvidenceFound(BusinessId businessId, UUID paymentIntentId, Instant updatedAt) {
        return dsl.execute("""
                UPDATE public.customer_payment_intents
                   SET status = CASE WHEN status = 'PENDING' THEN 'EVIDENCE_FOUND' ELSE status END,
                       updated_at = CAST(? AS TIMESTAMPTZ)
                 WHERE business_id = ? AND id = ?
                   AND status IN ('PENDING', 'EVIDENCE_FOUND', 'AWAITING_MERCHANT_CONFIRMATION')
                """, time(updatedAt), businessId.value(), paymentIntentId);
    }

    @Override
    public int markConfirmed(BusinessId businessId, UUID paymentIntentId, UUID creditEntryId,
            Instant updatedAt) {
        return dsl.execute("""
                UPDATE public.customer_payment_intents
                   SET status = 'CONFIRMED', confirmed_credit_entry_id = ?,
                       updated_at = CAST(? AS TIMESTAMPTZ)
                 WHERE business_id = ? AND id = ?
                   AND status IN ('EVIDENCE_FOUND', 'AWAITING_MERCHANT_CONFIRMATION')
                   AND confirmed_credit_entry_id IS NULL
                """, creditEntryId, time(updatedAt), businessId.value(), paymentIntentId);
    }

    @Override
    public Optional<UUID> findConfirmedCreditEntryId(BusinessId businessId, UUID paymentIntentId) {
        var row = dsl.fetchOne("""
                SELECT confirmed_credit_entry_id
                  FROM public.customer_payment_intents
                 WHERE business_id = ? AND id = ?
                """, businessId.value(), paymentIntentId);
        return row == null ? Optional.empty() : Optional.ofNullable(row.get("confirmed_credit_entry_id", UUID.class));
    }

    @Override
    public Optional<ConfirmationIdempotencyRecord> findConfirmationIdempotency(BusinessId businessId,
            String key) {
        var row = dsl.fetchOne("""
                SELECT request_fingerprint, payment_intent_id, credit_entry_id
                  FROM public.customer_payment_intent_confirmation_idempotency_keys
                 WHERE business_id = ? AND idempotency_key = ?
                """, businessId.value(), key);
        return row == null ? Optional.empty() : Optional.of(new ConfirmationIdempotencyRecord(
                row.get("request_fingerprint", String.class), row.get("payment_intent_id", UUID.class),
                row.get("credit_entry_id", UUID.class)));
    }

    @Override
    public boolean claimConfirmationIdempotency(BusinessId businessId, String key, String fingerprint,
            UUID paymentIntentId, UUID creditEntryId, Instant createdAt) {
        return dsl.execute("""
                INSERT INTO public.customer_payment_intent_confirmation_idempotency_keys
                    (business_id, idempotency_key, request_fingerprint, payment_intent_id,
                     credit_entry_id, created_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS TIMESTAMPTZ))
                ON CONFLICT (business_id, idempotency_key) DO NOTHING
                """, businessId.value(), key, fingerprint, paymentIntentId, creditEntryId, time(createdAt)) == 1;
    }

    private Optional<DebtPaymentIntent> findById(BusinessId businessId, UUID paymentIntentId, boolean lock) {
        var row = dsl.fetchOne("""
                SELECT id, business_id, customer_id, amount, pix_txid, status,
                       created_at, expires_at, updated_at
                  FROM public.customer_payment_intents
                 WHERE business_id = ? AND id = ?
                """ + (lock ? " FOR UPDATE" : ""), businessId.value(), paymentIntentId);
        return row == null ? Optional.empty() : Optional.of(toIntent(row));
    }

    private static DebtPaymentIntent toIntent(org.jooq.Record row) {
        return new DebtPaymentIntent(row.get("id", UUID.class),
                new BusinessId(row.get("business_id", UUID.class)), row.get("customer_id", UUID.class),
                new PaymentAmount(row.get("amount", BigDecimal.class)),
                row.get("pix_txid", String.class),
                DebtPaymentIntentStatus.valueOf(row.get("status", String.class)),
                instant(row.get("created_at", OffsetDateTime.class)),
                instant(row.get("expires_at", OffsetDateTime.class)),
                instant(row.get("updated_at", OffsetDateTime.class)));
    }

    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private static Instant instant(OffsetDateTime value) { return value.toInstant(); }
}
