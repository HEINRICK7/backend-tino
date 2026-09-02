package com.tino.backend.businessunderstanding.application.usecase;

import com.tino.backend.businessunderstanding.domain.model.ActivityCode;
import java.util.Arrays;
import java.util.List;

public final class GetActivityCatalog {
    public List<ActivityCode> execute() {
        return Arrays.asList(ActivityCode.values());
    }
}
