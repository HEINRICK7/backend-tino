package com.tino.backend.identity.adapter.out.persistence;

import com.tino.backend.identity.application.port.out.OtpChallengeRepository;
import com.tino.backend.identity.application.port.out.OtpPersistenceException;
import com.tino.backend.identity.domain.model.OtpChallenge;
import com.tino.backend.identity.domain.model.OtpChallengeStatus;
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

/** PostgreSQL adapter for pre-authentication OTP state. */
@Repository
public class JooqOtpChallengeRepository implements OtpChallengeRepository {
    private static final Table<?> CHALLENGES = DSL.table(DSL.name("public", "otp_challenges"));
    private static final Field<UUID> ID = DSL.field(DSL.name("id"), UUID.class);
    private static final Field<String> PHONE_E164 = DSL.field(DSL.name("phone_e164"), String.class);
    private static final Field<String> PHONE_HASH = DSL.field(DSL.name("phone_hash"), String.class);
    private static final Field<String> ORIGIN_HASH = DSL.field(DSL.name("request_origin_hash"), String.class);
    private static final Field<String> CODE_VERIFIER = DSL.field(DSL.name("code_verifier"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<OffsetDateTime> EXPIRES_AT = DSL.field(DSL.name("expires_at"), OffsetDateTime.class);
    private static final Field<Integer> ATTEMPT_COUNT = DSL.field(DSL.name("attempt_count"), Integer.class);
    private static final Field<Integer> MAX_ATTEMPTS = DSL.field(DSL.name("max_attempts"), Integer.class);
    private static final Field<Integer> RESEND_COUNT = DSL.field(DSL.name("resend_count"), Integer.class);
    private static final Field<Integer> MAX_RESENDS = DSL.field(DSL.name("max_resends"), Integer.class);
    private static final Field<OffsetDateTime> RESEND_AVAILABLE_AT =
            DSL.field(DSL.name("resend_available_at"), OffsetDateTime.class);
    private static final Field<String> PROVIDER_MESSAGE_ID =
            DSL.field(DSL.name("provider_message_id"), String.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> VERIFIED_AT = DSL.field(DSL.name("verified_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> CONSUMED_AT = DSL.field(DSL.name("consumed_at"), OffsetDateTime.class);
    private static final Field<String> TICKET_HASH = DSL.field(DSL.name("verification_ticket_hash"), String.class);
    private static final Field<OffsetDateTime> TICKET_EXPIRES_AT =
            DSL.field(DSL.name("verification_ticket_expires_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public JooqOtpChallengeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void lockPhone(String phoneHash) {
        try {
            dsl.fetch("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", phoneHash);
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OtpChallenge> findLatestPendingByPhoneHash(String phoneHash) {
        try {
            return dsl.select(CHALLENGES.fields())
                    .from(CHALLENGES)
                    .where(PHONE_HASH.eq(phoneHash).and(STATUS.in(
                            OtpChallengeStatus.PENDING.name(), OtpChallengeStatus.DELIVERED.name())))
                    .orderBy(CREATED_AT.desc())
                    .limit(1)
                    .fetchOptional(JooqOtpChallengeRepository::toDomain);
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countCreatedSinceByPhoneHash(String phoneHash, Instant since) {
        try {
            return dsl.fetchCount(dsl.selectFrom(CHALLENGES)
                    .where(PHONE_HASH.eq(phoneHash).and(CREATED_AT.ge(time(since)))));
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countCreatedSinceByOriginHash(String originHash, Instant since) {
        try {
            return dsl.fetchCount(dsl.selectFrom(CHALLENGES)
                    .where(ORIGIN_HASH.eq(originHash).and(CREATED_AT.ge(time(since)))));
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    @Override
    @Transactional
    public void insert(OtpChallenge challenge) {
        try {
            dsl.insertInto(CHALLENGES)
                    .columns(CHALLENGES.fields())
                    .values(
                            challenge.id(), challenge.phone().e164(), challenge.phoneHash(),
                            challenge.requestOriginHash(), challenge.codeVerifier(), challenge.status().name(),
                            time(challenge.expiresAt()), challenge.attemptCount(), challenge.maxAttempts(),
                            challenge.resendCount(), challenge.maxResends(), time(challenge.resendAvailableAt()),
                            time(challenge.createdAt()),
                            time(challenge.verifiedAt()), time(challenge.consumedAt()),
                            challenge.verificationTicketHash(), time(challenge.verificationTicketExpiresAt()),
                            challenge.providerMessageId())
                    .execute();
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    @Override
    @Transactional
    public Optional<OtpChallenge> findByIdForUpdate(UUID challengeId) {
        try {
            return dsl.select(CHALLENGES.fields())
                    .from(CHALLENGES)
                    .where(ID.eq(challengeId))
                    .forUpdate()
                    .fetchOptional(JooqOtpChallengeRepository::toDomain);
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    @Override
    @Transactional
    public Optional<OtpChallenge> findByTicketHashForUpdate(String ticketHash) {
        try {
            return dsl.select(CHALLENGES.fields())
                    .from(CHALLENGES)
                    .where(TICKET_HASH.eq(ticketHash))
                    .forUpdate()
                    .fetchOptional(JooqOtpChallengeRepository::toDomain);
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    @Override
    @Transactional
    public Optional<OtpChallenge> findByProviderMessageIdForUpdate(String providerMessageId) {
        try {
            return dsl.select(CHALLENGES.fields())
                    .from(CHALLENGES)
                    .where(PROVIDER_MESSAGE_ID.eq(providerMessageId))
                    .forUpdate()
                    .fetchOptional(JooqOtpChallengeRepository::toDomain);
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    @Override
    @Transactional
    public void update(OtpChallenge challenge) {
        try {
            dsl.update(CHALLENGES)
                    .set(CODE_VERIFIER, challenge.codeVerifier())
                    .set(STATUS, challenge.status().name())
                    .set(EXPIRES_AT, time(challenge.expiresAt()))
                    .set(ATTEMPT_COUNT, challenge.attemptCount())
                    .set(RESEND_COUNT, challenge.resendCount())
                    .set(RESEND_AVAILABLE_AT, time(challenge.resendAvailableAt()))
                    .set(PROVIDER_MESSAGE_ID, challenge.providerMessageId())
                    .set(VERIFIED_AT, time(challenge.verifiedAt()))
                    .set(CONSUMED_AT, time(challenge.consumedAt()))
                    .set(TICKET_HASH, challenge.verificationTicketHash())
                    .set(TICKET_EXPIRES_AT, time(challenge.verificationTicketExpiresAt()))
                    .where(ID.eq(challenge.id()))
                    .execute();
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    @Override
    @Transactional
    public int deleteFinishedBefore(Instant before) {
        try {
            return dsl.deleteFrom(CHALLENGES)
                    .where(STATUS.in(
                                    OtpChallengeStatus.EXPIRED.name(),
                                    OtpChallengeStatus.LOCKED.name(),
                                    OtpChallengeStatus.CONSUMED.name(),
                                    OtpChallengeStatus.DELIVERY_FAILED.name(),
                                    OtpChallengeStatus.CANCELLED.name())
                            .and(EXPIRES_AT.lt(time(before))))
                    .execute();
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    private static OtpChallenge toDomain(Record record) {
        try {
            return new OtpChallenge(
                    record.get(ID),
                    PhoneNumber.normalize(record.get(PHONE_E164)),
                    record.get(PHONE_HASH),
                    record.get(ORIGIN_HASH),
                    record.get(CODE_VERIFIER),
                    OtpChallengeStatus.valueOf(record.get(STATUS)),
                    instant(record.get(EXPIRES_AT)),
                    record.get(ATTEMPT_COUNT),
                    record.get(MAX_ATTEMPTS),
                    record.get(RESEND_COUNT),
                    record.get(MAX_RESENDS),
                    instant(record.get(RESEND_AVAILABLE_AT)),
                    record.get(PROVIDER_MESSAGE_ID),
                    instant(record.get(CREATED_AT)),
                    instant(record.get(VERIFIED_AT)),
                    instant(record.get(CONSUMED_AT)),
                    record.get(TICKET_HASH),
                    instant(record.get(TICKET_EXPIRES_AT)));
        } catch (RuntimeException exception) {
            throw new OtpPersistenceException(exception);
        }
    }

    private static OffsetDateTime time(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
