/*
 * Derived from dns66:
 * Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * Parsing code derived from AdBuster:
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

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * Ensures that the connection is alive and sets various timeouts and delays in response.
 * <p>
 * The implementation is a bit weird: Success and Failure cases are both handled in the timeout
 * case. When a packet is received, we simply store the time.
 * <p>
 * If poll() times out and we have not seen a packet after we last sent a ping, then we force
 * a reconnect and increase the reconnect delay.
 * <p>
 * If poll() times out and we have seen a packet after we last sent a ping, we increase the
 * poll() time out, causing the next check to run later, and send a ping packet.
 */

class VpnWatchdog {
    private static final String TAG = "VpnWatchDog";

    // Polling is quadrupled on every success, and values range from 4s to 1h8m.
    private static final int POLL_TIMEOUT_START = 1000;
    private static final int POLL_TIMEOUT_END = 4096000;
    private static final int POLL_TIMEOUT_WAITING = 7000;
    private static final int POLL_TIMEOUT_GROW = 4;

    // Reconnect penalty ranges from 0s to 5s, in increments of 200 ms.
    private static final int INIT_PENALTY_START = 0;
    private static final int INIT_PENALTY_END = 5000;
    private static final int INIT_PENALTY_INC = 200;

    private int initPenalty = INIT_PENALTY_START;
    private int pollTimeout = POLL_TIMEOUT_START;

    // Information about when packets where received.
    private long lastPacketSent;
    private long lastPacketReceived;

    private boolean enabled;
    private DatagramPacket checkAlivePacket;

    VpnWatchdog() {
        // Set default timestamps
        this.lastPacketSent = 0;
        this.lastPacketReceived = 0;
        // Set disable by default
        this.enabled = false;
    }


    /**
     * Returns the current poll time out.
     */
    int getPollTimeout() {
        if (!this.enabled) {
            return -1;
        }
        if (this.lastPacketReceived < this.lastPacketSent) {
            return POLL_TIMEOUT_WAITING;
        }
        return this.pollTimeout;
    }

    /**
     * Sets the target address ping packets should be sent to.
     */
    void setTarget(InetAddress target) {
        this.checkAlivePacket = new DatagramPacket(new byte[0], 0, 0 /* length */, target, 53);
    }

    /**
     * An initialization method. Sleeps the penalty and sends initial packet.
     *
     * @param enabled If the watchdog should be enabled.
     */
    void initialize(boolean enabled) {
        Log.d(TAG, "initialize: Initializing watchdog");

        this.pollTimeout = POLL_TIMEOUT_START;
        this.lastPacketSent = 0;
        this.enabled = enabled;

        if (!this.enabled) {
            Log.d(TAG, "initialize: Disabled.");
            return;
        }

        if (this.initPenalty > 0) {
            Log.d(TAG, "init penalty: Sleeping for " + this.initPenalty + "ms");
            try {
                Thread.sleep(this.initPenalty);
            } catch (InterruptedException exception) {
                Log.d(TAG, "Failed to wait the initial penalty");
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Handles a timeout of poll()
     *
     * @throws VpnWorker.VpnNetworkException When the watchdog timed out
     */
    void handleTimeout() throws VpnWorker.VpnNetworkException {
        if (!this.enabled) {
            return;
        }
        Log.d(TAG, "handleTimeout: Milliseconds elapsed between last receive and sent: "
                + (this.lastPacketReceived - this.lastPacketSent));
        // Receive really timed out
        if (this.lastPacketReceived < this.lastPacketSent && this.lastPacketSent != 0) {
            this.initPenalty += INIT_PENALTY_INC;
            if (this.initPenalty > INIT_PENALTY_END) {
                this.initPenalty = INIT_PENALTY_END;
            }
            throw new VpnWorker.VpnNetworkException("Watchdog timed out");
        }
        // We received a packet after sending it, so we can be more confident and grow our wait time
        this.pollTimeout *= POLL_TIMEOUT_GROW;
        if (this.pollTimeout > POLL_TIMEOUT_END) {
            this.pollTimeout = POLL_TIMEOUT_END;
        }

        sendPacket();
    }

    /**
     * Handles an incoming packet on a device.
     *
     * @param packetData The data of the packet
     */
    void handlePacket(byte[] packetData) {
        if (!this.enabled) {
            return;
        }
        Log.d(TAG, "handlePacket: Received packet of length " + packetData.length);
        this.lastPacketReceived = System.currentTimeMillis();
    }

    /**
     * Sends an empty check-alive packet to the configured target address.
     *
     * @throws VpnWorker.VpnNetworkException If sending failed and we should restart
     */
    void sendPacket() throws VpnWorker.VpnNetworkException {
        if (!this.enabled || this.checkAlivePacket == null) {
            return;
        }
        Log.d(TAG, "sendPacket: Sending packet, poll timeout is " + this.pollTimeout + ".");

        try (DatagramSocket socket = newDatagramSocket()) {
            socket.send(this.checkAlivePacket);
            this.lastPacketSent = System.currentTimeMillis();
        } catch (IOException e) {
            throw new VpnWorker.VpnNetworkException("Failed to send check-alive packet.", e);
        }
    }

    @NonNull
    DatagramSocket newDatagramSocket() throws SocketException {
        return new DatagramSocket();
    }
}
