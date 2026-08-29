package com.tino.backend.customer.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.customer.application.exception.CustomerNotFoundException;
import com.tino.backend.customer.application.model.CustomerView;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Objects;
import java.util.UUID;

public final class GetCustomer {
    private final BusinessAuthorization authorization;
    private final CustomerRepository customers;

    public GetCustomer(BusinessAuthorization authorization, CustomerRepository customers) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.customers = Objects.requireNonNull(customers, "customers");
    }

    public CustomerView execute(UUID userId, BusinessId businessId, UUID customerId) {
        return authorization.execute(userId, businessId, authorized -> customers
                .find(authorized, customerId).map(CustomerView::from)
                .orElseThrow(CustomerNotFoundException::new));
    }
}
