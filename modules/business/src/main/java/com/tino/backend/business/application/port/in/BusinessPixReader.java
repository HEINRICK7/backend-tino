package com.tino.backend.business.application.port.in;

import com.tino.backend.business.domain.model.BusinessPixConfiguration;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Optional;

/** Read-only contract for tenant-bound customer channels. */
@FunctionalInterface
public interface BusinessPixReader {
    Optional<BusinessPixConfiguration> read(BusinessId businessId);
}
