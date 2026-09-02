package com.tino.backend.reconciliation.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.reconciliation.application.exception.ReconciliationNotFoundException;
import com.tino.backend.reconciliation.application.model.ReconciliationItemView;
import com.tino.backend.reconciliation.application.model.ReconciliationRunView;
import com.tino.backend.reconciliation.application.port.out.ReconciliationRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.UUID;

public final class GetReconciliationRun {
    private final BusinessAuthorization authorization;
    private final ReconciliationRepository reconciliations;
    public GetReconciliationRun(BusinessAuthorization authorization, ReconciliationRepository reconciliations) {
        this.authorization = authorization; this.reconciliations = reconciliations;
    }
    public ReconciliationRunView execute(UUID userId, BusinessId businessId, UUID runId) {
        return authorization.execute(userId, businessId, authorizedBusiness -> {
            var run = reconciliations.findById(authorizedBusiness, runId)
                    .orElseThrow(ReconciliationNotFoundException::new);
            var items = reconciliations.findItems(authorizedBusiness, run.id()).stream()
                    .map(item -> new ReconciliationItemView(item.id(), item.providerEventId(), item.providerPaymentId(),
                            item.paymentId(), item.amount(), item.currency(), item.providerStatus(), item.classification().name()))
                    .toList();
            return new ReconciliationRunView(run.id(), run.businessId().value(), run.provider(), run.state().name(),
                    run.totalCount(), run.matchedCount(), run.discrepancyCount(), run.createdAt(), run.completedAt(), items);
        });
    }
}
