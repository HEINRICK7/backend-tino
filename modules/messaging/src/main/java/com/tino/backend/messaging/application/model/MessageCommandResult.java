package com.tino.backend.messaging.application.model;
public record MessageCommandResult(MessageView message, boolean replayed) {}
