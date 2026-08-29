package com.tino.backend.customer.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.customer.application.exception.CustomerConflictException;
import com.tino.backend.customer.application.model.CustomerCreateResult;
import com.tino.backend.customer.application.model.CustomerView;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.customer.domain.model.Customer;
import com.tino.backend.customer.domain.model.CustomerStatus;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CreateCustomer {
    private final BusinessAuthorization authorization;
    private final CustomerRepository customers;
    private final UuidGenerator ids;
    private final Clock clock;

    public CreateCustomer(BusinessAuthorization authorization, CustomerRepository customers,
            UuidGenerator ids, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.customers = Objects.requireNonNull(customers, "customers");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CustomerCreateResult execute(UUID userId, BusinessId businessId, String name,
            String nickname, String phone, String idempotencyKey, String fingerprint) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(fingerprint, "fingerprint");
        return authorization.execute(userId, businessId, authorizedBusiness -> {
            var existing = customers.findIdempotency(authorizedBusiness, idempotencyKey);
            if (existing.isPresent()) {
                var record = existing.orElseThrow();
                if (!record.fingerprint().equals(fingerprint)) {
                    throw new CustomerConflictException();
                }
                return new CustomerCreateResult(
                        CustomerView.from(customers.find(authorizedBusiness, record.customerId())
                                .orElseThrow()), true);
            }
            var now = Instant.now(clock);
            var customer = new Customer(ids.next(), authorizedBusiness, name, nickname, phone,
                    CustomerStatus.ACTIVE, now, now);
            customers.insert(customer);
            if (!customers.insertIdempotency(authorizedBusiness, idempotencyKey, fingerprint,
                    customer.id(), now)) {
                customers.deleteUnclaimed(customer);
                var concurrent = customers.findIdempotency(authorizedBusiness, idempotencyKey)
                        .orElseThrow();
                if (!concurrent.fingerprint().equals(fingerprint)) {
                    throw new CustomerConflictException();
                }
                return new CustomerCreateResult(
                        CustomerView.from(customers.find(authorizedBusiness, concurrent.customerId())
                                .orElseThrow()), true);
            }
            return new CustomerCreateResult(CustomerView.from(customer), false);
        });
    }
}
