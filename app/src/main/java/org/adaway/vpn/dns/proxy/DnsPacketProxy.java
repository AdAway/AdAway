package org.adaway.vpn.dns.proxy;

import static java.util.Objects.requireNonNull;

import org.adaway.vpn.dns.DnsServerMapper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;

import timber.log.Timber;

/**
 * This class is a {@link PacketProxy} that captures DNS request to fake DNS and forward them to the original ones.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class DnsPacketProxy extends AbstractDnsPacketProxy {

    public DnsPacketProxy(EventLoop eventLoop, DnsServerMapper dnsServerMapper) {
        super(eventLoop, dnsServerMapper);
    }

    @Override
    protected void handleAllowedResponse(UdpPacketData data, InetAddress dnsAddress) throws IOException {
        requireNonNull(data.dns);
        Timber.i("handleDnsRequest: DNS Name %s allowed, sending to %s.", data.dns.queryName, dnsAddress);
        DatagramPacket outPacket = new DatagramPacket(data.dns.rawData, 0, data.dns.rawData.length, dnsAddress, data.packetPort);
        this.eventLoop.forwardPacket(outPacket, responseData -> handleDnsResponse(data.ipPacket, responseData));
    }
}
