package com.tino.backend.customer.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.customer.application.exception.CustomerNotFoundException;
import com.tino.backend.customer.application.model.CustomerView;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.customer.domain.model.Customer;
import com.tino.backend.customer.domain.model.CustomerStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class UpdateCustomer {
    private final BusinessAuthorization authorization;
    private final CustomerRepository customers;
    private final Clock clock;

    public UpdateCustomer(BusinessAuthorization authorization, CustomerRepository customers,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.customers = Objects.requireNonNull(customers, "customers");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CustomerView execute(UUID userId, BusinessId businessId, UUID customerId,
            String name, String nickname, String phone) {
        return authorization.execute(userId, businessId, authorized -> {
            var current = customers.find(authorized, customerId)
                    .orElseThrow(CustomerNotFoundException::new);
            if (current.status() != CustomerStatus.ACTIVE) {
                throw new CustomerNotFoundException();
            }
            var updated = new Customer(current.id(), authorized, name, nickname, phone,
                    current.status(), current.createdAt(), Instant.now(clock));
            customers.update(updated);
            return CustomerView.from(updated);
        });
    }
}
