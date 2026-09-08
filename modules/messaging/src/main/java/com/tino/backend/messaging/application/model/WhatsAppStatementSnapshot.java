package com.tino.backend.messaging.application.model;

import com.tino.backend.messaging.domain.model.DebtStatementView;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.UUID;

/** Authoritative facts read for one render; presentation never recalculates them. */
public record WhatsAppStatementSnapshot(
        BusinessId businessId,
        UUID accountId,
        UUID customerId,
        String recipientPhone,
        long accountVersion,
        DebtStatementView statement) {}
