package org.adaway.vpn.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicReference;

public class VpnWorkerSocketTest {
    @Test
    public void failedProtectionClosesSocketBeforeSending() throws Exception {
        AtomicReference<DatagramSocket> captured = new AtomicReference<>();
        try {
            VpnWorker.openDnsSocket(packet(), socket -> {
                captured.set(socket);
                return false;
            });
            fail("Unprotected socket must not be returned.");
        } catch (IOException expected) {
            assertTrue(captured.get().isClosed());
        }
    }

    @Test
    public void protectedSocketIsConnectedToOnlyTheRequestedResolver() throws Exception {
        DatagramPacket packet = packet();
        try (DatagramSocket socket = VpnWorker.openDnsSocket(packet, ignored -> true)) {
            assertTrue(socket.isConnected());
            assertEquals(packet.getAddress(), socket.getInetAddress());
            assertEquals(packet.getPort(), socket.getPort());
        }
    }

    @Test
    public void idlePollsAreBoundedEvenWhenWatchdogIsDisabled() {
        assertEquals(1000, VpnWorker.boundedPollTimeout(-1));
        assertEquals(1000, VpnWorker.boundedPollTimeout(7000));
        assertEquals(500, VpnWorker.boundedPollTimeout(500));
    }

    private static DatagramPacket packet() throws Exception {
        return new DatagramPacket(new byte[0], 0, InetAddress.getByName("127.0.0.1"), 53);
    }
}
