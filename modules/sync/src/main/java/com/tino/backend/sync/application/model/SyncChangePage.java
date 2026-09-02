package com.tino.backend.sync.application.model;

import java.util.List;

/** Bounded page and the server sequence position acknowledged by that page. */
public record SyncChangePage(List<SyncChange> changes, long nextCursor) {
    public SyncChangePage {
        changes = List.copyOf(changes);
        if (nextCursor < 0) {
            throw new IllegalArgumentException("nextCursor must not be negative");
        }
    }
}
