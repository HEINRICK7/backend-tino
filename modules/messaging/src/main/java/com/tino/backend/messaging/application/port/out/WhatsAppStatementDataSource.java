package com.tino.backend.messaging.application.port.out;

import com.tino.backend.messaging.application.model.WhatsAppStatementSnapshot;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Optional;
import java.util.UUID;

/** Read-only boundary from authoritative customer/credit facts into messaging. */
public interface WhatsAppStatementDataSource {
    Optional<WhatsAppStatementSnapshot> find(BusinessId businessId, UUID accountId);
}
