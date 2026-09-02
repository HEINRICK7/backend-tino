package com.tino.backend.businessunderstanding.domain.model;

import java.util.List;

public record BusinessUnderstandingSnapshot(
        List<BusinessActivity> activities, List<BusinessOperatingMode> operatingModes) {
    public BusinessUnderstandingSnapshot {
        activities = List.copyOf(activities);
        operatingModes = List.copyOf(operatingModes);
    }

    public BusinessUnderstandingStatus status() {
        if (activities.isEmpty()) {
            return BusinessUnderstandingStatus.NOT_STARTED;
        }
        return operatingModes.isEmpty()
                ? BusinessUnderstandingStatus.IN_PROGRESS
                : BusinessUnderstandingStatus.READY;
    }

    public NextAction nextAction() {
        return switch (status()) {
            case NOT_STARTED -> NextAction.SELECT_ACTIVITIES;
            case IN_PROGRESS -> NextAction.DEFINE_OPERATING_MODEL;
            case READY -> NextAction.NONE;
        };
    }
}
