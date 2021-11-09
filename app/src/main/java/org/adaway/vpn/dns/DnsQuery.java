package org.adaway.vpn.dns;

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
    }
}
