/*
 * Derived from dns66:
 * Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * Derived from AdBuster:
 * Copyright (C) 2016 Daniel Brodie <dbrodie@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.vpn;

import android.content.Context;
import android.util.Log;

import org.adaway.AdAwayApplication;
import org.adaway.model.vpn.VpnModel;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpSelector;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.pcap4j.packet.namednumber.IpNumber;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.Section;
import org.xbill.DNS.TextParseException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Locale;

/**
 * Creates and parses packets, and sends packets to a remote socket or the device using
 * {@link VpnWorker}.
 */
public class DnsPacketProxy {

    private static final String TAG = "DnsPacketProxy";
    // Choose a value that is smaller than the time needed to unblock a host.
    private static final int NEGATIVE_CACHE_TTL_SECONDS = 5;
    private static final SOARecord NEGATIVE_CACHE_SOA_RECORD;

    static {
        try {
            // Let's use a guaranteed invalid hostname here, clients are not supposed to use
            // our fake values, the whole thing just exists for negative caching.
            Name name = new Name("adaway.vpn.invalid.");
            NEGATIVE_CACHE_SOA_RECORD = new SOARecord(name, DClass.IN, NEGATIVE_CACHE_TTL_SECONDS,
                    name, name, 0, 0, 0, 0, NEGATIVE_CACHE_TTL_SECONDS);
        } catch (TextParseException e) {
            throw new RuntimeException(e);
        }
    }

    private final EventLoop eventLoop;
    private final DnsServerMapper dnsServerMapper;
    private VpnModel vpnModel;

    DnsPacketProxy(EventLoop eventLoop, DnsServerMapper dnsServerMapper) {
        this.eventLoop = eventLoop;
        this.dnsServerMapper = dnsServerMapper;
    }

    /**
     * Initializes the rules database and the list of upstream servers.
     *
     * @param context The context we are operating in (for the database).
     */
    void initialize(Context context) {
        this.vpnModel = (VpnModel) ((AdAwayApplication) context.getApplicationContext()).getAdBlockModel();
    }

    /**
     * Handles a responsePayload from an upstream DNS server
     *
     * @param requestPacket   The original request packet
     * @param responsePayload The payload of the response
     */
    void handleDnsResponse(IpPacket requestPacket, byte[] responsePayload) {
        UdpPacket udpOutPacket = (UdpPacket) requestPacket.getPayload();
        UdpPacket.Builder payLoadBuilder = new UdpPacket.Builder(udpOutPacket)
                .srcPort(udpOutPacket.getHeader().getDstPort())
                .dstPort(udpOutPacket.getHeader().getSrcPort())
                .srcAddr(requestPacket.getHeader().getDstAddr())
                .dstAddr(requestPacket.getHeader().getSrcAddr())
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(
                        new UnknownPacket.Builder().rawData(responsePayload)
                );

        IpPacket ipOutPacket;
        if (requestPacket instanceof IpV4Packet) {
            ipOutPacket = new IpV4Packet.Builder((IpV4Packet) requestPacket)
                    .srcAddr((Inet4Address) requestPacket.getHeader().getDstAddr())
                    .dstAddr((Inet4Address) requestPacket.getHeader().getSrcAddr())
                    .correctChecksumAtBuild(true)
                    .correctLengthAtBuild(true)
                    .payloadBuilder(payLoadBuilder)
                    .build();

        } else {
            ipOutPacket = new IpV6Packet.Builder((IpV6Packet) requestPacket)
                    .srcAddr((Inet6Address) requestPacket.getHeader().getDstAddr())
                    .dstAddr((Inet6Address) requestPacket.getHeader().getSrcAddr())
                    .correctLengthAtBuild(true)
                    .payloadBuilder(payLoadBuilder)
                    .build();
        }

        this.eventLoop.queueDeviceWrite(ipOutPacket);
    }

    /**
     * Handles a DNS request, by either blocking it or forwarding it to the remote location.
     *
     * @param packetData The packet data to read
     * @throws VpnWorker.VpnNetworkException If some network error occurred
     */
    void handleDnsRequest(byte[] packetData) throws VpnWorker.VpnNetworkException {
        IpPacket ipPacket;
        try {
            ipPacket = (IpPacket) IpSelector.newPacket(packetData, 0, packetData.length);
        } catch (Exception e) {
            Log.i(TAG, "handleDnsRequest: Discarding invalid IP packet", e);
            return;
        }

        // Check UDP protocol
        if (ipPacket.getHeader().getProtocol() != IpNumber.UDP) {
            return;
        }

        UdpPacket updPacket;
        Packet udpPayload;

        try {
            updPacket = (UdpPacket) ipPacket.getPayload();
            udpPayload = updPacket.getPayload();
        } catch (Exception e) {
            Log.i(TAG, "handleDnsRequest: Discarding unknown packet type " + ipPacket.getHeader(), e);
            return;
        }

        InetAddress packetAddress = ipPacket.getHeader().getDstAddr();
        int packetPort = updPacket.getHeader().getDstPort().valueAsInt();
        InetAddress dnsAddress = this.dnsServerMapper.translate(packetAddress);
        if (dnsAddress == null) {
            return;
        }

        if (udpPayload == null) {
            Log.i(TAG, "handleDnsRequest: Sending UDP packet without payload: " + updPacket);

            // Let's be nice to Firefox. Firefox uses an empty UDP packet to
            // the gateway to reduce the RTT. For further details, please see
            // https://bugzilla.mozilla.org/show_bug.cgi?id=888268
            DatagramPacket outPacket = new DatagramPacket(new byte[0], 0, 0 /* length */, dnsAddress, packetPort);
            eventLoop.forwardPacket(outPacket, null);
            return;
        }

        byte[] dnsRawData = udpPayload.getRawData();
        Message dnsMsg;
        try {
            dnsMsg = new Message(dnsRawData);
        } catch (IOException e) {
            Log.i(TAG, "handleDnsRequest: Discarding non-DNS or invalid packet", e);
            return;
        }
        if (dnsMsg.getQuestion() == null) {
            Log.i(TAG, "handleDnsRequest: Discarding DNS packet with no query " + dnsMsg);
            return;
        }
        String dnsQueryName = dnsMsg.getQuestion().getName().toString(true);
        String hostname = dnsQueryName.toLowerCase(Locale.ENGLISH);
        boolean isBlocked = this.vpnModel != null && this.vpnModel.isBlocked(hostname);
        if (!isBlocked) {
            Log.i(TAG, "handleDnsRequest: DNS Name " + dnsQueryName + " Allowed, sending to " + dnsAddress);
            DatagramPacket outPacket = new DatagramPacket(dnsRawData, 0, dnsRawData.length, dnsAddress, packetPort);
            eventLoop.forwardPacket(outPacket, ipPacket);
        } else {
            Log.i(TAG, "handleDnsRequest: DNS Name " + dnsQueryName + " blocked!");
            dnsMsg.getHeader().setFlag(Flags.QR);
            dnsMsg.getHeader().setRcode(Rcode.NOERROR);
            dnsMsg.addRecord(NEGATIVE_CACHE_SOA_RECORD, Section.AUTHORITY);
            handleDnsResponse(ipPacket, dnsMsg.toWire());
        }
    }

    /**
     * Interface abstracting away {@link VpnWorker}.
     */
    interface EventLoop {
        /**
         * Called to send a packet to a remote location
         *
         * @param packet        The packet to send
         * @param requestPacket If specified, the event loop must wait for a response, and then
         *                      call {@link #handleDnsResponse(IpPacket, byte[])} for the data
         *                      of the response, with this packet as the first argument.
         */
        void forwardPacket(DatagramPacket packet, IpPacket requestPacket) throws VpnWorker.VpnNetworkException;

        /**
         * Write an IP packet to the local TUN device
         *
         * @param packet The packet to write (a response to a DNS request)
         */
        void queueDeviceWrite(IpPacket packet);
    }
}
