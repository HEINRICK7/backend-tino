package com.tino.backend.business.application.port.in;

import com.tino.backend.shared.kernel.BusinessId;

/** Public Business capability for configuring its explicit data source. */
public interface BusinessDataSourceConfiguration {
    String readSourceType(BusinessId businessId);

    void updateSourceType(BusinessId businessId, String sourceType);
}
