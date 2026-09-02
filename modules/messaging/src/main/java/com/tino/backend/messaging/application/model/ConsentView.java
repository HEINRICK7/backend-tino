package com.tino.backend.messaging.application.model;
public record ConsentView(String channel, String purpose, boolean granted, long version) {}
