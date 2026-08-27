package com.tino.backend.device.application.exception;

/** Safe denial for missing membership, disabled state, or cross-business access. */
public final class DeviceInstallationAccessDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DeviceInstallationAccessDeniedException() {
        super("device installation access denied");
    }
}
