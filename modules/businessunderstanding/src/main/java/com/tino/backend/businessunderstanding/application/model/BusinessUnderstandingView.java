package com.tino.backend.businessunderstanding.application.model;

import com.tino.backend.businessunderstanding.domain.model.BusinessUnderstandingSnapshot;

public record BusinessUnderstandingView(
        String status, boolean activitiesConfigured, boolean operatingModesConfigured, String nextAction) {
    public static BusinessUnderstandingView from(BusinessUnderstandingSnapshot snapshot) {
        return new BusinessUnderstandingView(snapshot.status().name(), !snapshot.activities().isEmpty(),
                !snapshot.operatingModes().isEmpty(), snapshot.nextAction().name());
    }
}
