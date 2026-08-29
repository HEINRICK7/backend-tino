package com.tino.backend.reconciliation.adapter.out.persistence;

import com.tino.backend.reconciliation.application.port.out.ReconciliationPersistenceException;
import com.tino.backend.reconciliation.application.port.out.ReconciliationRepository;
import com.tino.backend.reconciliation.domain.model.ReconciliationClassification;
import com.tino.backend.reconciliation.domain.model.ReconciliationRunState;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
public class JooqReconciliationRepository implements ReconciliationRepository {
    private static final Table<?> RUNS = table("reconciliation_runs");
    private static final Table<?> ITEMS = table("reconciliation_items");
    private static final Field<UUID> ID = field("id", UUID.class);
    private static final Field<UUID> BUSINESS_ID = field("business_id", UUID.class);
    private static final Field<UUID> RUN_ID = field("run_id", UUID.class);
    private static final Field<UUID> PAYMENT_ID = field("payment_id", UUID.class);
    private static final Field<String> PROVIDER = field("provider", String.class);
    private static final Field<String> IDEMPOTENCY_KEY = field("idempotency_key", String.class);
    private static final Field<String> FINGERPRINT = field("request_fingerprint", String.class);
    private static final Field<String> STATE = field("state", String.class);
    private static final Field<Integer> TOTAL_COUNT = field("total_count", Integer.class);
    private static final Field<Integer> MATCHED_COUNT = field("matched_count", Integer.class);
    private static final Field<Integer> DISCREPANCY_COUNT = field("discrepancy_count", Integer.class);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> COMPLETED_AT = field("completed_at", OffsetDateTime.class);
    private static final Field<String> PROVIDER_EVENT_ID = field("provider_event_id", String.class);
    private static final Field<String> PROVIDER_PAYMENT_ID = field("provider_payment_id", String.class);
    private static final Field<BigDecimal> AMOUNT = field("amount", BigDecimal.class);
    private static final Field<String> CURRENCY = field("currency", String.class);
    private static final Field<String> PROVIDER_STATUS = field("provider_status", String.class);
    private static final Field<String> CLASSIFICATION = field("classification", String.class);
    private static final Field<String> PAYLOAD_SHA256 = field("payload_sha256", String.class);
    private final DSLContext dsl;

    public JooqReconciliationRepository(DSLContext dsl) { this.dsl = dsl; }

    @Override @Transactional(readOnly = true)
    public Optional<RunRecord> findById(BusinessId businessId, UUID runId) {
        try { return dsl.select(RUNS.fields()).from(RUNS)
                .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(runId)))
                .fetchOptional().map(JooqReconciliationRepository::toRun); }
        catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override @Transactional(readOnly = true)
    public Optional<RunRecord> findByIdempotency(BusinessId businessId, String key) {
        try { return dsl.select(RUNS.fields()).from(RUNS)
                .where(BUSINESS_ID.eq(businessId.value()).and(IDEMPOTENCY_KEY.eq(key)))
                .fetchOptional().map(JooqReconciliationRepository::toRun); }
        catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override public void insertRun(RunRecord run) {
        try { dsl.insertInto(RUNS).columns(ID, BUSINESS_ID, PROVIDER, IDEMPOTENCY_KEY, FINGERPRINT, STATE,
                TOTAL_COUNT, MATCHED_COUNT, DISCREPANCY_COUNT, CREATED_AT)
                .values(run.id(), run.businessId().value(), run.provider(), run.idempotencyKey(), run.fingerprint(),
                        run.state().name(), run.totalCount(), run.matchedCount(), run.discrepancyCount(), time(run.createdAt()))
                .execute(); }
        catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override @Transactional(readOnly = true)
    public Optional<ItemRecord> findItem(BusinessId businessId, UUID runId, String provider, String eventId) {
        try { return dsl.select(ITEMS.fields()).from(ITEMS)
                .where(BUSINESS_ID.eq(businessId.value()).and(RUN_ID.eq(runId)).and(PROVIDER.eq(provider))
                        .and(PROVIDER_EVENT_ID.eq(eventId)))
                .fetchOptional().map(JooqReconciliationRepository::toItem); }
        catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override public void insertItem(ItemRecord item) {
        try { dsl.insertInto(ITEMS).columns(ID, BUSINESS_ID, RUN_ID, PROVIDER, PROVIDER_EVENT_ID,
                PROVIDER_PAYMENT_ID, PAYMENT_ID, AMOUNT, CURRENCY, PROVIDER_STATUS, CLASSIFICATION,
                PAYLOAD_SHA256, CREATED_AT)
                .values(item.id(), item.businessId().value(), item.runId(), item.provider(), item.providerEventId(),
                        item.providerPaymentId(), item.paymentId(), item.amount(), item.currency(), item.providerStatus(),
                        item.classification().name(), item.payloadHash(), time(item.createdAt())).execute(); }
        catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override public void completeRun(BusinessId businessId, UUID runId, int matched, int discrepancies,
            ReconciliationRunState state, Instant completedAt) {
        try { dsl.update(RUNS).set(STATE, state.name()).set(MATCHED_COUNT, matched)
                .set(DISCREPANCY_COUNT, discrepancies).set(COMPLETED_AT, time(completedAt))
                .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(runId))).execute(); }
        catch (RuntimeException exception) { throw translate(exception); }
    }

    @Override @Transactional(readOnly = true)
    public List<ItemRecord> findItems(BusinessId businessId, UUID runId) {
        try { return dsl.select(ITEMS.fields()).from(ITEMS)
                .where(BUSINESS_ID.eq(businessId.value()).and(RUN_ID.eq(runId))).orderBy(CREATED_AT.asc())
                .fetch().map(JooqReconciliationRepository::toItem); }
        catch (RuntimeException exception) { throw translate(exception); }
    }

    private static RunRecord toRun(Record row) {
        return new RunRecord(row.get(ID), new BusinessId(row.get(BUSINESS_ID)), row.get(PROVIDER),
                row.get(IDEMPOTENCY_KEY), row.get(FINGERPRINT), ReconciliationRunState.valueOf(row.get(STATE)),
                row.get(TOTAL_COUNT), row.get(MATCHED_COUNT), row.get(DISCREPANCY_COUNT),
                row.get(CREATED_AT).toInstant(), row.get(COMPLETED_AT) == null ? null : row.get(COMPLETED_AT).toInstant());
    }
    private static ItemRecord toItem(Record row) {
        return new ItemRecord(row.get(ID), new BusinessId(row.get(BUSINESS_ID)), row.get(RUN_ID), row.get(PROVIDER),
                row.get(PROVIDER_EVENT_ID), row.get(PROVIDER_PAYMENT_ID), row.get(PAYMENT_ID), row.get(AMOUNT),
                row.get(CURRENCY).trim(), row.get(PROVIDER_STATUS), ReconciliationClassification.valueOf(row.get(CLASSIFICATION)),
                row.get(PAYLOAD_SHA256), row.get(CREATED_AT).toInstant());
    }
    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private static ReconciliationPersistenceException translate(RuntimeException exception) {
        if (exception instanceof ReconciliationPersistenceException persistence) return persistence;
        return new ReconciliationPersistenceException(exception);
    }
    private static Table<?> table(String name) { return DSL.table(DSL.name("public", name)); }
    private static <T> Field<T> field(String name, Class<T> type) { return DSL.field(DSL.name(name), type); }
}
