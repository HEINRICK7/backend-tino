package com.tino.backend.identity.adapter.in.security;

import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication adapter whose principal is framework-independent and whose
 * credentials do not retain the bearer value.
 */
public final class AuthenticatedPrincipalAuthenticationToken extends AbstractAuthenticationToken {
    private static final long serialVersionUID = 1L;

    private final transient AuthenticatedPrincipal principal;

    public AuthenticatedPrincipalAuthenticationToken(
            AuthenticatedPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities == null ? List.of() : authorities);
        this.principal = Objects.requireNonNull(principal, "principal");
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
