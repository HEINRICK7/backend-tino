package com.tino.backend.identity.adapter.out.persistence;

import com.tino.backend.identity.application.port.out.ExternalSubjectAlreadyExistsException;
import com.tino.backend.identity.application.port.out.UserPersistenceException;
import com.tino.backend.identity.application.port.out.UserRepository;
import com.tino.backend.identity.domain.model.ExternalSubject;
import com.tino.backend.identity.domain.model.User;
import com.tino.backend.identity.domain.model.UserId;
import com.tino.backend.identity.domain.model.UserStatus;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL/jOOQ implementation of the identity persistence port.
 *
 * <p>Generated jOOQ metadata is kept in the infrastructure build boundary;
 * this adapter uses a deliberately tiny table description so identity's
 * application contracts never carry generated records.</p>
 */
@Repository
public class JooqUserRepository implements UserRepository {
    private static final Table<?> USERS = DSL.table(DSL.name("public", "users"));
    private static final Field<java.util.UUID> ID = DSL.field(DSL.name("id"), java.util.UUID.class);
    private static final Field<String> EXTERNAL_SUBJECT =
            DSL.field(DSL.name("external_subject"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<OffsetDateTime> CREATED_AT =
            DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT =
            DSL.field(DSL.name("updated_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public JooqUserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByExternalSubject(ExternalSubject externalSubject) {
        try {
            return dsl.select(ID, EXTERNAL_SUBJECT, STATUS, CREATED_AT, UPDATED_AT)
                    .from(USERS)
                    .where(EXTERNAL_SUBJECT.eq(externalSubject.value()))
                    .fetchOptional()
                    .map(JooqUserRepository::toDomain);
        } catch (org.jooq.exception.DataAccessException exception) {
            throw new UserPersistenceException(exception);
        }
    }

    @Override
    @Transactional
    public User insert(User user) throws ExternalSubjectAlreadyExistsException {
        try {
            dsl.insertInto(USERS)
                    .columns(ID, EXTERNAL_SUBJECT, STATUS, CREATED_AT, UPDATED_AT)
                    .values(
                            user.id().value(),
                            user.externalSubject().value(),
                            user.status().name(),
                            toDatabaseTime(user.createdAt()),
                            toDatabaseTime(user.updatedAt()))
                    .execute();
            return user;
        } catch (org.jooq.exception.DataAccessException exception) {
            if (isUniqueViolation(exception)) {
                throw new ExternalSubjectAlreadyExistsException(exception);
            }
            throw new UserPersistenceException(exception);
        }
    }

    private static User toDomain(Record record) {
        var id = record.get(ID, java.util.UUID.class);
        var externalSubject = record.get(EXTERNAL_SUBJECT, String.class);
        var status = record.get(STATUS, String.class);
        var createdAt = record.get(CREATED_AT, OffsetDateTime.class);
        var updatedAt = record.get(UPDATED_AT, OffsetDateTime.class);
        try {
            return new User(
                    new UserId(id),
                    new ExternalSubject(externalSubject),
                    UserStatus.valueOf(status),
                    toInstant(createdAt),
                    toInstant(updatedAt));
        } catch (RuntimeException exception) {
            throw new UserPersistenceException(exception);
        }
    }

    private static OffsetDateTime toDatabaseTime(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime value) {
        if (value == null) {
            throw new IllegalStateException("user timestamp is null");
        }
        return value.toInstant();
    }

    private static boolean isUniqueViolation(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
