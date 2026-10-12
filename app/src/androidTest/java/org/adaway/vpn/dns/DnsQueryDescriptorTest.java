package org.adaway.vpn.dns;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import android.os.ParcelFileDescriptor;
import android.system.Os;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

@RunWith(AndroidJUnit4.class)
public class DnsQueryDescriptorTest {
    @Test
    public void pollingDescriptorSurvivesSocketCloseOnEverySupportedApi() throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            DnsQuery query = new DnsQuery(socket, ignored -> { }, 0L);
            try {
                socket.close();
                // fstat verifies a live kernel fd, not just an unchanged Java fd integer.
                Os.fstat(query.getPollfd().fd);
                assertTrue(query.getPollfd().fd.valid());
            } finally {
                query.close();
            }
            assertFalse(query.getPollfd().fd.valid());
        }
    }

    @Test
    public void closingPollingDescriptorDoesNotCloseLiveSocketOnEverySupportedApi()
            throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (DatagramSocket receiver = new DatagramSocket(0, loopback);
             DatagramSocket sender = new DatagramSocket(0, loopback)) {
            receiver.setSoTimeout(2000);
            DnsQuery query = new DnsQuery(receiver, ignored -> { }, 0L);
            try {
                Field field = DnsQuery.class.getDeclaredField("socketDescriptor");
                field.setAccessible(true);
                ((ParcelFileDescriptor) field.get(query)).close();
                sender.send(new DatagramPacket(new byte[]{42}, 1,
                        loopback, receiver.getLocalPort()));
                DatagramPacket response = new DatagramPacket(new byte[1], 1);
                receiver.receive(response);
                assertEquals(42, response.getData()[0]);
            } finally {
                query.close();
            }
        }
    }

    @Test
    public void pollErrorClosesQueryWithoutBlockingForAReply() throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            DnsQuery query = new DnsQuery(socket, ignored -> {
                throw new AssertionError("Failed query must not deliver a response.");
            }, 0L);
            query.getPollfd().revents = (short) android.system.OsConstants.POLLERR;
            assertTrue(query.isAnswered());
            query.handleResponse();
            assertTrue(socket.isClosed());
            assertFalse(query.getPollfd().fd.valid());
        }
    }

    @Test
    public void closingQueryClosesSocketAndPollingDescriptor() throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            DnsQuery query = new DnsQuery(socket, ignored -> { }, 0L);
            FileDescriptor descriptor = query.getPollfd().fd;
            try {
                assertTrue(descriptor.valid());
            } finally {
                query.close();
            }
            assertTrue(socket.isClosed());
            assertFalse(descriptor.valid());
        }
    }
}
