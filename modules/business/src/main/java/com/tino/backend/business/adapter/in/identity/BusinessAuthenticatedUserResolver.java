package com.tino.backend.business.adapter.in.identity;

import com.tino.backend.business.application.model.AuthenticatedUser;
import com.tino.backend.business.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.business.application.exception.InactiveAuthenticatedUserException;
import com.tino.backend.business.domain.model.UserId;
import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Adapts the public Identity application contract to Business's local user snapshot. */
@Component
public final class BusinessAuthenticatedUserResolver implements AuthenticatedUserResolver {
    private final com.tino.backend.identity.application.port.in.AuthenticatedUserResolver identityUsers;

    public BusinessAuthenticatedUserResolver(
            com.tino.backend.identity.application.port.in.AuthenticatedUserResolver identityUsers) {
        this.identityUsers = Objects.requireNonNull(identityUsers, "identityUsers");
    }

    @Override
    public AuthenticatedUser resolve(String externalSubject) {
        try {
            var identityUser = identityUsers.resolve(AuthenticatedPrincipal.fromSubject(externalSubject));
            if (!identityUser.active()) {
                throw new InactiveAuthenticatedUserException();
            }
            return new AuthenticatedUser(new UserId(identityUser.userId()), true);
        } catch (DisabledUserException | InvalidAuthenticatedPrincipalException exception) {
            throw new InactiveAuthenticatedUserException();
        }
    }
}
