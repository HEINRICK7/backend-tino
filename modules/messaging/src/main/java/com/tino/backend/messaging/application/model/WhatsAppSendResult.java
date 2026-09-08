package com.tino.backend.messaging.application.model;

public record WhatsAppSendResult(WhatsAppDeliveryView message, boolean replayed) {}
