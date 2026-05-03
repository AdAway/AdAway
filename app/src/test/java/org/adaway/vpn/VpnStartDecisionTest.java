package org.adaway.vpn;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for the VPN start decision logic.
 * <p>
 * Pinning down the contract the rest of the VPN code paths rely on: if the user has
 * explicitly turned the VPN off, no background path (host-update worker, connection
 * monitor, sticky resurrection, …) may start it. Only an explicit user action (tile,
 * notification "Resume", home toggle, "Enable at startup") changes that.
 */
public class VpnStartDecisionTest {

    @Test
    public void backgroundStart_blockedWhenUserDisabled() {
        assertFalse("user-disabled VPN must not be auto-started",
                VpnStartDecision.mayBackgroundStart(false));
    }

    @Test
    public void backgroundStart_allowedWhenUserEnabled() {
        assertTrue("user-enabled VPN may be (re)started by background",
                VpnStartDecision.mayBackgroundStart(true));
    }
}
