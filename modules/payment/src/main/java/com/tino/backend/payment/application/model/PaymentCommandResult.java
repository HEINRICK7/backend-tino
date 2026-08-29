package com.tino.backend.payment.application.model;

public record PaymentCommandResult(PaymentView payment, boolean replayed) {}
