package com.tino.backend.businessunderstanding.application.port.in;

import com.tino.backend.businessunderstanding.application.model.BusinessUnderstandingView;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.UUID;

@FunctionalInterface
public interface BusinessUnderstandingReader {
    BusinessUnderstandingView read(UUID authenticatedUserId, BusinessId businessId);
}
