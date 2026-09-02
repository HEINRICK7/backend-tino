package com.tino.backend.businessunderstanding.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.businessunderstanding.application.port.out.BusinessUnderstandingRepository;
import com.tino.backend.businessunderstanding.domain.model.BusinessActivity;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ReplaceBusinessActivities {
    private final BusinessAuthorization authorization;
    private final BusinessUnderstandingRepository repository;
    private final Clock clock;

    public ReplaceBusinessActivities(BusinessAuthorization authorization,
            BusinessUnderstandingRepository repository, Clock clock) {
        this.authorization = authorization;
        this.repository = repository;
        this.clock = clock;
    }

    public void execute(UUID userId, BusinessId businessId, List<BusinessActivity> activities) {
        Objects.requireNonNull(activities, "activities");
        var unique = new LinkedHashMap<com.tino.backend.businessunderstanding.domain.model.ActivityCode, BusinessActivity>();
        for (var activity : activities) {
            Objects.requireNonNull(activity, "activity");
            var previous = unique.putIfAbsent(activity.code(), activity);
            if (previous != null && !Objects.equals(previous.customLabel(), activity.customLabel())) {
                throw new IllegalArgumentException("duplicate activity has conflicting labels");
            }
        }
        authorization.execute(userId, businessId,
                authorized -> {
                    repository.replaceActivities(authorized, List.copyOf(unique.values()), clock.instant());
                    return null;
                });
    }
}
