package com.tino.backend.messaging.adapter.out.persistence;

import com.tino.backend.messaging.application.port.out.WhatsAppMessageRepository;
import com.tino.backend.messaging.application.port.out.WhatsAppPersistenceException;
import com.tino.backend.messaging.domain.model.WhatsAppMessageType;
import com.tino.backend.shared.kernel.BusinessId;
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
public class JooqWhatsAppMessageRepository implements WhatsAppMessageRepository {
    private static final Table<?> PREVIEWS = table("whatsapp_previews");
    private static final Table<?> DELIVERIES = table("whatsapp_message_deliveries");
    private static final Table<?> AUDIT = table("whatsapp_delivery_audit");

    private static final Field<UUID> ID = field("id", UUID.class);
    private static final Field<UUID> BUSINESS_ID = field("business_id", UUID.class);
    private static final Field<UUID> PREVIEW_ID = field("preview_id", UUID.class);
    private static final Field<UUID> ACCOUNT_ID = field("account_id", UUID.class);
    private static final Field<UUID> CUSTOMER_ID = field("customer_id", UUID.class);
    private static final Field<String> MESSAGE_TYPE = field("message_type", String.class);
    private static final Field<String> TEMPLATE_ID = field("template_id", String.class);
    private static final Field<String> TEMPLATE_VERSION = field("template_version", String.class);
    private static final Field<String> RENDERED_HASH = field("rendered_hash", String.class);
    private static final Field<Long> SNAPSHOT_VERSION = field("snapshot_version", Long.class);
    private static final Field<String> TEXT = field("text_content", String.class);
    private static final Field<String> MIME_TYPE = field("media_mime_type", String.class);
    private static final Field<byte[]> MEDIA = field("media_content", byte[].class);
    private static final Field<String> FILENAME = field("media_filename", String.class);
    private static final Field<String> IDEMPOTENCY_KEY = field("idempotency_key", String.class);
    private static final Field<String> REQUEST_FINGERPRINT = field("request_fingerprint", String.class);
    private static final Field<String> PROVIDER = field("provider", String.class);
    private static final Field<String> PROVIDER_MESSAGE_ID = field("provider_message_id", String.class);
    private static final Field<String> STATUS = field("status", String.class);
    private static final Field<Integer> ATTEMPTS = field("attempt_count", Integer.class);
    private static final Field<OffsetDateTime> AVAILABLE_AT = field("available_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> SENDING_AT = field("sending_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> SENT_AT = field("sent_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> FAILED_AT = field("failed_at", OffsetDateTime.class);
    private static final Field<String> LAST_ERROR = field("last_error", String.class);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);
    private static final Field<String> EVENT_TYPE = field("event_type", String.class);
    private static final Field<UUID> DELIVERY_ID = field("delivery_id", UUID.class);
    private static final Field<UUID> ACTOR_USER_ID = field("actor_user_id", UUID.class);

    private final DSLContext dsl;

    public JooqWhatsAppMessageRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void insertPreview(PreviewRecord preview) {
        try {
            dsl.insertInto(PREVIEWS)
                    .columns(ID, BUSINESS_ID, ACCOUNT_ID, CUSTOMER_ID, MESSAGE_TYPE, TEMPLATE_ID, TEMPLATE_VERSION,
                            RENDERED_HASH, SNAPSHOT_VERSION, TEXT, MIME_TYPE, MEDIA, FILENAME, CREATED_AT, field("expires_at", OffsetDateTime.class))
                    .values(preview.id(), preview.businessId().value(), preview.accountId(), preview.customerId(),
                            preview.messageType().name(), preview.templateId(), preview.templateVersion(), preview.renderedHash(),
                            preview.snapshotVersion(), preview.text(), preview.mimeType(), preview.media(), preview.filename(),
                            time(preview.createdAt()), time(preview.expiresAt()))
                    .execute();
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public void insertAudit(AuditRecord audit) {
        try {
            dsl.insertInto(AUDIT)
                    .columns(ID, BUSINESS_ID, DELIVERY_ID, EVENT_TYPE, MESSAGE_TYPE, TEMPLATE_ID, TEMPLATE_VERSION,
                            RENDERED_HASH, ACTOR_USER_ID, PROVIDER_MESSAGE_ID, CREATED_AT)
                    .values(audit.id(), audit.businessId().value(), audit.deliveryId(), audit.eventType(),
                            audit.messageType().name(), audit.templateId(), audit.templateVersion(), audit.renderedHash(),
                            audit.actorUserId(), audit.providerMessageId(), time(audit.createdAt()))
                    .execute();
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public Optional<PreviewRecord> findPreview(BusinessId businessId, UUID previewId) {
        try {
            return dsl.select(PREVIEWS.fields()).from(PREVIEWS)
                    .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(previewId)))
                    .fetchOptional().map(JooqWhatsAppMessageRepository::preview);
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public Optional<DeliveryRecord> findDelivery(BusinessId businessId, UUID messageId) {
        try {
            return dsl.select(DELIVERIES.fields()).from(DELIVERIES)
                    .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(messageId)))
                    .fetchOptional().map(JooqWhatsAppMessageRepository::delivery);
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public Optional<DeliveryRecord> findDeliveryByIdempotency(BusinessId businessId, String idempotencyKey) {
        try {
            return dsl.select(DELIVERIES.fields()).from(DELIVERIES)
                    .where(BUSINESS_ID.eq(businessId.value()).and(IDEMPOTENCY_KEY.eq(idempotencyKey)))
                    .fetchOptional().map(JooqWhatsAppMessageRepository::delivery);
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public boolean insertDelivery(DeliveryRecord delivery) {
        try {
            return dsl.insertInto(DELIVERIES)
                    .columns(ID, BUSINESS_ID, PREVIEW_ID, ACCOUNT_ID, CUSTOMER_ID, MESSAGE_TYPE, TEMPLATE_ID,
                            TEMPLATE_VERSION, RENDERED_HASH, IDEMPOTENCY_KEY, REQUEST_FINGERPRINT, PROVIDER,
                            PROVIDER_MESSAGE_ID, STATUS, ATTEMPTS, AVAILABLE_AT, CREATED_AT, UPDATED_AT)
                    .values(delivery.id(), delivery.businessId().value(), delivery.previewId(), delivery.accountId(),
                            delivery.customerId(), delivery.messageType().name(), delivery.templateId(), delivery.templateVersion(),
                            delivery.renderedHash(), delivery.idempotencyKey(), delivery.requestFingerprint(), delivery.provider(),
                            delivery.providerMessageId(), delivery.status(), delivery.attemptCount(), time(delivery.availableAt()),
                            time(delivery.createdAt()), time(delivery.updatedAt()))
                    .onConflict(BUSINESS_ID, IDEMPOTENCY_KEY).doNothing().execute() == 1;
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    @Transactional
    public Optional<DeliveryRecord> claimDelivery(BusinessId businessId, UUID messageId, Instant now) {
        try {
            var row = dsl.select(DELIVERIES.fields()).from(DELIVERIES)
                    .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(messageId))
                            .and(STATUS.in("QUEUED", "FAILED"))
                            .and(AVAILABLE_AT.le(time(now))).and(ATTEMPTS.lt(3)))
                    .forUpdate().skipLocked().fetchOptional();
            if (row.isEmpty()) {
                return Optional.empty();
            }
            var current = delivery(row.orElseThrow());
            var attempt = current.attemptCount() + 1;
            dsl.update(DELIVERIES).set(STATUS, "SENDING").set(ATTEMPTS, attempt)
                    .set(SENDING_AT, time(now)).set(UPDATED_AT, time(now))
                    .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(messageId))).execute();
            return findDelivery(businessId, messageId);
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    @Transactional
    public DeliveryRecord markSent(BusinessId businessId, UUID messageId, String provider,
            String providerMessageId, Instant now) {
        try {
            dsl.update(DELIVERIES).set(STATUS, "SENT").set(PROVIDER, provider)
                    .set(PROVIDER_MESSAGE_ID, providerMessageId).set(SENT_AT, time(now))
                    .set(UPDATED_AT, time(now)).where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(messageId))).execute();
            return findDelivery(businessId, messageId).orElseThrow();
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    @Transactional
    public DeliveryRecord markFailed(BusinessId businessId, UUID messageId, String error, Instant availableAt,
            Instant now) {
        try {
            dsl.update(DELIVERIES).set(STATUS, "FAILED").set(FAILED_AT, time(now))
                    .set(AVAILABLE_AT, time(availableAt)).set(LAST_ERROR, bounded(error))
                    .set(UPDATED_AT, time(now)).where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(messageId))).execute();
            return findDelivery(businessId, messageId).orElseThrow();
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public void deleteExpiredPreviews(BusinessId businessId, Instant now) {
        try {
            var preview = PREVIEWS.as("preview_cleanup");
            var delivery = DELIVERIES.as("delivery_cleanup");
            var previewBusiness = DSL.field(DSL.name("preview_cleanup", "business_id"), UUID.class);
            var previewId = DSL.field(DSL.name("preview_cleanup", "id"), UUID.class);
            var previewExpires = DSL.field(DSL.name("preview_cleanup", "expires_at"), OffsetDateTime.class);
            var deliveryBusiness = DSL.field(DSL.name("delivery_cleanup", "business_id"), UUID.class);
            var deliveryPreview = DSL.field(DSL.name("delivery_cleanup", "preview_id"), UUID.class);
            dsl.deleteFrom(preview)
                    .where(previewBusiness.eq(businessId.value()).and(previewExpires.lt(time(now)))
                            .andNotExists(DSL.selectOne().from(delivery)
                                    .where(deliveryBusiness.eq(previewBusiness).and(deliveryPreview.eq(previewId)))))
                    .execute();
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    private static PreviewRecord preview(Record row) {
        return new PreviewRecord(row.get(ID), new BusinessId(row.get(BUSINESS_ID)), row.get(ACCOUNT_ID), row.get(CUSTOMER_ID),
                WhatsAppMessageType.valueOf(row.get(MESSAGE_TYPE)), row.get(TEMPLATE_ID), row.get(TEMPLATE_VERSION),
                row.get(RENDERED_HASH), row.get(SNAPSHOT_VERSION), row.get(TEXT), row.get(MIME_TYPE), row.get(MEDIA),
                row.get(FILENAME), row.get(CREATED_AT).toInstant(), row.get(field("expires_at", OffsetDateTime.class)).toInstant());
    }

    private static DeliveryRecord delivery(Record row) {
        return new DeliveryRecord(row.get(ID), new BusinessId(row.get(BUSINESS_ID)), row.get(PREVIEW_ID), row.get(ACCOUNT_ID),
                row.get(CUSTOMER_ID), WhatsAppMessageType.valueOf(row.get(MESSAGE_TYPE)), row.get(TEMPLATE_ID),
                row.get(TEMPLATE_VERSION), row.get(RENDERED_HASH), row.get(IDEMPOTENCY_KEY), row.get(REQUEST_FINGERPRINT),
                row.get(PROVIDER), row.get(PROVIDER_MESSAGE_ID), row.get(STATUS), row.get(ATTEMPTS),
                row.get(AVAILABLE_AT).toInstant(), instant(row.get(SENT_AT)), instant(row.get(FAILED_AT)), row.get(LAST_ERROR),
                row.get(CREATED_AT).toInstant(), row.get(UPDATED_AT).toInstant());
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime time(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "provider failure";
        }
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    private static Table<?> table(String name) {
        return DSL.table(DSL.name("public", name));
    }

    private static <T> Field<T> field(String name, Class<T> type) {
        return DSL.field(DSL.name(name), type);
    }

    private static WhatsAppPersistenceException translate(RuntimeException exception) {
        if (exception instanceof WhatsAppPersistenceException persistence) {
            return persistence;
        }
        return new WhatsAppPersistenceException(exception);
    }
}
