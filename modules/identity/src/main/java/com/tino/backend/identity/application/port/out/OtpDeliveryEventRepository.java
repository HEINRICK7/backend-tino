package com.tino.backend.identity.application.port.out;

import com.tino.backend.identity.domain.model.OtpDeliveryEvent;
import java.util.Optional;

/** Persistence boundary for provider delivery receipts and replay protection. */
public interface OtpDeliveryEventRepository {
    Optional<OtpDeliveryEvent> findByProviderEventId(String providerEventId);

    void insert(OtpDeliveryEvent event);
}
