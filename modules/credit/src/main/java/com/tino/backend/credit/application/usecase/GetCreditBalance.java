package com.tino.backend.credit.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.credit.application.exception.CreditCustomerNotFoundException;
import com.tino.backend.credit.application.model.CreditBalanceView;
import com.tino.backend.credit.application.port.out.CreditRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public final class GetCreditBalance {
    private final BusinessAuthorization authorization;
    private final CreditRepository credits;

    public GetCreditBalance(BusinessAuthorization authorization, CreditRepository credits) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.credits = Objects.requireNonNull(credits, "credits");
    }

    public CreditBalanceView execute(UUID userId, BusinessId businessId, UUID customerId) {
        return authorization.execute(userId, businessId, authorizedBusiness -> {
            if (!credits.customerExists(authorizedBusiness, customerId)) {
                throw new CreditCustomerNotFoundException();
            }
            return credits.findAccount(authorizedBusiness, customerId)
                    .map(account -> new CreditBalanceView(authorizedBusiness, customerId, account.id(),
                            account.currency(), account.balance(), account.version()))
                    .orElseGet(() -> new CreditBalanceView(authorizedBusiness, customerId, null,
                            "BRL", BigDecimal.ZERO.setScale(2), 0));
        });
    }
}
