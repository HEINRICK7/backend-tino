package com.tino.backend.sync.application.model;

import java.util.UUID;

public record SyncEventRejection(
        UUID eventId, String code, boolean retryable, String message) {}
