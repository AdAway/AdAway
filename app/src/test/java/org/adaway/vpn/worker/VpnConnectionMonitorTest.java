package org.adaway.vpn.worker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VpnConnectionMonitorTest {
    @Test
    public void stoppedMonitorDoesNotRequestRecoveryBeforeExecutorInterrupt() {
        AtomicInteger requests = new AtomicInteger();
        VpnConnectionMonitor monitor = new VpnConnectionMonitor(requests::incrementAndGet);
        monitor.stop();
        monitor.requestReconnect();
        assertEquals(0, requests.get());
    }

    @Test
    public void connectionLossRequestsRecoveryOnlyOnce() {
        AtomicInteger requests = new AtomicInteger();
        VpnConnectionMonitor monitor = new VpnConnectionMonitor(requests::incrementAndGet);
        monitor.requestReconnect();
        monitor.requestReconnect();
        assertEquals(1, requests.get());
    }

    @Test
    public void resetRearmsMonitorAfterRecoveryStop() throws Exception {
        VpnConnectionMonitor monitor = new VpnConnectionMonitor(null);

        monitor.stop();
        assertFalse(isRunning(monitor));

        monitor.reset();

        assertTrue("Recovered VPN restarts must submit an active monitor loop.",
                isRunning(monitor));
    }

    private static boolean isRunning(VpnConnectionMonitor monitor) throws Exception {
        Field field = VpnConnectionMonitor.class.getDeclaredField("running");
        field.setAccessible(true);
        return ((AtomicBoolean) field.get(monitor)).get();
    }
}
