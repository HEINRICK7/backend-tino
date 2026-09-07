package com.tino.backend.business.adapter.out.persistence;

import com.tino.backend.business.application.model.PhoneChangeRequest;
import com.tino.backend.business.application.port.out.PhoneChangeRepository;
import com.tino.backend.business.domain.model.PhoneChangePersistenceException;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqPhoneChangeRepository implements PhoneChangeRepository {
    private static final Table<?> REQUESTS = DSL.table(DSL.name("public", "phone_change_requests"));
    private static final Field<UUID> CHALLENGE_ID = DSL.field(DSL.name("challenge_id"), UUID.class);
    private static final Field<UUID> USER_ID = DSL.field(DSL.name("user_id"), UUID.class);
    private static final Field<UUID> BUSINESS_ID = DSL.field(DSL.name("business_id"), UUID.class);
    private static final Field<String> NEW_PHONE = DSL.field(DSL.name("new_phone_e164"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> COMPLETED_AT = DSL.field(DSL.name("completed_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public JooqPhoneChangeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public Optional<PhoneChangeRequest> findByChallengeIdForUpdate(UUID challengeId) {
        try {
            return dsl.select(CHALLENGE_ID, USER_ID, BUSINESS_ID, NEW_PHONE, STATUS, CREATED_AT, COMPLETED_AT)
                    .from(REQUESTS)
                    .where(CHALLENGE_ID.eq(challengeId))
                    .forUpdate()
                    .fetchOptional()
                    .map(JooqPhoneChangeRepository::toModel);
        } catch (RuntimeException exception) {
            throw new PhoneChangePersistenceException(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PhoneChangeRequest> findPendingByUserBusinessAndPhone(
            UUID userId, UUID businessId, String phoneE164) {
        try {
            return dsl.select(CHALLENGE_ID, USER_ID, BUSINESS_ID, NEW_PHONE, STATUS, CREATED_AT, COMPLETED_AT)
                    .from(REQUESTS)
                    .where(USER_ID.eq(userId)
                            .and(BUSINESS_ID.eq(businessId))
                            .and(NEW_PHONE.eq(phoneE164))
                            .and(STATUS.eq(PhoneChangeRequest.Status.PENDING.name())))
                    .orderBy(CREATED_AT.desc())
                    .limit(1)
                    .fetchOptional()
                    .map(JooqPhoneChangeRepository::toModel);
        } catch (RuntimeException exception) {
            throw new PhoneChangePersistenceException(exception);
        }
    }

    @Override
    public void insert(PhoneChangeRequest request) {
        try {
            dsl.insertInto(REQUESTS)
                    .columns(CHALLENGE_ID, USER_ID, BUSINESS_ID, NEW_PHONE, STATUS, CREATED_AT, COMPLETED_AT)
                    .values(request.challengeId(), request.userId(), request.businessId().value(),
                            request.newPhoneE164(), request.status().name(), toDatabaseTime(request.createdAt()),
                            request.completedAt() == null ? null : toDatabaseTime(request.completedAt()))
                    .execute();
        } catch (RuntimeException exception) {
            throw new PhoneChangePersistenceException(exception);
        }
    }

    @Override
    public void markCompleted(UUID challengeId, Instant completedAt) {
        try {
            var updated = dsl.update(REQUESTS)
                    .set(STATUS, PhoneChangeRequest.Status.COMPLETED.name())
                    .set(COMPLETED_AT, toDatabaseTime(completedAt))
                    .where(CHALLENGE_ID.eq(challengeId)
                            .and(STATUS.eq(PhoneChangeRequest.Status.PENDING.name())))
                    .execute();
            if (updated != 1) {
                throw new PhoneChangePersistenceException(
                        new IllegalStateException("phone change request is not pending"));
            }
        } catch (PhoneChangePersistenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PhoneChangePersistenceException(exception);
        }
    }

    private static PhoneChangeRequest toModel(org.jooq.Record record) {
        var status = PhoneChangeRequest.Status.valueOf(record.get(STATUS));
        return new PhoneChangeRequest(
                record.get(CHALLENGE_ID),
                record.get(USER_ID),
                new BusinessId(record.get(BUSINESS_ID)),
                record.get(NEW_PHONE),
                status,
                record.get(CREATED_AT).toInstant(),
                record.get(COMPLETED_AT) == null ? null : record.get(COMPLETED_AT).toInstant());
    }

    private static OffsetDateTime toDatabaseTime(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
