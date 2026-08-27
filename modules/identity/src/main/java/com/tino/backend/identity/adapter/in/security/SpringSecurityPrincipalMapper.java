package com.tino.backend.identity.adapter.in.security;

import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.domain.model.ExternalSubject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;

/** Converts a validated JWT to the minimal framework-independent principal. */
public final class SpringSecurityPrincipalMapper implements Converter<Jwt, AuthenticatedPrincipal> {
    @Override
    public AuthenticatedPrincipal convert(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return null;
        }
        try {
            return new AuthenticatedPrincipal(new ExternalSubject(jwt.getSubject()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
