package com.tino.backend.identity.application.usecase;

import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException;
import com.tino.backend.identity.application.exception.UserResolutionException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.out.ExternalSubjectAlreadyExistsException;
import com.tino.backend.identity.application.port.out.UserRepository;
import com.tino.backend.identity.domain.model.ExternalSubject;
import com.tino.backend.identity.domain.model.User;
import com.tino.backend.identity.domain.model.UserId;
import com.tino.backend.identity.domain.model.UserStatus;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Finds or just-in-time provisions the authenticated global user.
 *
 * <p>The database unique constraint is the authority for first-access races:
 * a losing insert is translated by the port and then resolved by a fresh
 * lookup. No process-local or distributed lock is involved.</p>
 */
public final class ResolveAuthenticatedUser {
    private final UserRepository users;
    private final UuidGenerator ids;
    private final Clock clock;

    public ResolveAuthenticatedUser(UserRepository users, UuidGenerator ids, Clock clock) {
        this.users = Objects.requireNonNull(users, "users");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public User execute(AuthenticatedPrincipal principal) {
        var subject = validate(principal);
        var existing = users.findByExternalSubject(subject);
        if (existing.isPresent()) {
            return requireActive(existing.orElseThrow());
        }

        var now = Instant.now(clock);
        var candidate = User.active(new UserId(ids.next()), subject, now, now);
        try {
            return requireActive(users.insert(candidate));
        } catch (ExternalSubjectAlreadyExistsException race) {
            return users.findByExternalSubject(subject)
                    .map(ResolveAuthenticatedUser::requireActive)
                    .orElseThrow(() -> new UserResolutionException(race));
        }
    }

    private static ExternalSubject validate(AuthenticatedPrincipal principal) {
        if (principal == null || principal.externalSubject() == null) {
            throw new InvalidAuthenticatedPrincipalException();
        }
        var subject = principal.externalSubject();
        if (subject.value() == null || subject.value().isBlank()) {
            throw new InvalidAuthenticatedPrincipalException();
        }
        return subject;
    }

    private static User requireActive(User user) {
        if (user == null) {
            throw new UserResolutionException();
        }
        if (user.status() == UserStatus.DISABLED) {
            throw new DisabledUserException();
        }
        return user;
    }
}
