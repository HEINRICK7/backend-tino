package com.tino.backend.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.out.ExternalSubjectAlreadyExistsException;
import com.tino.backend.identity.application.port.out.UserRepository;
import com.tino.backend.identity.domain.model.ExternalSubject;
import com.tino.backend.identity.domain.model.User;
import com.tino.backend.identity.domain.model.UserId;
import com.tino.backend.identity.domain.model.UserStatus;
import com.tino.backend.shared.kernel.UuidV7Generator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResolveAuthenticatedUserTest {
    private static final Instant NOW = Instant.parse("2026-08-26T12:34:56.123456Z");

    @Test
    void sameSubjectIsIdempotentAndNewUserIsActive() {
        var repository = new InMemoryUserRepository();
        var useCase = useCase(repository);
        var principal = principal("opaque-subject-1");

        var first = useCase.execute(principal);
        var second = useCase.execute(principal);

        assertThat(second).isEqualTo(first);
        assertThat(first.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(repository.values()).singleElement().isEqualTo(first);
    }

    @Test
    void differentSubjectsResolveToDifferentUsers() {
        var repository = new InMemoryUserRepository();
        var useCase = useCase(repository);

        var first = useCase.execute(principal("opaque-subject-1"));
        var second = useCase.execute(principal("opaque-subject-2"));

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(repository.values()).hasSize(2);
    }

    @Test
    void disabledUserIsRejectedExplicitly() {
        var subject = new ExternalSubject("disabled-subject");
        var disabled = new User(
                new UserId(UUID.randomUUID()), subject, UserStatus.DISABLED, NOW, NOW);
        var repository = new InMemoryUserRepository();
        repository.put(disabled);

        assertThatThrownBy(() -> useCase(repository).execute(new AuthenticatedPrincipal(subject)))
                .isInstanceOf(DisabledUserException.class);
    }

    @Test
    void nullPrincipalFailsClosed() {
        assertThatThrownBy(() -> useCase(new InMemoryUserRepository()).execute(null))
                .isInstanceOf(InvalidAuthenticatedPrincipalException.class);
    }

    @Test
    void loserOfCreateRaceResolvesTheCommittedWinner() {
        var winner = User.active(
                new UserId(UUID.randomUUID()), new ExternalSubject("race-subject"), NOW, NOW);
        var repository = new RaceRepository(winner);
        var resolved = useCase(repository).execute(principal("race-subject"));

        assertThat(resolved).isEqualTo(winner);
        assertThat(repository.insertAttempts).isEqualTo(1);
    }

    private static ResolveAuthenticatedUser useCase(UserRepository repository) {
        return new ResolveAuthenticatedUser(
                repository, new UuidV7Generator(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AuthenticatedPrincipal principal(String subject) {
        return new AuthenticatedPrincipal(new ExternalSubject(subject));
    }

    private static final class InMemoryUserRepository implements UserRepository {
        private final Map<ExternalSubject, User> users = new HashMap<>();

        @Override
        public Optional<User> findByExternalSubject(ExternalSubject externalSubject) {
            return Optional.ofNullable(users.get(externalSubject));
        }

        @Override
        public User insert(User user) throws ExternalSubjectAlreadyExistsException {
            if (users.containsKey(user.externalSubject())) {
                throw new ExternalSubjectAlreadyExistsException(null);
            }
            users.put(user.externalSubject(), user);
            return user;
        }

        void put(User user) {
            users.put(user.externalSubject(), user);
        }

        ArrayList<User> values() {
            return new ArrayList<>(users.values());
        }
    }

    private static final class RaceRepository implements UserRepository {
        private final User winner;
        private int insertAttempts;

        private RaceRepository(User winner) {
            this.winner = winner;
        }

        @Override
        public Optional<User> findByExternalSubject(ExternalSubject externalSubject) {
            return insertAttempts == 0 ? Optional.empty() : Optional.of(winner);
        }

        @Override
        public User insert(User user) throws ExternalSubjectAlreadyExistsException {
            insertAttempts++;
            throw new ExternalSubjectAlreadyExistsException(null);
        }
    }
}
