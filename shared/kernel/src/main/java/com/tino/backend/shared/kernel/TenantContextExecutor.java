package com.tino.backend.shared.kernel;

import java.util.function.Supplier;

/**
 * Runs one operation with an already-resolved business tenant in scope.
 *
 * <p>The contract intentionally does not expose transactions, JDBC, jOOQ, or
 * session-variable details. Infrastructure adapters provide the transaction
 * and database implementation.</p>
 */
@FunctionalInterface
public interface TenantContextExecutor {
    <T> T execute(BusinessId businessId, Supplier<T> operation);
}
