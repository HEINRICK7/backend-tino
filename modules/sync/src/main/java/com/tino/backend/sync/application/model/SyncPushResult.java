package com.tino.backend.sync.application.model;

import java.util.List;
import java.util.UUID;

public record SyncPushResult(
        List<UUID> acknowledgedEventIds,
        List<UUID> alreadyProcessedEventIds,
        List<SyncEventRejection> rejected) {
    public SyncPushResult {
        acknowledgedEventIds = List.copyOf(acknowledgedEventIds);
        alreadyProcessedEventIds = List.copyOf(alreadyProcessedEventIds);
        rejected = List.copyOf(rejected);
    }
}
