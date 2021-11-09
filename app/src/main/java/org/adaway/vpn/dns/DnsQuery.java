package org.adaway.vpn.dns;

import static android.system.OsConstants.POLLIN;

import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.system.StructPollfd;

import org.pcap4j.packet.IpPacket;

import java.net.DatagramSocket;

/**
 * This class represents a DNS query.
 *
 * @author Bruce BUJON
 */
public class DnsQuery {
    /**
     * The socket used to query DNS server.
     */
    public final DatagramSocket socket;
    /**
     * The DNS query packet.
     */
    public final IpPacket packet;
    /**
     * The pollfd related to the query to poll the OS with.
     */
    public final StructPollfd pollfd;
    /**
     * The query creation time, UNIX timestamp in seconds).
     */
    final long time;

    /**
     * Constructor.
     *
     * @param socket The socket used to query DNS server.
     * @param packet The DNS query packet.
     */
    public DnsQuery(DatagramSocket socket, IpPacket packet) {
        this.socket = socket;
        this.packet = packet;
        this.time = System.currentTimeMillis() / 1000;
        this.pollfd = new StructPollfd();
        this.pollfd.fd = ParcelFileDescriptor.fromDatagramSocket(this.socket).getFileDescriptor();
        this.pollfd.events = (short) OsConstants.POLLIN;
    }

    /**
     * Check whether the query is answered, meaning its socket has received data to read.
     *
     * @return {@code true} if there is data to read from {@link #socket}, {@code false} otherwise.
     */
    public boolean isAnswered() {
        return (this.pollfd.revents & POLLIN) != 0;
    }
}
