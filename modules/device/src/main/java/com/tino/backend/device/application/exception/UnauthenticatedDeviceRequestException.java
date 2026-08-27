package com.tino.backend.device.application.exception;

/** Defensive failure for an authenticated adapter invoked without its principal. */
public final class UnauthenticatedDeviceRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UnauthenticatedDeviceRequestException() {
        super("authentication is required");
    }
}
