package com.tino.backend.bootstrap.application.model;

import com.tino.backend.bootstrap.domain.model.BootstrapState;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/** Read-only startup context composed from Identity, Business, and Device authorities. */
public record BootstrapContext(
        BootstrapState state,
        BootstrapUserSummary user,
        List<BootstrapBusinessSummary> businesses,
        BootstrapBusinessSummary selectedBusiness,
        BootstrapInstallationSummary installation,
        @JsonProperty("business_understanding")
        com.tino.backend.businessunderstanding.application.model.BusinessUnderstandingView businessUnderstanding) {
    public BootstrapContext {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(businesses, "businesses");
        businesses = List.copyOf(businesses);
    }

    public BootstrapContext(
            BootstrapState state,
            BootstrapUserSummary user,
            List<BootstrapBusinessSummary> businesses,
            BootstrapBusinessSummary selectedBusiness,
            BootstrapInstallationSummary installation) {
        this(state, user, businesses, selectedBusiness, installation, null);
    }
}
