package com.tino.backend.business.adapter.out.persistence;

import com.tino.backend.business.application.port.out.BusinessMembershipRepository;
import com.tino.backend.business.application.port.out.BusinessPersistenceException;
import com.tino.backend.business.application.port.out.DuplicateMembershipException;
import com.tino.backend.business.domain.model.BusinessMembership;
import com.tino.backend.business.domain.model.BusinessRole;
import com.tino.backend.business.domain.model.MembershipId;
import com.tino.backend.business.domain.model.MembershipStatus;
import com.tino.backend.business.domain.model.UserId;
import com.tino.backend.shared.kernel.BusinessId;
import java.sql.SQLException;
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

/** jOOQ-only adapter for explicit User-to-Business membership queries. */
@Repository
public class JooqBusinessMembershipRepository implements BusinessMembershipRepository {
    private static final Table<?> MEMBERSHIPS =
            DSL.table(DSL.name("public", "business_memberships"));
    private static final Field<UUID> ID = DSL.field(DSL.name("id"), UUID.class);
    private static final Field<UUID> BUSINESS_ID =
            DSL.field(DSL.name("business_id"), UUID.class);
    private static final Field<UUID> USER_ID = DSL.field(DSL.name("user_id"), UUID.class);
    private static final Field<String> ROLE = DSL.field(DSL.name("role"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<OffsetDateTime> CREATED_AT =
            DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT =
            DSL.field(DSL.name("updated_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public JooqBusinessMembershipRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void insert(BusinessMembership membership) throws DuplicateMembershipException {
        try {
            dsl.insertInto(MEMBERSHIPS)
                    .columns(ID, BUSINESS_ID, USER_ID, ROLE, STATUS, CREATED_AT, UPDATED_AT)
                    .values(
                            membership.id().value(),
                            membership.businessId().value(),
                            membership.userId().value(),
                            membership.role().name(),
                            membership.status().name(),
                            toDatabaseTime(membership.createdAt()),
                            toDatabaseTime(membership.updatedAt()))
                    .execute();
        } catch (org.jooq.exception.DataAccessException exception) {
            throw translate(exception);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw translate(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessMembership> findByUserAndBusiness(
            UserId userId, BusinessId businessId) {
        try {
            return dsl.select(ID, BUSINESS_ID, USER_ID, ROLE, STATUS, CREATED_AT, UPDATED_AT)
                    .from(MEMBERSHIPS)
                    .where(USER_ID.eq(userId.value()).and(BUSINESS_ID.eq(businessId.value())))
                    .fetchOptional()
                    .map(JooqBusinessMembershipRepository::toMembership);
        } catch (org.jooq.exception.DataAccessException exception) {
            throw new BusinessPersistenceException(exception);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new BusinessPersistenceException(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusinessMembership> findActiveByUser(UserId userId) {
        try {
            return dsl.select(ID, BUSINESS_ID, USER_ID, ROLE, STATUS, CREATED_AT, UPDATED_AT)
                    .from(MEMBERSHIPS)
                    .where(USER_ID.eq(userId.value()).and(STATUS.eq(MembershipStatus.ACTIVE.name())))
                    .fetch()
                    .map(JooqBusinessMembershipRepository::toMembership);
        } catch (org.jooq.exception.DataAccessException exception) {
            throw new BusinessPersistenceException(exception);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new BusinessPersistenceException(exception);
        }
    }

    private static BusinessMembership toMembership(Record record) {
        try {
            return new BusinessMembership(
                    new MembershipId(record.get(ID, UUID.class)),
                    new BusinessId(record.get(BUSINESS_ID, UUID.class)),
                    new UserId(record.get(USER_ID, UUID.class)),
                    BusinessRole.valueOf(record.get(ROLE, String.class)),
                    MembershipStatus.valueOf(record.get(STATUS, String.class)),
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
            throw new IllegalStateException("membership timestamp is null");
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
