package com.tino.backend.business.application.port.in;

import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.util.Optional;

/** Read-only contract for tenant-bound downstream channels. */
public interface BusinessPixReader {
    Optional<PixView> read(BusinessId businessId);

    /** Returns the code with the current amount when positive. */
    Optional<PixView> readForAmount(BusinessId businessId, BigDecimal amount);

    record PixView(boolean enabled, String key, String copyPaste) {
        public PixView {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Pix key is required");
            }
            if (copyPaste == null || copyPaste.isBlank()) {
                throw new IllegalArgumentException("Pix copy and paste is required");
            }
        }
    }
}
