package com.tino.backend.sync.adapter.out.persistence;

import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.sync.application.exception.SyncPersistenceException;
import com.tino.backend.sync.application.port.out.SyncEventRepository;
import com.tino.backend.sync.domain.model.SyncEvent;
import com.tino.backend.sync.domain.model.SyncEventEffects;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** jOOQ-only persistence adapter for the atomic M6 event write set. */
@Repository
public class JooqSyncEventRepository implements SyncEventRepository {
    private static final Table<?> CLAIMS = table("sync_event_claims");
    private static final Table<?> CHANGES = table("sync_changes");
    private static final Table<?> OUTBOX = table("sync_outbox");
    private static final Table<?> REJECTIONS = table("sync_event_rejections");
    private static final Field<UUID> BUSINESS_ID = field("business_id", UUID.class);
    private static final Field<UUID> EVENT_ID = field("event_id", UUID.class);
    private static final Field<UUID> ID = field("id", UUID.class);
    private static final Field<UUID> OUTBOX_ID = field("id", UUID.class);
    private static final Field<String> STORE_ID = field("store_id", String.class);
    private static final Field<String> DEVICE_ID = field("device_id", String.class);
    private static final Field<String> AGGREGATE_ID = field("aggregate_id", String.class);
    private static final Field<String> EVENT_TYPE = field("event_type", String.class);
    private static final Field<Integer> SCHEMA_VERSION = field("schema_version", Integer.class);
    private static final Field<OffsetDateTime> OCCURRED_AT = field("occurred_at", OffsetDateTime.class);
    private static final Field<JSONB> PAYLOAD = field("payload", JSONB.class);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
    private static final Field<Boolean> RETRYABLE = field("retryable", Boolean.class);
    private static final Field<String> CODE = field("code", String.class);
    private static final Field<String> MESSAGE = field("message", String.class);

    private final DSLContext dsl;

    public JooqSyncEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean claim(BusinessId businessId, SyncEvent event, Instant createdAt) {
        try {
            return dsl.insertInto(CLAIMS)
                    .columns(BUSINESS_ID, EVENT_ID, STORE_ID, DEVICE_ID, AGGREGATE_ID,
                            EVENT_TYPE, SCHEMA_VERSION, OCCURRED_AT, PAYLOAD, CREATED_AT)
                    .values(businessId.value(), event.eventId(), event.storeId(), event.deviceId(),
                            event.aggregateId(), event.eventType(), event.schemaVersion(),
                            databaseTime(event.occurredAt()), json(event.payloadJson()),
                            databaseTime(createdAt))
                    .onConflict(BUSINESS_ID, EVENT_ID)
                    .doNothing()
                    .execute() == 1;
        } catch (RuntimeException exception) {
            throw new SyncPersistenceException(exception);
        }
    }

    @Override
    public void appendAccepted(
            BusinessId businessId,
            SyncEvent event,
            SyncEventEffects effects,
            UUID outboxId,
            Instant createdAt) {
        try {
            dsl.insertInto(CHANGES)
                    .columns(BUSINESS_ID, EVENT_ID, STORE_ID, DEVICE_ID, AGGREGATE_ID,
                            EVENT_TYPE, SCHEMA_VERSION, OCCURRED_AT, PAYLOAD, CREATED_AT)
                    .values(businessId.value(), event.eventId(), event.storeId(), event.deviceId(),
                            event.aggregateId(), event.eventType(), event.schemaVersion(),
                            databaseTime(event.occurredAt()), json(effects.changePayloadJson()),
                            databaseTime(createdAt))
                    .execute();
            dsl.insertInto(OUTBOX)
                    .columns(OUTBOX_ID, BUSINESS_ID, EVENT_ID, EVENT_TYPE, PAYLOAD, CREATED_AT)
                    .values(outboxId, businessId.value(), event.eventId(), event.eventType(),
                            json(effects.outboxPayloadJson()), databaseTime(createdAt))
                    .execute();
        } catch (RuntimeException exception) {
            throw new SyncPersistenceException(exception);
        }
    }

    @Override
    public void recordRejection(
            BusinessId businessId,
            UUID rejectionId,
            UUID eventId,
            String deviceId,
            String code,
            boolean retryable,
            String message,
            Instant createdAt) {
        try {
            dsl.insertInto(REJECTIONS)
                    .columns(ID, BUSINESS_ID, EVENT_ID, DEVICE_ID, CODE, RETRYABLE, MESSAGE, CREATED_AT)
                    .values(rejectionId, businessId.value(), eventId, deviceId, code, retryable,
                            message, databaseTime(createdAt))
                    .execute();
        } catch (RuntimeException exception) {
            throw new SyncPersistenceException(exception);
        }
    }

    private static Table<?> table(String name) {
        return DSL.table(DSL.name("public", name));
    }

    private static <T> Field<T> field(String name, Class<T> type) {
        return DSL.field(DSL.name(name), type);
    }

    private static JSONB json(String value) {
        return JSONB.jsonb(value);
    }

    private static OffsetDateTime databaseTime(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
