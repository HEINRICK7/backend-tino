package com.tino.backend.customer.application.port.out;

import com.tino.backend.customer.domain.model.Customer;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {
    Optional<Customer> find(BusinessId businessId, UUID customerId);
    List<Customer> findActive(BusinessId businessId);
    void insert(Customer customer);
    void update(Customer customer);
    void deleteUnclaimed(Customer customer);
    Optional<IdempotencyRecord> findIdempotency(BusinessId businessId, String key);
    boolean insertIdempotency(BusinessId businessId, String key, String fingerprint,
            UUID customerId, java.time.Instant createdAt);

    record IdempotencyRecord(String fingerprint, UUID customerId) {}
}
