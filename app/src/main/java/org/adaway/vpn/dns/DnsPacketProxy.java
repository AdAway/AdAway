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
package org.adaway.vpn.dns;

import android.content.Context;

import com.google.common.net.InetAddresses;

import org.adaway.AdAwayApplication;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.ListType;
import org.adaway.model.vpn.VpnModel;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpSelector;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.pcap4j.packet.namednumber.IpNumber;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Opcode;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.Section;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import timber.log.Timber;

/**
 * Creates and parses packets, and sends packets to a remote socket or the device using VpnWorker.
 */
public class DnsPacketProxy {
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

    public DnsPacketProxy(EventLoop eventLoop, DnsServerMapper dnsServerMapper) {
        this.eventLoop = eventLoop;
        this.dnsServerMapper = dnsServerMapper;
    }

    /**
     * Initializes the rules database and the list of upstream servers.
     *
     * @param context The context we are operating in (for the database).
     */
    public void initialize(Context context) {
        this.vpnModel = (VpnModel) ((AdAwayApplication) context.getApplicationContext()).getAdBlockModel();
    }

    /**
     * Handles a responsePayload from an upstream DNS server
     *
     * @param requestPacket   The original request packet
     * @param responsePayload The payload of the response
     */
    public void handleDnsResponse(IpPacket requestPacket, byte[] responsePayload) {
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
     * @throws IOException If some network error occurred
     */
    public void handleDnsRequest(byte[] packetData) throws IOException {
        IpPacket ipPacket;
        try {
            ipPacket = (IpPacket) IpSelector.newPacket(packetData, 0, packetData.length);
        } catch (Exception e) {
            Timber.i(e, "handleDnsRequest: Discarding invalid IP packet");
            return;
        }

        // Check UDP protocol
        if (!IpNumber.UDP.equals(ipPacket.getHeader().getProtocol())) {
            return;
        }

        UdpPacket updPacket;
        Packet udpPayload;

        try {
            updPacket = (UdpPacket) ipPacket.getPayload();
            udpPayload = updPacket.getPayload();
        } catch (Exception e) {
            Timber.i(e, "handleDnsRequest: Discarding unknown packet type %s", ipPacket.getHeader());
            return;
        }

        InetAddress packetAddress = ipPacket.getHeader().getDstAddr();
        int packetPort = updPacket.getHeader().getDstPort().valueAsInt();
        if (packetPort != 53) {
            return;
        }
        Optional<InetAddress> dnsAddressOptional = this.dnsServerMapper.getDnsServerFromFakeAddress(packetAddress);
        if (!dnsAddressOptional.isPresent()) {
            Timber.w("Cannot find mapped DNS for %s.", packetAddress.getHostAddress());
            return;
        }
        InetAddress dnsAddress = dnsAddressOptional.get();

        if (udpPayload == null) {
            Timber.i("handleDnsRequest: Sending UDP packet without payload: %s", updPacket);

            // Let's be nice to Firefox. Firefox uses an empty UDP packet to
            // the gateway to reduce the RTT. For further details, please see
            // https://bugzilla.mozilla.org/show_bug.cgi?id=888268
            DatagramPacket outPacket = new DatagramPacket(new byte[0], 0, dnsAddress, packetPort);
            eventLoop.forwardPacket(outPacket);
            return;
        }

        byte[] dnsRawData = udpPayload.getRawData();
        Message dnsMsg;
        try {
            dnsMsg = new Message(dnsRawData);
        } catch (IOException e) {
            Timber.d(e, "Discarding non-DNS or invalid packet.");
            return;
        }
        if (!isStandardQuery(dnsMsg)) {
            Timber.d("Discarding unsupported DNS query.");
            return;
        }
        Name name = dnsMsg.getQuestion().getName();
        String dnsQueryName = name.toString(true);
        HostEntry entry = getHostEntry(dnsQueryName);
        switch (entry.getType()) {
            case BLOCKED:
                Timber.d("DNS query blocked.");
                handleDnsResponse(ipPacket, createBlockedResponse(dnsMsg).toWire());
                break;
            case ALLOWED:
                Timber.d("DNS query allowed.");
                DatagramPacket outPacket = new DatagramPacket(dnsRawData, 0, dnsRawData.length, dnsAddress, packetPort);
                this.eventLoop.forwardPacket(outPacket, data -> {
                    if (isMatchingResponse(dnsMsg, data)) {
                        handleDnsResponse(ipPacket, data);
                    }
                });
                break;
            case REDIRECTED:
                Timber.d("DNS query redirected.");
                handleDnsResponse(ipPacket,
                        createRedirectResponse(dnsMsg, entry.getRedirection()).toWire());
                break;
        }
    }

    static boolean isStandardQuery(Message query) {
        return !query.getHeader().getFlag(Flags.QR)
                && query.getHeader().getOpcode() == Opcode.QUERY
                && query.getHeader().getCount(Section.QUESTION) == 1;
    }

    static boolean isMatchingResponse(Message query, byte[] data) {
        try {
            Message response = new Message(data);
            return response.getHeader().getFlag(Flags.QR)
                    && response.getHeader().getID() == query.getHeader().getID()
                    && response.getHeader().getOpcode() == query.getHeader().getOpcode()
                    && response.getHeader().getCount(Section.QUESTION) == 1
                    && query.getQuestion().equals(response.getQuestion());
        } catch (IOException e) {
            return false;
        }
    }

    private static Message createResponse(Message query) {
        // Do not reflect client-supplied answers or DNSSEC authentication flags.
        Message response = new Message(query.getHeader().getID());
        response.getHeader().setFlag(Flags.QR);
        response.getHeader().setFlag(Flags.RA);
        if (query.getHeader().getFlag(Flags.RD)) {
            response.getHeader().setFlag(Flags.RD);
        }
        if (query.getHeader().getFlag(Flags.CD)) {
            response.getHeader().setFlag(Flags.CD);
        }
        response.addRecord(query.getQuestion(), Section.QUESTION);
        return response;
    }

    static Message createBlockedResponse(Message query) {
        Message response = createResponse(query);
        response.addRecord(NEGATIVE_CACHE_SOA_RECORD, Section.AUTHORITY);
        return response;
    }

    static Message createRedirectResponse(Message query, String redirection) {
        Message response = createResponse(query);
        InetAddress address;
        try {
            if (redirection == null) {
                throw new IllegalArgumentException("Missing redirect address.");
            }
            address = InetAddresses.forString(redirection);
        } catch (IllegalArgumentException e) {
            response.getHeader().setRcode(Rcode.SERVFAIL);
            return response;
        }
        Record question = query.getQuestion();
        int addressType = address instanceof Inet6Address ? Type.AAAA : Type.A;
        if (question.getDClass() == DClass.IN
                && (question.getType() == addressType || question.getType() == Type.ANY)) {
            Record answer = addressType == Type.AAAA
                    ? new AAAARecord(question.getName(), DClass.IN, NEGATIVE_CACHE_TTL_SECONDS, address)
                    : new ARecord(question.getName(), DClass.IN, NEGATIVE_CACHE_TTL_SECONDS, address);
            response.addRecord(answer, Section.ANSWER);
        } else {
            response.addRecord(NEGATIVE_CACHE_SOA_RECORD, Section.AUTHORITY);
        }
        return response;
    }

    private HostEntry getHostEntry(String dnsQueryName) {
        String hostname = dnsQueryName.toLowerCase(Locale.ENGLISH);
        HostEntry entry = null;
        if (this.vpnModel != null) {
            entry = this.vpnModel.getEntry(hostname);
        }
        if (entry == null) {
            entry = new HostEntry();
            entry.setHost(hostname);
            entry.setType(ListType.ALLOWED);
        }
        return entry;
    }

    /**
     * Interface abstracting away VpnWorker.
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
}
