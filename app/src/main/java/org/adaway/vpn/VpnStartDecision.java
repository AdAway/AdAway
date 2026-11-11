package org.adaway.vpn;

/**
 * Pure decision logic for whether the VPN service may be (re)started by a non-user-initiated
 * caller (background worker, host update, connection monitor, sticky resurrection, …).
 * <p>
 * Kept free of Android dependencies so it can be unit-tested.
 */
public final class VpnStartDecision {
    private VpnStartDecision() {
    }

    /**
     * Whether a background trigger is allowed to (re)start the VPN.
     *
     * @param userEnabled whether the user has explicitly asked for the VPN to be on.
     * @return {@code true} if the caller may start the service, {@code false} if it must
     * not — the user has explicitly turned the VPN off and any silent restart is a bug.
     */
    public static boolean mayBackgroundStart(boolean userEnabled) {
        return userEnabled;
    }
}
