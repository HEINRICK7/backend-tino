package com.tino.backend.credit.application.port.out.customer;

import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.UUID;

/**
 * Durable handoff from the authoritative credit transaction to customer
 * communication channels. Implementations must only enqueue work locally; a
 * remote notification provider must never be called from the credit write.
 */
@FunctionalInterface
public interface CustomerUpdateNotificationPort {
    void enqueue(CustomerUpdate update);

    record CustomerUpdate(
            BusinessId businessId,
            UUID customerId,
            UUID activityId,
            String kind,
            Instant occurredAt) {}
}
