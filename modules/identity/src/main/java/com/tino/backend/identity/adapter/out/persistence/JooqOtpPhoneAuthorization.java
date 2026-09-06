package com.tino.backend.identity.adapter.out.persistence;

import com.tino.backend.identity.application.port.out.OtpPhoneAuthorization;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL adapter for the phone identity used by the pre-authentication policy. */
@Repository
public class JooqOtpPhoneAuthorization implements OtpPhoneAuthorization {
    private static final Table<?> IDENTITIES = DSL.table(DSL.name("public", "identity_phone_bindings"));
    private static final Field<String> PHONE_HASH = DSL.field(DSL.name("phone_hash"), String.class);
    private static final Field<String> EXTERNAL_SUBJECT = DSL.field(DSL.name("external_subject"), String.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = DSL.field(DSL.name("updated_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public JooqOtpPhoneAuthorization(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAuthorized(String phoneHash, UUID businessId) {
        var identities = IDENTITIES.as("identity_phone");
        var users = DSL.table(DSL.name("public", "users")).as("identity_user");
        var memberships = DSL.table(DSL.name("public", "business_memberships")).as("membership");
        var identityPhoneHash = DSL.field(DSL.name("identity_phone", "phone_hash"), String.class);
        var identitySubject = DSL.field(DSL.name("identity_phone", "external_subject"), String.class);
        var userSubject = DSL.field(DSL.name("identity_user", "external_subject"), String.class);
        var userId = DSL.field(DSL.name("identity_user", "id"), UUID.class);
        var membershipUserId = DSL.field(DSL.name("membership", "user_id"), UUID.class);
        var membershipBusinessId = DSL.field(DSL.name("membership", "business_id"), UUID.class);
        var membershipStatus = DSL.field(DSL.name("membership", "status"), String.class);
        return dsl.fetchExists(DSL.selectOne()
                .from(identities)
                .join(users).on(identitySubject.eq(userSubject))
                .join(memberships).on(membershipUserId.eq(userId))
                .where(identityPhoneHash.eq(phoneHash)
                        .and(membershipBusinessId.eq(businessId))
                        .and(membershipStatus.eq("ACTIVE"))));
    }

    @Override
    @Transactional
    public void bind(String phoneHash, String externalSubject) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var updated = dsl.update(IDENTITIES)
                .set(EXTERNAL_SUBJECT, externalSubject)
                .set(UPDATED_AT, now)
                .where(PHONE_HASH.eq(phoneHash).and(EXTERNAL_SUBJECT.eq(externalSubject)))
                .execute();
        if (updated > 0) {
            return;
        }
        try {
            dsl.insertInto(IDENTITIES)
                    .columns(PHONE_HASH, EXTERNAL_SUBJECT, CREATED_AT, UPDATED_AT)
                    .values(phoneHash, externalSubject, now, now)
                    .execute();
        } catch (org.jooq.exception.DataAccessException exception) {
            throw new IllegalStateException("phone identity is already bound", exception);
        }
    }
}
