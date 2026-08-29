package com.tino.backend.sync.application.port.in;

import com.tino.backend.sync.domain.model.SyncEvent;
import com.tino.backend.sync.domain.model.SyncEventEffects;

/** Registered event semantics; controllers never branch on event types. */
public interface SyncEventHandler {
    String eventType();

    int schemaVersion();

    SyncEventEffects handle(SyncEvent event);
}
