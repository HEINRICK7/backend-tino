package com.tino.backend.external.adapter.out.persistence;

import com.tino.backend.external.application.port.out.ExternalBusinessConnectionRepository;
import com.tino.backend.external.domain.model.ExternalBusinessConnection;
import com.tino.backend.external.domain.model.ExternalConnectionStatus;
import com.tino.backend.external.domain.model.ExternalDataSourceType;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqExternalBusinessConnectionRepository implements ExternalBusinessConnectionRepository {
    private static final Table<?> CONNECTIONS = DSL.table(DSL.name("public", "external_business_connections"));
    private static final Field<UUID> ID = field("id", UUID.class);
    private static final Field<UUID> BUSINESS_ID = field("business_id", UUID.class);
    private static final Field<String> PROVIDER = field("provider", String.class);
    private static final Field<String> STATUS = field("status", String.class);
    private static final Field<String> SOURCE_TYPE = field("source_type", String.class);
    private static final Field<OffsetDateTime> LAST_SUCCESS = field("last_successful_sync_at", OffsetDateTime.class);
    private static final Field<String> CURSOR = field("sync_cursor", String.class);
    private static final Field<OffsetDateTime> STARTED = field("last_sync_started_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> FINISHED = field("last_sync_finished_at", OffsetDateTime.class);
    private static final Field<String> ERROR = field("last_sync_error_code", String.class);
    private static final Field<Integer> RECEIVED = field("last_sync_received", Integer.class);
    private static final Field<Integer> CREATED = field("last_sync_created", Integer.class);
    private static final Field<Integer> UPDATED = field("last_sync_updated", Integer.class);
    private static final Field<Integer> DEACTIVATED = field("last_sync_deactivated", Integer.class);
    private static final Field<Integer> REJECTED = field("last_sync_rejected", Integer.class);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);
    private final DSLContext dsl;

    public JooqExternalBusinessConnectionRepository(DSLContext dsl) { this.dsl = dsl; }

    @Override
    public ExternalBusinessConnection create(BusinessId businessId, String provider, Instant now) {
        var id = UUID.randomUUID();
        var time = time(now);
        dsl.insertInto(CONNECTIONS).columns(ID, BUSINESS_ID, PROVIDER, STATUS, SOURCE_TYPE, CREATED_AT, UPDATED_AT)
                .values(id, businessId.value(), provider, ExternalConnectionStatus.CONNECTED.name(),
                        ExternalDataSourceType.EXTERNAL_API.name(), time, time).execute();
        return find(businessId, id).orElseThrow();
    }

    @Override
    public Optional<ExternalBusinessConnection> find(BusinessId businessId, UUID id) {
        return dsl.select().from(CONNECTIONS).where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(id)))
                .fetchOptional(this::map);
    }

    @Override
    public List<ExternalBusinessConnection> list(BusinessId businessId) {
        return dsl.select().from(CONNECTIONS).where(BUSINESS_ID.eq(businessId.value())).orderBy(CREATED_AT.asc())
                .fetch(this::map);
    }

    @Override
    public ExternalBusinessConnection markSyncing(BusinessId businessId, UUID id, Instant now) {
        var existing = find(businessId, id).orElseThrow(() -> new IllegalArgumentException("external connection not found"));
        if (existing.status() == ExternalConnectionStatus.SYNCING) throw new ExternalSyncAlreadyRunningException();
        dsl.update(CONNECTIONS).set(STATUS, ExternalConnectionStatus.SYNCING.name()).set(STARTED, time(now))
                .set(FINISHED, (OffsetDateTime) null).set(ERROR, (String) null).set(RECEIVED, 0).set(CREATED, 0)
                .set(UPDATED, 0).set(DEACTIVATED, 0).set(REJECTED, 0).set(UPDATED_AT, time(now))
                .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(id))).execute();
        return find(businessId, id).orElseThrow();
    }

    @Override
    public void pageSucceeded(BusinessId businessId, UUID id, String cursor, int received, int created,
            int updated, int deactivated, int rejected, Instant now) {
        dsl.update(CONNECTIONS).set(CURSOR, cursor).set(RECEIVED, received).set(CREATED, created)
                .set(UPDATED, updated).set(DEACTIVATED, deactivated).set(REJECTED, rejected).set(UPDATED_AT, time(now))
                .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(id))).execute();
    }

    @Override
    public ExternalBusinessConnection markSucceeded(BusinessId businessId, UUID id, String cursor, int received,
            int created, int updated, int deactivated, int rejected, Instant completedAt) {
        dsl.update(CONNECTIONS).set(STATUS, ExternalConnectionStatus.READY.name()).set(CURSOR, cursor)
                .set(LAST_SUCCESS, time(completedAt)).set(FINISHED, time(completedAt)).set(ERROR, (String) null)
                .set(RECEIVED, received).set(CREATED, created).set(UPDATED, updated).set(DEACTIVATED, deactivated)
                .set(REJECTED, rejected).set(UPDATED_AT, time(completedAt))
                .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(id))).execute();
        return find(businessId, id).orElseThrow();
    }

    @Override
    public ExternalBusinessConnection markFailed(BusinessId businessId, UUID id, ExternalConnectionStatus status,
            String errorCode, int received, int created, int updated, int deactivated, int rejected, Instant finishedAt) {
        dsl.update(CONNECTIONS).set(STATUS, status.name()).set(FINISHED, time(finishedAt)).set(ERROR, errorCode)
                .set(RECEIVED, received).set(CREATED, created).set(UPDATED, updated).set(DEACTIVATED, deactivated)
                .set(REJECTED, rejected).set(UPDATED_AT, time(finishedAt))
                .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(id))).execute();
        return find(businessId, id).orElseThrow();
    }

    private ExternalBusinessConnection map(org.jooq.Record row) {
        return new ExternalBusinessConnection(row.get(ID), new BusinessId(row.get(BUSINESS_ID)), row.get(PROVIDER),
                ExternalConnectionStatus.valueOf(row.get(STATUS)), ExternalDataSourceType.valueOf(row.get(SOURCE_TYPE)),
                instant(row.get(LAST_SUCCESS)), row.get(CURSOR), instant(row.get(STARTED)), instant(row.get(FINISHED)),
                row.get(ERROR), value(row.get(RECEIVED)), value(row.get(CREATED)), value(row.get(UPDATED)),
                value(row.get(DEACTIVATED)), value(row.get(REJECTED)), instant(row.get(CREATED_AT)), instant(row.get(UPDATED_AT)));
    }

    private static int value(Integer value) { return value == null ? 0 : value; }
    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private static Instant instant(OffsetDateTime value) { return value == null ? null : value.toInstant(); }
    private static <T> Field<T> field(String name, Class<T> type) { return DSL.field(DSL.name(name), type); }

    public static final class ExternalSyncAlreadyRunningException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
