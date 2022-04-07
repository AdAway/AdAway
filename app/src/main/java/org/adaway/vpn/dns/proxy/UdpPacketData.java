package org.adaway.vpn.dns.proxy;

import androidx.annotation.Nullable;

import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpPacket.IpHeader;
import org.pcap4j.packet.IpSelector;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.namednumber.IpNumber;

import java.net.InetAddress;

import timber.log.Timber;

/**
 * This class is a data store for the parsed UDP packet data.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class UdpPacketData {
    /**
     * The parent IP packet.
     */
    final IpPacket ipPacket;
    /**
     * The IP packet inet address.
     */
    final InetAddress packetAddress;
    /**
     * The UPD packet destination port.
     */
    final int packetPort;
    /**
     * The DNS packet data, <code>null</code> if not valid.
     */
    @Nullable
    final DnsPacketData dns;

    private UdpPacketData(
            IpPacket ipPacket,
            InetAddress packetAddress,
            int packetPort,
            @Nullable DnsPacketData dns
    ) {
        this.ipPacket = ipPacket;
        this.packetAddress = packetAddress;
        this.packetPort = packetPort;
        this.dns = dns;
    }

    /**
     * Thy to parse an UDP packet data.
     *
     * @param data The packet data.
     * @return The parsed UDP packet data, <code>null</code> if data could not be parsed.
     */
    @Nullable // Do not use exception to save performance as it is expected to occasionally fail
    static UdpPacketData tryToParse(byte[] data) {
        try {
            IpPacket ipPacket = (IpPacket) IpSelector.newPacket(data, 0, data.length);
            IpHeader header = ipPacket.getHeader();
            InetAddress packetAddress = header.getDstAddr();
            if (header.getProtocol() != IpNumber.UDP) {
                Timber.i("Not an UDP packet.");
                return null;
            }
            UdpPacket updPacket = (UdpPacket) ipPacket.getPayload();
            int packetPort = updPacket.getHeader().getDstPort().valueAsInt();
            Packet udpPayload = updPacket.getPayload();
            return new UdpPacketData(
                    ipPacket,
                    packetAddress,
                    packetPort,
                    udpPayload == null ? null : DnsPacketData.tryToParse(udpPayload.getRawData())
            );
        } catch (Exception e) {
            Timber.i(e, "Failed to parse UDP packet.");
            return null;
        }
    }
}
