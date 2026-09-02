package com.tino.backend.reconciliation;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.payment.application.port.out.PaymentRepository;
import com.tino.backend.reconciliation.application.port.out.ReconciliationRepository;
import com.tino.backend.reconciliation.application.usecase.GetReconciliationRun;
import com.tino.backend.reconciliation.application.usecase.ReconcilePayments;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ReconciliationConfiguration {
    @Bean
    ReconcilePayments reconcilePayments(BusinessAuthorization authorization,
            ReconciliationRepository reconciliations, PaymentRepository payments, UuidGenerator ids, Clock clock) {
        return new ReconcilePayments(authorization, reconciliations, payments, ids, clock);
    }
    @Bean
    GetReconciliationRun getReconciliationRun(BusinessAuthorization authorization,
            ReconciliationRepository reconciliations) {
        return new GetReconciliationRun(authorization, reconciliations);
    }
}
