package com.tino.backend.messaging.domain.model;

public enum MessageStatus {
    QUEUED, PROCESSING, SENT, FAILED, DEAD_LETTER;

    public boolean canTransitionTo(MessageStatus target) {
        return switch (this) {
            case QUEUED -> target == PROCESSING;
            case PROCESSING -> target == SENT || target == FAILED || target == DEAD_LETTER;
            case FAILED -> target == PROCESSING || target == DEAD_LETTER;
            case SENT, DEAD_LETTER -> false;
        };
    }
}
