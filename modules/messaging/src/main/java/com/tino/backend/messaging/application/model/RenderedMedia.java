package com.tino.backend.messaging.application.model;

import java.util.Arrays;
import java.util.Objects;

public record RenderedMedia(String mimeType, byte[] content, String filename, String sha256) {
    public RenderedMedia {
        if (mimeType == null || mimeType.isBlank() || filename == null || filename.isBlank()
                || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid rendered media");
        }
        content = Arrays.copyOf(Objects.requireNonNull(content, "content"), content.length);
        if (content.length == 0) {
            throw new IllegalArgumentException("rendered media cannot be empty");
        }
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
