package org.adaway.vpn.dns.proxy;

import android.content.Context;

import java.io.IOException;

/**
 * This interface defines the packet proxy that will handle the capture packets from the VPN interface.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public interface PacketProxy {
    /**
     * Initialize the packet proxy with the current application context.
     *
     * @param context The application context.
     */
    void initialize(Context context);

    /**
     * Handles a DNS request, by either blocking it or forwarding it to the remote location.
     *
     * @param packetData The packet data to read
     * @throws IOException If some network error occurred
     */
    void handleDnsRequest(byte[] packetData) throws IOException;
}
