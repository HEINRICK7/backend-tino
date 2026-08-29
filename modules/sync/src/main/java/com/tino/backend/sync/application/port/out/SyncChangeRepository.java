package com.tino.backend.sync.application.port.out;

import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.sync.application.model.SyncChangePage;

/** Tenant-scoped sequential change-log read contract. */
public interface SyncChangeRepository {
    SyncChangePage findAfter(BusinessId businessId, long cursor, int limit);
}
