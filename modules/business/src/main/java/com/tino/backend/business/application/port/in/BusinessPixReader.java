package com.tino.backend.business.application.port.in;

import com.tino.backend.shared.kernel.BusinessId;
import java.util.Optional;

/** Read-only contract for tenant-bound downstream channels. */
@FunctionalInterface
public interface BusinessPixReader {
    Optional<PixView> read(BusinessId businessId);

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
