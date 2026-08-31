package com.tino.backend.external.application.model;

import com.tino.backend.external.domain.model.ExternalBusinessConnection;

public record ConnectionRegistrationResult(ExternalBusinessConnection connection, boolean replayed) {}
