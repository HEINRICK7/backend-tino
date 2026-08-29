package com.tino.backend.reconciliation.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ReconciliationItemView(UUID id, String providerEventId, String providerPaymentId,
        UUID paymentId, BigDecimal amount, String currency, String providerStatus, String classification) {}
