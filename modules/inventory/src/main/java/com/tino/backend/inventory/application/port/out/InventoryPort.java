package com.tino.backend.inventory.application.port.out;

import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface InventoryPort {
    void receive(BusinessId businessId, UUID receiptId, UUID productId, BigDecimal quantity,
            BigDecimal unitCost, Instant now);
}
