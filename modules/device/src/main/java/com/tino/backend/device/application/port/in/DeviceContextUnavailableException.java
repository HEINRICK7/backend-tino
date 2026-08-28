package com.tino.backend.device.application.port.in;

/** Safe public failure when Device context composition cannot read its source. */
public final class DeviceContextUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DeviceContextUnavailableException(Throwable cause) {
        super("device context is unavailable", cause);
    }
}
