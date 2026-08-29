package com.tino.backend.payment.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.payment.application.exception.PaymentNotFoundException;
import com.tino.backend.payment.application.model.PaymentView;
import com.tino.backend.payment.application.port.out.PaymentRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Objects;
import java.util.UUID;

public final class GetPayment {
    private final BusinessAuthorization authorization;
    private final PaymentRepository payments;

    public GetPayment(BusinessAuthorization authorization, PaymentRepository payments) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.payments = Objects.requireNonNull(payments, "payments");
    }

    public PaymentView execute(UUID userId, BusinessId businessId, UUID paymentId) {
        return authorization.execute(userId, businessId, authorizedBusiness ->
                PaymentView.from(payments.find(authorizedBusiness, paymentId)
                        .orElseThrow(PaymentNotFoundException::new)));
    }
}
