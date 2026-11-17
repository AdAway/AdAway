package org.adaway.vpn.dns;

import org.junit.Test;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.OPTRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.Type;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class DnsQueryTest {
    @Test
    public void shortResponseHasNoBufferPadding() throws Exception {
        assertDatagramPreserved(47);
    }

    @Test
    public void ednsResponseLargerThan1024BytesIsNotTruncated() throws Exception {
        Name name = Name.fromString("large.example.");
        Message response = Message.newQuery(Record.newRecord(name, Type.TXT, DClass.IN));
        response.getHeader().setFlag(Flags.QR);
        response.addRecord(new TXTRecord(name, DClass.IN, 60,
                Collections.nCopies(16, "x".repeat(240))), Section.ANSWER);
        response.addRecord(new OPTRecord(4096, 0, 0, 0), Section.ADDITIONAL);
        byte[] payload = response.toWire();
        assertTrue(payload.length > 1024);
        assertTrue(payload.length <= 4096);
        assertDatagramPreserved(payload);
    }

    @Test
    public void largeUdpResponseIsNotTruncated() throws Exception {
        assertDatagramPreserved(32768);
    }

    private void assertDatagramPreserved(int size) throws Exception {
        byte[] response = new byte[size];
        for (int i = 0; i < response.length; i++) {
            response[i] = (byte) (i * 31 + 7);
        }
        assertDatagramPreserved(response);
    }

    private void assertDatagramPreserved(byte[] response) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (DatagramSocket receiver = new DatagramSocket(0, loopback);
             DatagramSocket sender = new DatagramSocket(0, loopback)) {
            receiver.setSoTimeout(2000);
            sender.send(new DatagramPacket(response, response.length, loopback,
                    receiver.getLocalPort()));
            assertArrayEquals(response, DnsQuery.receiveResponse(receiver));
        }
    }
}
