package com.tino.backend.catalog.application.model;

import java.util.UUID;

public record ExternalProductProjectionResult(UUID tinoProductId, boolean created, boolean updated, boolean deactivated) {}
