package org.adaway.vpn.dns.proxy;

import org.pcap4j.packet.IpPacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.function.Consumer;

/**
 * This interface abstracts the {@link org.adaway.vpn.worker.VpnWorker}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public interface EventLoop {
    /**
     * Forward a packet to the VPN underlying network.
     *
     * @param packet The packet to forward.
     * @throws IOException If the packet could not be forwarded.
     */
    void forwardPacket(DatagramPacket packet) throws IOException;

    /**
     * Forward a packet to the VPN underlying network.
     *
     * @param packet   The packet to forward.
     * @param callback The callback to call with the packet response data.
     * @throws IOException If the packet could not be forwarded.
     */
    void forwardPacket(DatagramPacket packet, Consumer<byte[]> callback) throws IOException;

    /**
     * Write an IP packet to the local TUN device
     *
     * @param packet The packet to write (a response to a DNS request)
     */
    void queueDeviceWrite(IpPacket packet);
}
