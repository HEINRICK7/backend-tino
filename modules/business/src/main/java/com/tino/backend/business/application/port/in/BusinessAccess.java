package com.tino.backend.business.application.port.in;

import com.tino.backend.shared.kernel.BusinessId;
import java.util.UUID;

/** Public authorization boundary that intentionally returns no internal business model. */
@FunctionalInterface
public interface BusinessAccess {
    BusinessId require(UUID userId, BusinessId requestedBusinessId);
}
