package com.tino.backend.bootstrap.application.model;

import com.tino.backend.bootstrap.domain.model.BootstrapState;
import java.util.List;
import java.util.Objects;

/** Read-only startup context composed from Identity, Business, and Device authorities. */
public record BootstrapContext(
        BootstrapState state,
        BootstrapUserSummary user,
        List<BootstrapBusinessSummary> businesses,
        BootstrapBusinessSummary selectedBusiness,
        BootstrapInstallationSummary installation) {
    public BootstrapContext {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(businesses, "businesses");
        businesses = List.copyOf(businesses);
    }
}
