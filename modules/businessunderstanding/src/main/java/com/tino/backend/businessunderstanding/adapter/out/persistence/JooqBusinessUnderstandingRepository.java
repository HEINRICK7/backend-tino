package com.tino.backend.businessunderstanding.adapter.out.persistence;

import com.tino.backend.businessunderstanding.application.exception.BusinessUnderstandingPersistenceException;
import com.tino.backend.businessunderstanding.application.exception.BusinessUnderstandingNotFoundException;
import com.tino.backend.businessunderstanding.application.port.out.BusinessUnderstandingRepository;
import com.tino.backend.businessunderstanding.domain.model.ActivityCode;
import com.tino.backend.businessunderstanding.domain.model.BusinessActivity;
import com.tino.backend.businessunderstanding.domain.model.BusinessItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.BusinessOperatingMode;
import com.tino.backend.businessunderstanding.domain.model.ItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeSource;
import com.tino.backend.businessunderstanding.domain.model.OperatingMode;
import com.tino.backend.businessunderstanding.domain.model.OperatingModeSource;
import com.tino.backend.businessunderstanding.domain.model.UsageContext;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.sql.SQLException;
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
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqBusinessUnderstandingRepository implements BusinessUnderstandingRepository {
    private static final Table<?> ACTIVITIES = table("business_activities");
    private static final Table<?> MODES = table("business_operating_modes");
    private static final Table<?> PURPOSES = table("business_item_purposes");
    private static final Field<UUID> ID = field("id", UUID.class);
    private static final Field<UUID> BUSINESS_ID = field("business_id", UUID.class);
    private static final Field<String> ACTIVITY_CODE = field("activity_code", String.class);
    private static final Field<String> CUSTOM_LABEL = field("custom_label", String.class);
    private static final Field<String> MODE_CODE = field("mode_code", String.class);
    private static final Field<String> SOURCE = field("source", String.class);
    private static final Field<BigDecimal> CONFIDENCE = field("confidence", BigDecimal.class);
    private static final Field<UUID> PRODUCT_ID = field("product_id", UUID.class);
    private static final Field<String> CANONICAL_ITEM_KEY = field("canonical_item_key", String.class);
    private static final Field<String> USAGE_CONTEXT = field("usage_context", String.class);
    private static final Field<String> PURPOSE = field("purpose", String.class);
    private static final Field<Long> EVIDENCE_COUNT = field("evidence_count", Long.class);
    private static final Field<String> EVIDENCE_CLASSIFIED_BY = field("evidence_classified_by", String.class);
    private static final Field<String> EVIDENCE_REASON = field("evidence_reason", String.class);
    private static final Field<OffsetDateTime> EVIDENCE_AT = field("evidence_at", OffsetDateTime.class);
    private static final Field<Long> QUALIFIED_EVIDENCE_COUNT = DSL.field(
            DSL.name("public", "business_item_purposes", "evidence_count"), Long.class);
    private static final Field<OffsetDateTime> FIRST_OBSERVED_AT = field("first_observed_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> LAST_OBSERVED_AT = field("last_observed_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);
    private final DSLContext dsl;

    public JooqBusinessUnderstandingRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusinessActivity> findActivities(BusinessId businessId) {
        try {
            return dsl.select(ACTIVITY_CODE, CUSTOM_LABEL).from(ACTIVITIES)
                    .where(BUSINESS_ID.eq(businessId.value()))
                    .orderBy(ACTIVITY_CODE.asc()).fetch()
                    .map(row -> new BusinessActivity(
                            ActivityCode.parse(row.get(ACTIVITY_CODE)), row.get(CUSTOM_LABEL)));
        } catch (RuntimeException exception) {
            throw persistence(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusinessOperatingMode> findOperatingModes(BusinessId businessId) {
        try {
            return dsl.select(MODE_CODE, SOURCE, CONFIDENCE).from(MODES)
                    .where(BUSINESS_ID.eq(businessId.value()))
                    .orderBy(MODE_CODE.asc()).fetch()
                    .map(row -> new BusinessOperatingMode(
                            OperatingMode.parse(row.get(MODE_CODE)),
                            OperatingModeSource.valueOf(row.get(SOURCE)), row.get(CONFIDENCE)));
        } catch (RuntimeException exception) {
            throw persistence(exception);
        }
    }

    @Override
    @Transactional
    public void replaceActivities(BusinessId businessId, List<BusinessActivity> activities, Instant now) {
        try {
            var requestedCodes = activities.stream().map(item -> item.code().name()).toList();
            var delete = dsl.deleteFrom(ACTIVITIES).where(BUSINESS_ID.eq(businessId.value()));
            if (!requestedCodes.isEmpty()) {
                delete.and(ACTIVITY_CODE.notIn(requestedCodes));
            }
            delete.execute();
            for (var activity : activities) {
                dsl.insertInto(ACTIVITIES)
                        .columns(ID, BUSINESS_ID, ACTIVITY_CODE, CUSTOM_LABEL, CREATED_AT, UPDATED_AT)
                        .values(UUID.randomUUID(), businessId.value(), activity.code().name(), activity.customLabel(),
                                time(now), time(now))
                        .onConflict(BUSINESS_ID, ACTIVITY_CODE).doUpdate()
                        .set(CUSTOM_LABEL, activity.customLabel()).set(UPDATED_AT, time(now)).execute();
            }
        } catch (RuntimeException exception) {
            throw persistence(exception);
        }
    }

    @Override
    @Transactional
    public void replaceOperatingModes(BusinessId businessId, List<BusinessOperatingMode> modes, Instant now) {
        try {
            var requestedCodes = modes.stream().map(item -> item.mode().name()).toList();
            var delete = dsl.deleteFrom(MODES).where(BUSINESS_ID.eq(businessId.value()));
            if (!requestedCodes.isEmpty()) {
                delete.and(MODE_CODE.notIn(requestedCodes));
            }
            delete.execute();
            for (var mode : modes) {
                dsl.insertInto(MODES)
                        .columns(ID, BUSINESS_ID, MODE_CODE, SOURCE, CONFIDENCE, CREATED_AT, UPDATED_AT)
                        .values(UUID.randomUUID(), businessId.value(), mode.mode().name(), mode.source().name(),
                                mode.confidence(), time(now), time(now))
                        .onConflict(BUSINESS_ID, MODE_CODE).doUpdate()
                        .set(SOURCE, mode.source().name()).set(CONFIDENCE, mode.confidence())
                        .set(UPDATED_AT, time(now)).execute();
            }
        } catch (RuntimeException exception) {
            throw persistence(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessItemPurpose> findPurposeByProduct(BusinessId businessId, UUID productId,
            UsageContext usageContext) {
        return find(BUSINESS_ID.eq(businessId.value()).and(PRODUCT_ID.eq(productId))
                .and(USAGE_CONTEXT.eq(usageContext.value())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessItemPurpose> findPurposeByCanonicalKey(BusinessId businessId, String canonicalItemKey,
            UsageContext usageContext) {
        return find(BUSINESS_ID.eq(businessId.value()).and(CANONICAL_ITEM_KEY.eq(canonicalItemKey))
                .and(USAGE_CONTEXT.eq(usageContext.value())));
    }

    @Override
    @Transactional
    public void upsertAutomaticPurpose(BusinessItemPurpose item) {
        if (item.source() == ItemPurposeSource.USER_CONFIRMED) {
            throw new IllegalArgumentException("automatic purpose cannot be user confirmed");
        }
        upsert(item, false);
    }

    @Override
    @Transactional
    public void upsertConfirmedPurpose(BusinessItemPurpose item) {
        if (item.source() != ItemPurposeSource.USER_CONFIRMED) {
            throw new IllegalArgumentException("confirmed purpose must have USER_CONFIRMED source");
        }
        upsert(item, true);
    }

    private void upsert(BusinessItemPurpose item, boolean explicitUserCorrection) {
        try {
            var identity = BUSINESS_ID.eq(item.businessId().value()).and(USAGE_CONTEXT.eq(item.usageContext().value()))
                    .and(item.productId() != null ? PRODUCT_ID.eq(item.productId())
                            : CANONICAL_ITEM_KEY.eq(item.canonicalItemKey()));
            var existing = find(identity);
            if (existing.isPresent()) {
                var updateCondition = identity;
                if (!explicitUserCorrection) {
                    updateCondition = updateCondition.and(
                            authorityRank().le(DSL.val(item.source().authority().rank())));
                }
                dsl.update(PURPOSES)
                        .set(PURPOSE, item.purpose().name()).set(SOURCE, item.source().name())
                        .set(CONFIDENCE, item.confidence())
                        .set(EVIDENCE_COUNT, QUALIFIED_EVIDENCE_COUNT.add(1L))
                        .set(EVIDENCE_CLASSIFIED_BY, item.evidenceClassifiedBy())
                        .set(EVIDENCE_REASON, item.evidenceReason()).set(EVIDENCE_AT, time(item.evidenceAt()))
                        .set(LAST_OBSERVED_AT, time(item.lastObservedAt())).set(UPDATED_AT, time(item.updatedAt()))
                        .where(updateCondition).execute();
                return;
            }
            dsl.insertInto(PURPOSES)
                    .columns(ID, BUSINESS_ID, PRODUCT_ID, CANONICAL_ITEM_KEY, PURPOSE, SOURCE, CONFIDENCE,
                            EVIDENCE_COUNT, EVIDENCE_CLASSIFIED_BY, EVIDENCE_REASON, EVIDENCE_AT,
                            FIRST_OBSERVED_AT, LAST_OBSERVED_AT, CREATED_AT, UPDATED_AT, USAGE_CONTEXT)
                    .values(item.id(), item.businessId().value(), item.productId(), item.canonicalItemKey(),
                            item.purpose().name(), item.source().name(), item.confidence(), item.evidenceCount(),
                            item.evidenceClassifiedBy(), item.evidenceReason(), time(item.evidenceAt()),
                            time(item.firstObservedAt()), time(item.lastObservedAt()), time(item.createdAt()),
                            time(item.updatedAt()), item.usageContext().value())
                    .execute();
        } catch (RuntimeException exception) {
            if (isSqlState(exception, "23503") || isSqlState(exception, "42501")) {
                throw new BusinessUnderstandingNotFoundException();
            }
            throw persistence(exception);
        }
    }

    private Optional<BusinessItemPurpose> find(org.jooq.Condition condition) {
        try {
            return dsl.select(ID, BUSINESS_ID, PRODUCT_ID, CANONICAL_ITEM_KEY, PURPOSE, SOURCE, CONFIDENCE,
                            EVIDENCE_COUNT, EVIDENCE_CLASSIFIED_BY, EVIDENCE_REASON, EVIDENCE_AT,
                            USAGE_CONTEXT, FIRST_OBSERVED_AT, LAST_OBSERVED_AT, CREATED_AT, UPDATED_AT)
                    .from(PURPOSES).where(condition).fetchOptional().map(row -> new BusinessItemPurpose(
                            row.get(ID), new BusinessId(row.get(BUSINESS_ID)), row.get(PRODUCT_ID),
                            row.get(CANONICAL_ITEM_KEY), UsageContext.of(row.get(USAGE_CONTEXT)),
                            ItemPurpose.valueOf(row.get(PURPOSE)),
                            ItemPurposeSource.valueOf(row.get(SOURCE)), row.get(CONFIDENCE), row.get(EVIDENCE_COUNT),
                            row.get(EVIDENCE_CLASSIFIED_BY), row.get(EVIDENCE_REASON), instant(row.get(EVIDENCE_AT)),
                            instant(row.get(FIRST_OBSERVED_AT)), instant(row.get(LAST_OBSERVED_AT)),
                            instant(row.get(CREATED_AT)), instant(row.get(UPDATED_AT))));
        } catch (RuntimeException exception) {
            throw persistence(exception);
        }
    }

    private static Table<?> table(String name) {
        return DSL.table(DSL.name("public", name));
    }

    private static <T> Field<T> field(String name, Class<T> type) {
        return DSL.field(DSL.name(name), type);
    }

    private static OffsetDateTime time(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        if (value == null) throw new IllegalStateException("business understanding timestamp is null");
        return value.toInstant();
    }

    private static Field<Integer> authorityRank() {
        return DSL.when(SOURCE.eq(ItemPurposeSource.USER_CONFIRMED.name()), 3)
                .when(SOURCE.eq(ItemPurposeSource.LEARNED.name()), 2)
                .when(SOURCE.eq(ItemPurposeSource.SYSTEM_SUGGESTED.name()), 1)
                .otherwise(0);
    }

    private static BusinessUnderstandingPersistenceException persistence(Throwable exception) {
        return new BusinessUnderstandingPersistenceException(exception);
    }

    private static boolean isSqlState(Throwable exception, String state) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException && state.equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
