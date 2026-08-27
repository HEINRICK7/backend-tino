package com.tino.backend.business.application.port.in;

import com.tino.backend.business.application.model.AuthenticatedUser;

/** Public composition contract for adapting an authenticated subject to Business. */
@FunctionalInterface
public interface AuthenticatedUserResolver {
    AuthenticatedUser resolve(String externalSubject);
}
