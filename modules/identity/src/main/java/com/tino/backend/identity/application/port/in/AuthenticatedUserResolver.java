package com.tino.backend.identity.application.port.in;

/** Public identity application contract used by modules that need the authenticated User id. */
@FunctionalInterface
public interface AuthenticatedUserResolver {
    AuthenticatedUserSnapshot resolve(AuthenticatedPrincipal principal);
}
