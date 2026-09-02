package com.tino.backend.businessunderstanding.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.businessunderstanding.application.port.out.BusinessUnderstandingRepository;
import com.tino.backend.businessunderstanding.domain.model.BusinessOperatingMode;
import com.tino.backend.businessunderstanding.domain.model.OperatingMode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.tino.backend.shared.kernel.BusinessId;

public final class ReplaceOperatingModes {
    private final BusinessAuthorization authorization;
    private final BusinessUnderstandingRepository repository;
    private final Clock clock;

    public ReplaceOperatingModes(BusinessAuthorization authorization,
            BusinessUnderstandingRepository repository, Clock clock) {
        this.authorization = authorization;
        this.repository = repository;
        this.clock = clock;
    }

    public void execute(UUID userId, BusinessId businessId, List<OperatingMode> modes) {
        Objects.requireNonNull(modes, "modes");
        var unique = new LinkedHashMap<OperatingMode, BusinessOperatingMode>();
        for (var mode : modes) {
            Objects.requireNonNull(mode, "mode");
            unique.putIfAbsent(mode, BusinessOperatingMode.declared(mode));
        }
        authorization.execute(userId, businessId,
                authorized -> {
                    repository.replaceOperatingModes(authorized, List.copyOf(unique.values()), clock.instant());
                    return null;
                });
    }
}
