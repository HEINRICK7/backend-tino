package com.tino.backend.messaging.application.model;

public record InteractiveAction(String id, String label) {
    public InteractiveAction {
        if (id == null || id.isBlank() || label == null || label.isBlank()) {
            throw new IllegalArgumentException("invalid interactive action");
        }
    }
}
