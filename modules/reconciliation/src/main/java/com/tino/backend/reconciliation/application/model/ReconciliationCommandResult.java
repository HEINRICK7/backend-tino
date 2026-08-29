package com.tino.backend.reconciliation.application.model;

public record ReconciliationCommandResult(ReconciliationRunView run, boolean replayed) {}
