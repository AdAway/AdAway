package org.adaway.vpn.dns;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

public class DnsQueryResponseTest {
    @Test
    public void shortResponseHasNoTrailingPadding() throws Exception {
        assertRoundTrip(new byte[]{1, 2, 3, 4});
    }

    @Test
    public void largeResponseIsNotTruncatedAt1024Bytes() throws Exception {
        byte[] response = new byte[4096];
        Arrays.fill(response, (byte) 0x5a);
        assertRoundTrip(response);
    }

    private static void assertRoundTrip(byte[] payload) throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (DatagramSocket receiver = new DatagramSocket(0, loopback);
             DatagramSocket sender = new DatagramSocket(0, loopback)) {
            receiver.setSoTimeout(2000);
            sender.send(new DatagramPacket(payload, payload.length,
                    loopback, receiver.getLocalPort()));
            assertArrayEquals(payload, DnsQuery.receiveResponse(receiver));
        }
    }
}
