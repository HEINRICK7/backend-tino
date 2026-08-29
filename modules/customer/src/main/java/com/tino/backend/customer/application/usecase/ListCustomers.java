package com.tino.backend.customer.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.customer.application.model.CustomerView;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListCustomers {
    private final BusinessAuthorization authorization;
    private final CustomerRepository customers;

    public ListCustomers(BusinessAuthorization authorization, CustomerRepository customers) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.customers = Objects.requireNonNull(customers, "customers");
    }

    public List<CustomerView> execute(UUID userId, BusinessId businessId) {
        return authorization.execute(userId, businessId,
                authorized -> customers.findActive(authorized).stream().map(CustomerView::from).toList());
    }
}
