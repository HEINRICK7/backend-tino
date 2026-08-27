package com.tino.backend.business.application.usecase;

import com.tino.backend.business.application.model.AuthorizedBusinessContext;
import com.tino.backend.business.domain.model.UserId;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.util.Objects;
import java.util.function.Function;

/** Establishes tenant context only after ResolveBusinessAccess authorizes the request. */
public final class ExecuteAuthorizedBusinessOperation {
    private final ResolveBusinessAccess access;
    private final TenantContextExecutor tenantContext;

    public ExecuteAuthorizedBusinessOperation(
            ResolveBusinessAccess access, TenantContextExecutor tenantContext) {
        this.access = Objects.requireNonNull(access, "access");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
    }

    public <T> T execute(
            UserId userId,
            BusinessId requestedBusinessId,
            Function<AuthorizedBusinessContext, T> operation) {
        Objects.requireNonNull(operation, "operation");
        var authorized = access.execute(userId, requestedBusinessId);
        return tenantContext.execute(authorized.businessId(), () -> operation.apply(authorized));
    }
}
