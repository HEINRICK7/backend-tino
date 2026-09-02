package com.tino.backend.businessunderstanding.domain.model;

import java.util.Objects;

public record BusinessActivity(ActivityCode code, String customLabel) {
    public BusinessActivity {
        Objects.requireNonNull(code, "code");
        if (code == ActivityCode.OTHER) {
            if (customLabel == null || customLabel.isBlank()) {
                throw new IllegalArgumentException("custom label is required for OTHER");
            }
            customLabel = customLabel.trim();
        } else if (customLabel != null) {
            throw new IllegalArgumentException("custom label is only valid for OTHER");
        }
    }
}
