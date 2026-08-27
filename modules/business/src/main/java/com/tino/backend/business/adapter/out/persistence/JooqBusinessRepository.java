package com.tino.backend.business.adapter.out.persistence;

import com.tino.backend.business.application.port.out.BusinessRepository;
import com.tino.backend.business.application.port.out.BusinessPersistenceException;
import com.tino.backend.business.application.port.out.DuplicateMembershipException;
import com.tino.backend.business.domain.model.Business;
import com.tino.backend.business.domain.model.BusinessMembership;
import com.tino.backend.business.domain.model.BusinessName;
import com.tino.backend.business.domain.model.BusinessStatus;
import com.tino.backend.business.domain.model.BusinessVertical;
import com.tino.backend.business.domain.model.MembershipStatus;
import com.tino.backend.business.domain.model.BusinessRole;
import com.tino.backend.business.domain.model.MembershipId;
import com.tino.backend.business.domain.model.UserId;
import com.tino.backend.shared.kernel.BusinessId;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
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

/** jOOQ-only adapter for Business persistence and the atomic initial OWNER write. */
@Repository
public class JooqBusinessRepository implements BusinessRepository {
    private static final Table<?> BUSINESSES = DSL.table(DSL.name("public", "businesses"));
    private static final Table<?> MEMBERSHIPS =
            DSL.table(DSL.name("public", "business_memberships"));
    private static final Field<UUID> BUSINESS_ID =
            DSL.field(DSL.name("id"), UUID.class);
    private static final Field<String> TRADE_NAME =
            DSL.field(DSL.name("trade_name"), String.class);
    private static final Field<String> VERTICAL =
            DSL.field(DSL.name("vertical"), String.class);
    private static final Field<String> BUSINESS_STATUS =
            DSL.field(DSL.name("status"), String.class);
    private static final Field<OffsetDateTime> CREATED_AT =
            DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT =
            DSL.field(DSL.name("updated_at"), OffsetDateTime.class);
    private static final Field<UUID> MEMBERSHIP_ID =
            DSL.field(DSL.name("id"), UUID.class);
    private static final Field<UUID> MEMBERSHIP_BUSINESS_ID =
            DSL.field(DSL.name("business_id"), UUID.class);
    private static final Field<UUID> MEMBERSHIP_USER_ID =
            DSL.field(DSL.name("user_id"), UUID.class);
    private static final Field<String> MEMBERSHIP_ROLE =
            DSL.field(DSL.name("role"), String.class);
    private static final Field<String> MEMBERSHIP_STATUS =
            DSL.field(DSL.name("status"), String.class);

    private final DSLContext dsl;

    public JooqBusinessRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void createWithOwner(Business business, BusinessMembership owner) {
        try {
            dsl.insertInto(BUSINESSES)
                    .columns(BUSINESS_ID, TRADE_NAME, VERTICAL, BUSINESS_STATUS, CREATED_AT, UPDATED_AT)
                    .values(
                            business.id().value(),
                            business.tradeName().value(),
                            business.vertical().name(),
                            business.status().name(),
                            toDatabaseTime(business.createdAt()),
                            toDatabaseTime(business.updatedAt()))
                    .execute();
            dsl.insertInto(MEMBERSHIPS)
                    .columns(
                            MEMBERSHIP_ID,
                            MEMBERSHIP_BUSINESS_ID,
                            MEMBERSHIP_USER_ID,
                            MEMBERSHIP_ROLE,
                            MEMBERSHIP_STATUS,
                            CREATED_AT,
                            UPDATED_AT)
                    .values(
                            owner.id().value(),
                            owner.businessId().value(),
                            owner.userId().value(),
                            owner.role().name(),
                            owner.status().name(),
                            toDatabaseTime(owner.createdAt()),
                            toDatabaseTime(owner.updatedAt()))
                    .execute();
        } catch (org.jooq.exception.DataAccessException exception) {
            throw translate(exception);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw translate(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Business> findById(BusinessId businessId) {
        try {
            return dsl.select(BUSINESS_ID, TRADE_NAME, VERTICAL, BUSINESS_STATUS, CREATED_AT, UPDATED_AT)
                    .from(BUSINESSES)
                    .where(BUSINESS_ID.eq(businessId.value()))
                    .fetchOptional()
                    .map(JooqBusinessRepository::toBusiness);
        } catch (org.jooq.exception.DataAccessException exception) {
            throw new BusinessPersistenceException(exception);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new BusinessPersistenceException(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Business> findByIds(Collection<BusinessId> businessIds) {
        if (businessIds.isEmpty()) {
            return List.of();
        }
        try {
            var values = businessIds.stream().map(BusinessId::value).toList();
            return dsl.select(BUSINESS_ID, TRADE_NAME, VERTICAL, BUSINESS_STATUS, CREATED_AT, UPDATED_AT)
                    .from(BUSINESSES)
                    .where(BUSINESS_ID.in(values))
                    .fetch()
                    .map(JooqBusinessRepository::toBusiness);
        } catch (org.jooq.exception.DataAccessException exception) {
            throw new BusinessPersistenceException(exception);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new BusinessPersistenceException(exception);
        }
    }

    private static Business toBusiness(Record record) {
        try {
            return new Business(
                    new BusinessId(record.get(BUSINESS_ID, UUID.class)),
                    new BusinessName(record.get(TRADE_NAME, String.class)),
                    BusinessVertical.valueOf(record.get(VERTICAL, String.class)),
                    BusinessStatus.valueOf(record.get(BUSINESS_STATUS, String.class)),
                    toInstant(record.get(CREATED_AT, OffsetDateTime.class)),
                    toInstant(record.get(UPDATED_AT, OffsetDateTime.class)));
        } catch (RuntimeException exception) {
            throw new BusinessPersistenceException(exception);
        }
    }

    private static OffsetDateTime toDatabaseTime(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime value) {
        if (value == null) {
            throw new IllegalStateException("business timestamp is null");
        }
        return value.toInstant();
    }

    private static boolean isSqlState(Throwable exception, String state) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException && state.equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static BusinessPersistenceException translate(Throwable exception) {
        if (isSqlState(exception, "23505")) {
            return new DuplicateMembershipException(exception);
        }
        return new BusinessPersistenceException(exception);
    }
}
