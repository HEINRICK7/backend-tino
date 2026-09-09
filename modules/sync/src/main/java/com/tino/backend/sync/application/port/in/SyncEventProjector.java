package com.tino.backend.sync.application.port.in;

import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.sync.domain.model.SyncEvent;
import java.util.UUID;

/** Projects accepted Android events into canonical backend domain models. */
public interface SyncEventProjector {
    void project(UUID authenticatedUserId, BusinessId businessId, SyncEvent event);
}
