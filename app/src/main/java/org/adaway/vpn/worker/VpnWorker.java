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
package org.adaway.vpn.worker;

import static android.system.OsConstants.POLLIN;
import static android.system.OsConstants.POLLOUT;
import static org.adaway.vpn.VpnStatus.RECONNECTING_NETWORK_ERROR;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STARTING;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.adaway.vpn.VpnStatus.STOPPING;
import static org.adaway.vpn.worker.VpnBuilder.establish;

import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import androidx.annotation.Nullable;

import org.adaway.helper.PreferenceHelper;
import org.adaway.vpn.VpnService;
import org.adaway.vpn.dns.DnsPacketProxy;
import org.adaway.vpn.dns.DnsQuery;
import org.adaway.vpn.dns.DnsQueryQueue;
import org.adaway.vpn.dns.DnsServerMapper;
import org.pcap4j.packet.IpPacket;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

// TODO Write document
// TODO It is thread safe
// TODO Rework status notification
// TODO Improve exception handling in work()
public class VpnWorker implements DnsPacketProxy.EventLoop {
    private static final String TAG = "VpnWorker";
    /**
     * Maximum packet size is constrained by the MTU, which is given as a signed short.
     */
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;

    /**
     * The VPN service, also used as {@link android.content.Context}.
     */
    private final VpnService vpnService;
    /**
     * The queue of packets to send to the device.
     */
    private final Queue<byte[]> deviceWrites;
    /**
     * The queue of DNS queries.
     */
    private final DnsQueryQueue dnsQueryQueue;
    // The mapping between fake and real dns addresses
    private final DnsServerMapper dnsServerMapper;
    // The object where we actually handle packets.
    private final DnsPacketProxy dnsPacketProxy;

    // TODO Comment
    private final VpnConnectionThrottler connectionThrottler;
    private final VpnConnectionMonitor connectionMonitor;

    // Watch dog that checks our connection is alive.
    private final VpnWatchdog vpnWatchDog;

    /**
     * The VPN worker executor (<code>null</code> if not started).
     */
    private final AtomicReference<ExecutorService> executor;
    /**
     * The VPN network interface, (<code>null</code> if not established).
     */
    private final AtomicReference<ParcelFileDescriptor> vpnNetworkInterface;

    /**
     * Constructor.
     *
     * @param vpnService The VPN service, also used as {@link android.content.Context}.
     */
    public VpnWorker(VpnService vpnService) {
        this.vpnService = vpnService;
        this.deviceWrites = new LinkedList<>();
        this.dnsQueryQueue = new DnsQueryQueue();
        this.dnsServerMapper = new DnsServerMapper();
        this.dnsPacketProxy = new DnsPacketProxy(this, this.dnsServerMapper);
        this.connectionThrottler = new VpnConnectionThrottler();
        this.connectionMonitor = new VpnConnectionMonitor(this.vpnService);
        this.vpnWatchDog = new VpnWatchdog();
        this.executor = new AtomicReference<>(null);
        this.vpnNetworkInterface = new AtomicReference<>(null);
    }

    /**
     * Start the VPN worker.
     * Kill the current worker and restart it if already running.
     */
    public void start() {
        Log.d(TAG, "Starting VPN thread…");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(this::work);
        executor.submit(this.connectionMonitor::monitor);
        setExecutor(executor);
        Log.i(TAG, "VPN thread started.");
    }

    /**
     * Stop the VPN worker.
     */
    public void stop() {
        Log.d(TAG, "Stopping VPN thread.");
        this.connectionMonitor.reset();
        forceCloseTunnel();
        setExecutor(null);
        Log.i(TAG, "VPN thread stopped.");
    }

    /**
     * Keep track of the worker executor.<br>
     * Shut the previous one down in exists.
     *
     * @param executor The new worker executor, <code>null</code> if no executor any more.
     */
    private void setExecutor(ExecutorService executor) {
        ExecutorService oldExecutor = this.executor.getAndSet(executor);
        if (oldExecutor != null) {
            Log.d(TAG, "Shutting down VPN executor…");
            oldExecutor.shutdownNow();
            Log.d(TAG, "VPN executor shutdown.");
        }
    }

    /**
     * Force close the tunnel connection.
     */
    private void forceCloseTunnel() {
        ParcelFileDescriptor networkInterface = this.vpnNetworkInterface.get();
        if (networkInterface != null) {
            try {
                networkInterface.close();
            } catch (IOException e) {
                Log.w("Failed to close VPN network interface.", e);
            }
        }
    }

    private void work() {
        Log.d(TAG, "Starting work…");
        // Initialize context
        this.dnsPacketProxy.initialize(this.vpnService);
        // Initialize the watchdog
        this.vpnWatchDog.initialize(PreferenceHelper.getVpnWatchdogEnabled(this.vpnService));
        // Try connecting the vpn continuously
        while (true) {
            try {
                this.connectionThrottler.throttle();
                this.vpnService.notifyVpnStatus(STARTING);
                runVpn();
                Log.i(TAG, "Told to stop");
                this.vpnService.notifyVpnStatus(STOPPING);
                break;
            } catch (InterruptedException e) {
                Log.d(TAG, "Failed to wait for connexion throttling.", e);
                Thread.currentThread().interrupt();
                break;
            } catch (VpnNetworkException | IOException e) {
                Log.w(TAG, "Network exception in vpn thread, reconnecting", e);
                // If an exception was thrown, show to the user and try again
                this.vpnService.notifyVpnStatus(RECONNECTING_NETWORK_ERROR);
            }
        }
        this.vpnService.notifyVpnStatus(STOPPED);
        Log.d(TAG, "Exiting work.");
    }

    private void runVpn() throws IOException, VpnNetworkException {
        // Allocate the buffer for a single packet.
        byte[] packet = new byte[MAX_PACKET_SIZE];

        // Authenticate and configure the virtual network interface.
        try (ParcelFileDescriptor pfd = establish(this.vpnService, this.dnsServerMapper);
             // Read and write views of the tunnel device
             FileInputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
             FileOutputStream outputStream = new FileOutputStream(pfd.getFileDescriptor())) {
            // Store reference to network interface to close it externally on demand
            this.vpnNetworkInterface.set(pfd);
            // Initialize connection monitor
            this.connectionMonitor.initialize();

            // Update address to ping with default DNS server
            this.vpnWatchDog.setTarget(this.dnsServerMapper.getDefaultDnsServerAddress());

            // Now we are connected. Set the flag and show the message.
            this.vpnService.notifyVpnStatus(RUNNING);

            // We keep forwarding packets till something goes wrong.
            while (true) {
                doOne(inputStream, outputStream, packet);
            }
        }
    }

    private void doOne(FileInputStream inputStream, FileOutputStream fileOutputStream, byte[] packet)
            throws IOException, VpnNetworkException {
        // Create poll FD on tunnel
        StructPollfd deviceFd = new StructPollfd();
        deviceFd.fd = inputStream.getFD();
        deviceFd.events = (short) POLLIN;
        if (!this.deviceWrites.isEmpty()) {
            deviceFd.events |= (short) POLLOUT;
        }
        // Create poll FD on each DNS query socket
        StructPollfd[] polls = new StructPollfd[1 + this.dnsQueryQueue.size()];
        polls[0] = deviceFd;
        {
            int i = 1;
            for (DnsQuery query : this.dnsQueryQueue) {
                polls[i] = query.pollfd;
                i++;
            }
        }

        boolean deviceReadyToWrite;
        boolean deviceReadyToRead;
        try {
            Log.d(TAG, "doOne: Polling " + polls.length + " file descriptors");
            int numberOfEvents = Os.poll(polls, this.vpnWatchDog.getPollTimeout());
            // TODO BUG - There is a bug where the watchdog keeps doing timeout if there is no network activity
            // TODO BUG - 0 Might be a valid value if no current DNS query and everything was already sent back to device
            if (numberOfEvents == 0) {
                this.vpnWatchDog.handleTimeout();
                return;
            }
            deviceReadyToWrite = (deviceFd.revents & POLLOUT) != 0;
            deviceReadyToRead = (deviceFd.revents & POLLIN) != 0;
        } catch (ErrnoException e) {
            throw new IOException("Failed to wait for event on file descriptors. Error number: " + e.errno, e);
        }

        // Need to do this before reading from the device, otherwise a new insertion there could
        // invalidate one of the sockets we want to read from either due to size or time out
        // constraints
        checkForDnsResponse();
        if (deviceReadyToWrite) {
            writeToDevice(fileOutputStream);
        }
        if (deviceReadyToRead) {
            readPacketFromDevice(inputStream, packet);
        }
    }

    private void checkForDnsResponse() {
        Iterator<DnsQuery> iterator = this.dnsQueryQueue.iterator();
        while (iterator.hasNext()) {
            DnsQuery query = iterator.next();
            if (query.isAnswered()) {
                iterator.remove();
                try {
                    Log.d(TAG, "Read from DNS socket" + query.socket);
                    handleRawDnsResponse(query);
                } catch (IOException e) {
                    Log.w(TAG, "Could not handle DNS response.", e);
                }
            }
        }
    }

    private void handleRawDnsResponse(DnsQuery query) throws IOException {
        byte[] responseData = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length);
        query.socket.receive(responsePacket);
        query.socket.close();
        this.dnsPacketProxy.handleDnsResponse(query.packet, responseData);
    }

    private void writeToDevice(FileOutputStream fileOutputStream) throws VpnNetworkException {
        Log.d(TAG, "Write to device " + this.deviceWrites.size() + " packets.");
        try {
            while (!this.deviceWrites.isEmpty()) {
                byte[] ipPacketData = this.deviceWrites.poll();
                fileOutputStream.write(ipPacketData);
            }
        } catch (IOException e) {
            // TODO: Make this more specific, only for: "File descriptor closed"
            throw new VpnNetworkException("Outgoing VPN output stream closed", e);
        }
    }

    private void readPacketFromDevice(FileInputStream inputStream, byte[] packet) throws IOException {
        Log.d(TAG, "Read a packet from device.");
        // Read the outgoing packet from the input stream.
        int length = inputStream.read(packet);
        if (length == 0) {
            // TODO: Possibly change to exception
            Log.w(TAG, "Got empty packet!");
            return;
        }
        final byte[] readPacket = Arrays.copyOfRange(packet, 0, length);

        vpnWatchDog.handlePacket(readPacket);
        dnsPacketProxy.handleDnsRequest(readPacket);
    }

    public void forwardPacket(DatagramPacket outPacket, IpPacket parsedPacket) throws IOException {
        DatagramSocket dnsSocket = null;
        try {
            // Packets to be sent to the real DNS server will need to be protected from the VPN
            dnsSocket = new DatagramSocket();

            vpnService.protect(dnsSocket);

            dnsSocket.send(outPacket);

            if (parsedPacket != null)
                dnsQueryQueue.add(new DnsQuery(dnsSocket, parsedPacket));
            else
                closeOrWarn(dnsSocket, TAG, "handleDnsRequest: Cannot close socket in error");
        } catch (IOException e) {
            closeOrWarn(dnsSocket, TAG, "handleDnsRequest: Cannot close socket in error");
            if (e.getCause() instanceof ErrnoException) {
                ErrnoException errnoExc = (ErrnoException) e.getCause();
                if ((errnoExc.errno == OsConstants.ENETUNREACH) || (errnoExc.errno == OsConstants.EPERM)) {
                    throw new IOException("Cannot send message:", e);
                }
            }
            Log.w(TAG, "handleDnsRequest: Could not send packet to upstream", e);
        }
    }

    public void queueDeviceWrite(IpPacket ipOutPacket) {
        byte[] rawData = ipOutPacket.getRawData();
        // TODO Check why data could be null
        if (rawData != null) {
            deviceWrites.add(rawData);
        }
    }


    static <T extends Closeable> T closeOrWarn(T fd, String tag, String message) {
        try {
            if (fd != null)
                fd.close();
        } catch (Exception e) {
            Log.e(tag, "closeOrWarn: " + message, e);
        }
        return null;
    }
}
