package com.tino.backend.identity.adapter.in.security;

import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import java.util.Collection;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/** Resource Server converter that fails closed when {@code sub} is absent. */
public final class AuthenticatedPrincipalJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {
    private final SpringSecurityPrincipalMapper principalMapper = new SpringSecurityPrincipalMapper();
    private final JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AuthenticatedPrincipal principal = principalMapper.convert(jwt);
        if (principal == null) {
            throw new BadCredentialsException("validated token has no usable subject");
        }
        Collection<GrantedAuthority> grantedAuthorities = authorities.convert(jwt);
        return new AuthenticatedPrincipalAuthenticationToken(principal, grantedAuthorities);
    }
}
