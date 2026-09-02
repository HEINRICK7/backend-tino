package com.tino.backend.business.application.port.in;

import com.tino.backend.shared.kernel.BusinessId;
import java.util.UUID;
import java.util.function.Function;

/**
 * Public composition contract for operations that require an authorized
 * Business tenant.
 *
 * <p>The callback is invoked only after the authenticated internal User has
 * an ACTIVE membership and the requested Business is ACTIVE. The
 * implementation establishes the transaction-local tenant context before
 * invoking it.</p>
 */
@FunctionalInterface
public interface BusinessAuthorization {
    <T> T execute(UUID authenticatedUserId, BusinessId requestedBusinessId,
            Function<BusinessId, T> authorizedOperation);
}
