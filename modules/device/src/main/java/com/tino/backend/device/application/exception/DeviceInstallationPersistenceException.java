package com.tino.backend.device.application.exception;

/** Persistence failure kept behind the Device application boundary. */
public final class DeviceInstallationPersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DeviceInstallationPersistenceException(Throwable cause) {
        super("device installation persistence failed", cause);
    }
}
