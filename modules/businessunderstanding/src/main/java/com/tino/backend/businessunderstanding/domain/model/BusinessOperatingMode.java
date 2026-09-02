package com.tino.backend.businessunderstanding.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record BusinessOperatingMode(
        OperatingMode mode, OperatingModeSource source, BigDecimal confidence) {
    public BusinessOperatingMode {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(source, "source");
        if (confidence != null && (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("confidence must be between zero and one");
        }
    }

    public static BusinessOperatingMode declared(OperatingMode mode) {
        return new BusinessOperatingMode(mode, OperatingModeSource.USER_DECLARED, BigDecimal.ONE);
    }
}
