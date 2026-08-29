package com.tino.backend.sync.application.usecase;

import com.tino.backend.sync.application.port.in.SyncEventHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable registry keyed by the handler-owned event type and schema version. */
public final class SyncEventHandlerRegistry {
    private final Map<Key, SyncEventHandler> handlers;

    public SyncEventHandlerRegistry(List<SyncEventHandler> handlers) {
        var registered = new HashMap<Key, SyncEventHandler>();
        for (var handler : handlers) {
            Objects.requireNonNull(handler, "handler");
            var key = new Key(handler.eventType(), handler.schemaVersion());
            if (registered.putIfAbsent(key, handler) != null) {
                throw new IllegalStateException("duplicate sync handler: " + key);
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    public SyncEventHandler find(String eventType, int schemaVersion) {
        return handlers.get(new Key(eventType, schemaVersion));
    }

    private record Key(String eventType, int schemaVersion) {
        private Key {
            if (eventType == null || eventType.isBlank() || schemaVersion <= 0) {
                throw new IllegalArgumentException("invalid sync handler key");
            }
        }
    }
}
