package com.tino.backend.customer.application.model;

import com.tino.backend.customer.domain.model.Customer;
import com.tino.backend.customer.domain.model.CustomerStatus;
import java.time.Instant;
import java.util.UUID;

public record CustomerView(
        UUID id,
        UUID businessId,
        String name,
        String nickname,
        String phone,
        CustomerStatus status,
        Instant createdAt,
        Instant updatedAt) {
    public static CustomerView from(Customer customer) {
        return new CustomerView(customer.id(), customer.businessId().value(), customer.name(),
                customer.nickname(), customer.phone(), customer.status(), customer.createdAt(),
                customer.updatedAt());
    }
}
