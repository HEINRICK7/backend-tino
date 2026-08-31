package com.tino.backend.external.application.model;

import com.tino.backend.external.domain.model.ExternalConnectionStatus;
import com.tino.backend.external.domain.model.ExternalDataSourceType;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.UUID;

/** The business-level source view. Businesses without a connection remain TINO_NATIVE. */
public record BusinessDataSource(BusinessId businessId, ExternalDataSourceType sourceType,
        String provider, UUID connectionId, ExternalConnectionStatus status) {}
