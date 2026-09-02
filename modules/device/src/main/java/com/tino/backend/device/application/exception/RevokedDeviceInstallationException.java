package com.tino.backend.device.application.exception;

/** A revoked installation is historical and cannot be implicitly reactivated. */
public final class RevokedDeviceInstallationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RevokedDeviceInstallationException() {
        super("device installation is revoked");
    }
}
