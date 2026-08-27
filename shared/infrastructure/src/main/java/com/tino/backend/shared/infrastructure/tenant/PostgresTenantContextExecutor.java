package com.tino.backend.shared.infrastructure.tenant;

import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL implementation of the kernel tenant-operation contract.
 *
 * <p>The tenant value is set with PostgreSQL's transaction-local
 * {@code set_config(..., true)}. A new transaction is used for each operation,
 * so the setting is discarded on both commit and rollback before the pooled
 * connection can be returned to its caller.</p>
 */
@Component
public final class PostgresTenantContextExecutor implements TenantContextExecutor {
    private static final String SET_TENANT_CONTEXT =
            "select set_config('app.business_id', ?, true)";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresTenantContextExecutor(
            DataSource dataSource, PlatformTransactionManager transactionManager) {
        jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
        transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public <T> T execute(BusinessId businessId, Supplier<T> operation) {
        Objects.requireNonNull(businessId, "businessId");
        Objects.requireNonNull(operation, "operation");
        return transactions.execute(status -> {
            setTenantContext(businessId);
            return operation.get();
        });
    }

    private void setTenantContext(BusinessId businessId) {
        jdbc.queryForObject(SET_TENANT_CONTEXT, String.class, businessId.value().toString());
    }
}
