package com.tino.backend.business.application.port.out;

import com.tino.backend.business.domain.model.BusinessPixConfiguration;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Optional;

public interface BusinessPixConfigurationRepository {
    Optional<BusinessPixConfiguration> find(BusinessId businessId);

    void upsert(BusinessPixConfiguration configuration);

    void delete(BusinessId businessId);
}
