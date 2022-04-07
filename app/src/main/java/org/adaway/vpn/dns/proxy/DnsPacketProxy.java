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
package org.adaway.vpn.dns.proxy;

import static java.util.Objects.requireNonNull;

import org.adaway.vpn.dns.DnsServerMapper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;

import timber.log.Timber;

/**
 * Creates and parses packets, and sends packets to a remote socket or the device using VpnWorker.
 */
public class DnsPacketProxy extends AbstractDnsPacketProxy {

    public DnsPacketProxy(EventLoop eventLoop, DnsServerMapper dnsServerMapper) {
        super(eventLoop, dnsServerMapper);
    }

    protected void handleAllowedResponse(UdpPacketData data, InetAddress dnsAddress) throws IOException {
        requireNonNull(data.dns);
        Timber.i("handleDnsRequest: DNS Name %s allowed, sending to %s.", data.dns.queryName, dnsAddress);
        DatagramPacket outPacket = new DatagramPacket(data.dns.rawData, 0, data.dns.rawData.length, dnsAddress, data.packetPort);
        this.eventLoop.forwardPacket(outPacket, responseData -> handleDnsResponse(data.ipPacket, responseData));
    }
}
