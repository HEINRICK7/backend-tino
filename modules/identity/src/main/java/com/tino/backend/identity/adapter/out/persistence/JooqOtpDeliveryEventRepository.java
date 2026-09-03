package com.tino.backend.identity.adapter.out.persistence;

import com.tino.backend.identity.application.port.out.OtpDeliveryEventRepository;
import com.tino.backend.identity.application.port.out.OtpPersistenceException;
import com.tino.backend.identity.domain.model.OtpDeliveryEvent;
import com.tino.backend.identity.domain.model.PhoneNumber;
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

/** PostgreSQL adapter for outbound WhatsApp delivery evidence. */
@Repository
public class JooqOtpDeliveryEventRepository implements OtpDeliveryEventRepository {
    private static final Table<?> EVENTS = DSL.table(DSL.name("public", "otp_delivery_events"));
    private static final Field<String> PROVIDER_EVENT_ID = DSL.field(DSL.name("provider_event_id"), String.class);
    private static final Field<UUID> CHALLENGE_ID = DSL.field(DSL.name("challenge_id"), UUID.class);
    private static final Field<String> PROVIDER_MESSAGE_ID = DSL.field(DSL.name("provider_message_id"), String.class);
    private static final Field<String> RECIPIENT_PHONE = DSL.field(DSL.name("recipient_phone_e164"), String.class);
    private static final Field<String> EVENT_TYPE = DSL.field(DSL.name("event_type"), String.class);
    private static final Field<OffsetDateTime> OCCURRED_AT = DSL.field(DSL.name("occurred_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> RECEIVED_AT = DSL.field(DSL.name("received_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public JooqOtpDeliveryEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OtpDeliveryEvent> findByProviderEventId(String providerEventId) {
        try {
            return dsl.select(EVENTS.fields()).from(EVENTS)
                    .where(PROVIDER_EVENT_ID.eq(providerEventId))
                    .fetchOptional(JooqOtpDeliveryEventRepository::toDomain);
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    @Override
    @Transactional
    public void insert(OtpDeliveryEvent event) {
        try {
            dsl.insertInto(EVENTS)
                    .columns(EVENTS.fields())
                    .values(event.providerEventId(), event.challengeId(), event.providerMessageId(),
                            event.recipientPhone().e164(), event.eventType(), time(event.occurredAt()),
                            time(event.receivedAt()))
                    .execute();
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    private static OtpDeliveryEvent toDomain(Record record) {
        try {
            return new OtpDeliveryEvent(
                    record.get(PROVIDER_EVENT_ID), record.get(CHALLENGE_ID),
                    record.get(PROVIDER_MESSAGE_ID), PhoneNumber.normalize(record.get(RECIPIENT_PHONE)),
                    record.get(EVENT_TYPE), instant(record.get(OCCURRED_AT)), instant(record.get(RECEIVED_AT)));
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    private static OffsetDateTime time(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value.toInstant();
    }
}
