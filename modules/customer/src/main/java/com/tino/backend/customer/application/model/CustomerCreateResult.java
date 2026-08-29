package com.tino.backend.customer.application.model;

public record CustomerCreateResult(CustomerView customer, boolean replayed) {}
