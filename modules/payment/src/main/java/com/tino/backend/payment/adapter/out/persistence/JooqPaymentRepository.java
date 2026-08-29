package com.tino.backend.payment.adapter.out.persistence;

import com.tino.backend.payment.application.port.out.PaymentPersistenceException;
import com.tino.backend.payment.application.port.out.PaymentRepository;
import com.tino.backend.payment.domain.model.Payment;
import com.tino.backend.payment.domain.model.PaymentAmount;
import com.tino.backend.payment.domain.model.PaymentMethod;
import com.tino.backend.payment.domain.model.PaymentStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqPaymentRepository implements PaymentRepository {
    private static final Table<?> CUSTOMERS = table("customers");
    private static final Table<?> PAYMENTS = table("payments");
    private static final Table<?> IDEMPOTENCY = table("payment_idempotency_keys");
    private static final Table<?> EVENTS = table("payment_provider_events");
    private static final Table<?> OUTBOX = table("payment_outbox");
    private static final Field<UUID> ID = field("id", UUID.class);
    private static final Field<UUID> BUSINESS_ID = field("business_id", UUID.class);
    private static final Field<UUID> CUSTOMER_ID = field("customer_id", UUID.class);
    private static final Field<UUID> PAYMENT_ID = field("payment_id", UUID.class);
    private static final Field<UUID> ACCOUNT_ID = field("account_id", UUID.class);
    private static final Field<BigDecimal> AMOUNT = field("amount", BigDecimal.class);
    private static final Field<String> CURRENCY = field("currency", String.class);
    private static final Field<String> METHOD = field("method", String.class);
    private static final Field<String> EXTERNAL_REFERENCE = field("external_reference", String.class);
    private static final Field<String> PROVIDER = field("provider", String.class);
    private static final Field<String> PROVIDER_PAYMENT_ID = field("provider_payment_id", String.class);
    private static final Field<String> STATUS = field("status", String.class);
    private static final Field<Long> VERSION = field("version", Long.class);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);
    private static final Field<String> IDEMPOTENCY_KEY = field("idempotency_key", String.class);
    private static final Field<String> FINGERPRINT = field("request_fingerprint", String.class);
    private static final Field<String> COMMAND_TYPE = field("command_type", String.class);
    private static final Field<String> STATE = field("state", String.class);
    private static final Field<Integer> ATTEMPT_COUNT = field("attempt_count", Integer.class);
    private static final Field<OffsetDateTime> AVAILABLE_AT = field("available_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> LOCKED_AT = field("locked_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> COMPLETED_AT = field("completed_at", OffsetDateTime.class);
    private static final Field<String> LAST_ERROR = field("last_error", String.class);
    private static final Field<String> PROVIDER_EVENT_ID = field("provider_event_id", String.class);
    private static final Field<String> PAYLOAD_SHA256 = field("payload_sha256", String.class);
    private final DSLContext dsl;

    public JooqPaymentRepository(DSLContext dsl) { this.dsl = dsl; }

    @Override
    @Transactional(readOnly = true)
    public boolean customerExists(BusinessId businessId, UUID customerId) {
        try {
            return dsl.selectOne().from(CUSTOMERS)
                    .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(customerId)))
                    .fetchOptional().isPresent();
        } catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> find(BusinessId businessId, UUID paymentId) {
        try {
            return dsl.select(ID, BUSINESS_ID, CUSTOMER_ID, AMOUNT, CURRENCY, METHOD, EXTERNAL_REFERENCE,
                            PROVIDER, PROVIDER_PAYMENT_ID, STATUS, VERSION, CREATED_AT, UPDATED_AT)
                    .from(PAYMENTS).where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(paymentId)))
                    .fetchOptional().map(JooqPaymentRepository::toPayment);
        } catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findIdempotency(BusinessId businessId, String key) {
        try {
            return dsl.select(FINGERPRINT, PAYMENT_ID).from(IDEMPOTENCY)
                    .where(BUSINESS_ID.eq(businessId.value()).and(IDEMPOTENCY_KEY.eq(key)))
                    .fetchOptional().map(row -> new IdempotencyRecord(row.get(FINGERPRINT), row.get(PAYMENT_ID)));
        } catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override
    public boolean claimIdempotency(BusinessId businessId, String key, String fingerprint,
            UUID paymentId, Instant createdAt) {
        try {
            return dsl.insertInto(IDEMPOTENCY)
                    .columns(BUSINESS_ID, IDEMPOTENCY_KEY, FINGERPRINT, PAYMENT_ID, CREATED_AT)
                    .values(businessId.value(), key, fingerprint, paymentId, toDatabaseTime(createdAt))
                    .onConflict(BUSINESS_ID, IDEMPOTENCY_KEY).doNothing().execute() == 1;
        } catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override
    public void insert(Payment payment) {
        try {
            dsl.insertInto(PAYMENTS)
                    .columns(ID, BUSINESS_ID, CUSTOMER_ID, AMOUNT, CURRENCY, METHOD, EXTERNAL_REFERENCE,
                            PROVIDER, PROVIDER_PAYMENT_ID, STATUS, VERSION, CREATED_AT, UPDATED_AT)
                    .values(payment.id(), payment.businessId().value(), payment.customerId(), payment.amount().value(),
                            payment.currency(), payment.method().name(), payment.externalReference(), payment.provider(),
                            payment.providerPaymentId(), payment.status().name(), payment.version(),
                            toDatabaseTime(payment.createdAt()), toDatabaseTime(payment.updatedAt()))
                    .execute();
        } catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override
    public void enqueue(OutboxCommand command) {
        try {
            dsl.insertInto(OUTBOX).columns(ID, BUSINESS_ID, PAYMENT_ID, COMMAND_TYPE, STATE, ATTEMPT_COUNT,
                            AVAILABLE_AT, CREATED_AT)
                    .values(command.id(), command.businessId().value(), command.paymentId(), command.commandType(),
                            command.state(), command.attemptCount(), toDatabaseTime(command.availableAt()),
                            toDatabaseTime(command.createdAt())).execute();
        } catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override
    public Optional<OutboxCommand> claimOutbox(BusinessId businessId, UUID paymentId, Instant now) {
        try {
            var row = dsl.select(ID, BUSINESS_ID, PAYMENT_ID, COMMAND_TYPE, STATE, ATTEMPT_COUNT,
                            AVAILABLE_AT, CREATED_AT).from(OUTBOX)
                    .where(BUSINESS_ID.eq(businessId.value()).and(PAYMENT_ID.eq(paymentId))
                            .and(STATE.eq("PENDING")).and(AVAILABLE_AT.le(toDatabaseTime(now))))
                    .orderBy(CREATED_AT.asc()).limit(1).forUpdate().skipLocked().fetchOptional();
            if (row.isEmpty()) return Optional.empty();
            var command = toOutbox(row.orElseThrow());
            dsl.update(OUTBOX).set(STATE, "PROCESSING").set(ATTEMPT_COUNT, command.attemptCount() + 1)
                    .set(LOCKED_AT, toDatabaseTime(now)).where(ID.eq(command.id())).execute();
            return Optional.of(new OutboxCommand(command.id(), command.businessId(), command.paymentId(),
                    command.commandType(), "PROCESSING", command.attemptCount() + 1,
                    command.availableAt(), command.createdAt()));
        } catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override
    public void completeOutbox(BusinessId businessId, UUID outboxId, Instant completedAt) {
        try {
            dsl.update(OUTBOX).set(STATE, "COMPLETED").set(COMPLETED_AT, toDatabaseTime(completedAt))
                    .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(outboxId))).execute();
        } catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override
    public void failOutbox(BusinessId businessId, UUID outboxId, Instant availableAt, String error) {
        try {
            dsl.update(OUTBOX).set(STATE, "PENDING").set(AVAILABLE_AT, toDatabaseTime(availableAt))
                    .set(LOCKED_AT, (OffsetDateTime) null).set(LAST_ERROR, error)
                    .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(outboxId))).execute();
        } catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override
    @Transactional
    public Payment applyProviderEvent(BusinessId businessId, UUID paymentId, String provider,
            String providerEventId, String providerPaymentId, PaymentStatus status,
            String payloadHash, Instant createdAt) {
        try {
            var paymentCondition = BUSINESS_ID.eq(businessId.value()).and(ID.eq(paymentId));
            var existingEvent = dsl.select(PAYMENT_ID).from(EVENTS)
                    .where(PROVIDER.eq(provider).and(PROVIDER_EVENT_ID.eq(providerEventId)))
                    .fetchOptional();
            if (existingEvent.isPresent()) {
                return find(businessId, existingEvent.orElseThrow().get(PAYMENT_ID))
                        .orElseThrow(() -> new IllegalStateException("payment event target disappeared"));
            }
            var current = dsl.select(ID, BUSINESS_ID, CUSTOMER_ID, AMOUNT, CURRENCY, METHOD, EXTERNAL_REFERENCE,
                            PROVIDER, PROVIDER_PAYMENT_ID, STATUS, VERSION, CREATED_AT, UPDATED_AT)
                    .from(PAYMENTS).where(paymentCondition).forUpdate().fetchOptional()
                    .orElseThrow(() -> new IllegalArgumentException("payment not found"));
            var currentPayment = toPayment(current);
            dsl.insertInto(EVENTS).columns(ID, BUSINESS_ID, PAYMENT_ID, PROVIDER, PROVIDER_EVENT_ID,
                            PROVIDER_PAYMENT_ID, STATUS, PAYLOAD_SHA256, CREATED_AT)
                    .values(UUID.randomUUID(), businessId.value(), paymentId, provider, providerEventId,
                            providerPaymentId, status.name(), payloadHash, toDatabaseTime(createdAt)).execute();
            dsl.update(PAYMENTS).set(PROVIDER_PAYMENT_ID, providerPaymentId).set(STATUS, status.name())
                    .set(VERSION, currentPayment.version() + 1).set(UPDATED_AT, toDatabaseTime(createdAt))
                    .where(paymentCondition).execute();
            return find(businessId, paymentId).orElseThrow();
        } catch (RuntimeException exception) { throw translate(exception); }
    }

    private static Payment toPayment(Record row) {
        return new Payment(row.get(ID), new BusinessId(row.get(BUSINESS_ID)), row.get(CUSTOMER_ID),
                new PaymentAmount(row.get(AMOUNT)), row.get(CURRENCY).trim(),
                PaymentMethod.valueOf(row.get(METHOD)), row.get(EXTERNAL_REFERENCE), row.get(PROVIDER),
                row.get(PROVIDER_PAYMENT_ID), PaymentStatus.valueOf(row.get(STATUS)), row.get(VERSION),
                row.get(CREATED_AT).toInstant(), row.get(UPDATED_AT).toInstant());
    }

    private static OutboxCommand toOutbox(Record row) {
        return new OutboxCommand(row.get(ID), new BusinessId(row.get(BUSINESS_ID)), row.get(PAYMENT_ID),
                row.get(COMMAND_TYPE), row.get(STATE), row.get(ATTEMPT_COUNT),
                row.get(AVAILABLE_AT).toInstant(), row.get(CREATED_AT).toInstant());
    }

    private static OffsetDateTime toDatabaseTime(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private static PaymentPersistenceException translate(RuntimeException exception) {
        if (exception instanceof PaymentPersistenceException persistence) return persistence;
        return new PaymentPersistenceException(exception);
    }
    private static Table<?> table(String name) { return DSL.table(DSL.name("public", name)); }
    private static <T> Field<T> field(String name, Class<T> type) { return DSL.field(DSL.name(name), type); }
}
