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

import static android.system.OsConstants.ENETUNREACH;
import static android.system.OsConstants.EPERM;
import static android.system.OsConstants.POLLIN;
import static android.system.OsConstants.POLLOUT;
import static android.system.OsConstants.POLLERR;
import static android.system.OsConstants.POLLHUP;
import static android.system.OsConstants.POLLNVAL;
import static org.adaway.vpn.VpnStatus.RECONNECTING_NETWORK_ERROR;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STARTING;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.adaway.vpn.VpnStatus.STOPPING;
import static org.adaway.vpn.worker.VpnBuilder.establish;

import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructPollfd;

import org.adaway.helper.PreferenceHelper;
import org.adaway.vpn.VpnService;
import org.adaway.vpn.dns.DnsPacketProxy;
import org.adaway.vpn.dns.DnsQueryQueue;
import org.adaway.vpn.dns.DnsServerMapper;
import org.pcap4j.packet.IpPacket;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import timber.log.Timber;

// TODO Write document
// TODO It is thread safe
// TODO Rework status notification
// TODO Improve exception handling in work()
public class VpnWorker implements DnsPacketProxy.EventLoop {
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
    private final VpnPacketProcessor packetProcessor;

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
     * Whether the worker is started (prevents double-start races).
     */
    private final AtomicBoolean started;
    /**
     * The VPN network interface, (<code>null</code> if not established).
     */
    private final AtomicReference<ParcelFileDescriptor> vpnNetworkInterface;
    private long lastPollActivity;

    /**
     * Constructor.
     *
     * @param vpnService The VPN service, also used as {@link android.content.Context}.
     */
    public VpnWorker(VpnService vpnService) {
        this.vpnService = vpnService;
        this.deviceWrites = new ConcurrentLinkedQueue<>();
        this.dnsQueryQueue = new DnsQueryQueue();
        this.dnsServerMapper = new DnsServerMapper();
        this.dnsPacketProxy = new DnsPacketProxy(this, this.dnsServerMapper);
        this.connectionThrottler = new VpnConnectionThrottler();
        this.connectionMonitor = new VpnConnectionMonitor(
                () -> this.vpnService.requestReconnect(this));
        this.vpnWatchDog = new VpnWatchdog(this.vpnService::protect);
        this.packetProcessor = new VpnPacketProcessor(
                this.deviceWrites,
                this.dnsPacketProxy::handleDnsRequest,
                this.vpnWatchDog::handlePacket);
        this.executor = new AtomicReference<>(null);
        this.vpnNetworkInterface = new AtomicReference<>(null);
        this.started = new AtomicBoolean(false);
    }

    /**
     * Start the VPN worker.
     * If already running, restart it.
     */
    public synchronized void start() {
        if (this.started.get()) {
            Timber.d("VPN worker already started; restarting.");
            stop();
        }
        this.started.set(true);
        Timber.d("Starting VPN thread…");
        ExecutorService exec = Executors.newFixedThreadPool(2);
        setExecutor(exec);
        exec.submit(this::work);
        exec.submit(this.connectionMonitor::monitor);
        Timber.i("VPN thread started.");
    }

    /**
     * Stop the VPN worker.
     */
    public synchronized void stop() {
        Timber.d("Stopping VPN thread.");
        this.started.set(false);
        this.connectionMonitor.stop();
        forceCloseTunnel();
        setExecutor(null);
        Timber.i("VPN thread stopped.");
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
            Timber.d("Shutting down VPN executor…");
            oldExecutor.shutdownNow();
            try {
                if (!oldExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    Timber.d("VPN executor did not terminate quickly.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Timber.d("VPN executor shut down.");
        }
    }

    /**
     * Force close the tunnel connection.
     */
    private void forceCloseTunnel() {
        ParcelFileDescriptor networkInterface = this.vpnNetworkInterface.getAndSet(null);
        if (networkInterface != null) {
            try {
                networkInterface.close();
            } catch (IOException e) {
                Timber.tag("Failed to close VPN network interface.").w(e);
            }
        }
    }

    private void work() {
        try {
            workUntilStopped();
        } catch (RuntimeException e) {
            Timber.e(e, "VPN worker stopped after an unexpected failure.");
        } finally {
            this.started.set(false);
            this.connectionMonitor.stop();
            forceCloseTunnel();
            this.dnsQueryQueue.close();
            this.deviceWrites.clear();
            this.vpnService.notifyVpnStatus(this, STOPPED);
        }
    }

    private void workUntilStopped() {
        Timber.d("Starting work…");
        // Initialize context
        this.dnsPacketProxy.initialize(this.vpnService);
        // Initialize the watchdog
        this.vpnWatchDog.initialize(PreferenceHelper.getVpnWatchdogEnabled(this.vpnService));
        // Try connecting the vpn continuously
        while (!Thread.currentThread().isInterrupted() && this.started.get()) {
            try {
                this.connectionThrottler.throttle();
                if (Thread.currentThread().isInterrupted() || !this.started.get()) {
                    break;
                }
                this.vpnService.notifyVpnStatus(this, STARTING);
                runVpn();
                Timber.i("Told to stop");
                this.vpnService.notifyVpnStatus(this, STOPPING);
                break;
            } catch (InterruptedException e) {
                Timber.d(e, "Failed to wait for connexion throttling.");
                Thread.currentThread().interrupt();
                break;
            } catch (SecurityException e) {
                Timber.w(e, "VPN permission is no longer available.");
                break;
            } catch (VpnNetworkException | IOException | IllegalStateException e) {
                if (Thread.currentThread().isInterrupted() || !this.started.get()) {
                    break;
                }
                Timber.w(e, "Network exception in vpn thread, reconnecting…");
                // If an exception was thrown, notify status and try again
                this.vpnService.notifyVpnStatus(this, RECONNECTING_NETWORK_ERROR);
            }
        }
        Timber.d("Exiting work.");
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
            if (Thread.currentThread().isInterrupted() || !this.started.get()) {
                return;
            }
            // Initialize connection monitor
            this.connectionMonitor.initialize();

            // Update address to ping with default DNS server
            this.vpnWatchDog.setTarget(this.dnsServerMapper.getDefaultDnsServerAddress());

            // Now we are connected. Set the flag and show the message.
            this.vpnService.notifyVpnStatus(this, RUNNING);
            this.lastPollActivity = System.nanoTime();

            // We keep forwarding packets till something goes wrong.
            boolean deviceOpened = true;
            while (deviceOpened && this.started.get() && !Thread.currentThread().isInterrupted()) {
                deviceOpened = doOne(inputStream, outputStream, packet);
            }
        } finally {
            this.vpnNetworkInterface.set(null);
            this.connectionMonitor.clearNetworkInterface();
            this.dnsQueryQueue.close();
            this.deviceWrites.clear();
        }
    }

    private boolean doOne(FileInputStream inputStream, FileOutputStream fileOutputStream, byte[] packet)
            throws IOException, VpnNetworkException {
        // Create poll FD on tunnel
        StructPollfd deviceFd = new StructPollfd();
        deviceFd.fd = inputStream.getFD();
        deviceFd.events = (short) POLLIN;
        boolean deviceWaitingToWrite = this.packetProcessor.hasDeviceWrites();
        if (deviceWaitingToWrite) {
            deviceFd.events |= (short) POLLOUT;
        }
        // Create poll FD on each DNS query socket
        StructPollfd[] queryFds = this.dnsQueryQueue.getQueryFds();
        StructPollfd[] polls = new StructPollfd[1 + queryFds.length];
        polls[0] = deviceFd;
        System.arraycopy(queryFds, 0, polls, 1, queryFds.length);
        boolean deviceReadyToWrite;
        boolean deviceReadyToRead;
        try {
            Timber.d("doOne: Polling %d file descriptors.", polls.length);
            int watchdogTimeout = this.vpnWatchDog.getPollTimeout();
            // Bound idle waits so cancellation and query expiry do not depend on traffic.
            int pollTimeout = boundedPollTimeout(watchdogTimeout);
            int numberOfEvents = Os.poll(polls, pollTimeout);
            if (numberOfEvents == 0) {
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - this.lastPollActivity);
                if (watchdogTimeout >= 0 && elapsedMs >= watchdogTimeout) {
                    this.lastPollActivity = System.nanoTime();
                    if (!isIdlePollTimeout(deviceWaitingToWrite, queryFds.length)) {
                        this.vpnWatchDog.handleTimeout();
                    }
                }
                return true;
            }
            this.lastPollActivity = System.nanoTime();
            if ((deviceFd.revents & (POLLERR | POLLHUP | POLLNVAL)) != 0) {
                throw new IOException("VPN tunnel is no longer available.");
            }
            deviceReadyToWrite = (deviceFd.revents & POLLOUT) != 0;
            deviceReadyToRead = (deviceFd.revents & POLLIN) != 0;
        } catch (ErrnoException e) {
            throw new IOException("Failed to wait for event on file descriptors. Error number: " + e.errno, e);
        }

        // Need to do this before reading from the device, otherwise a new insertion there could
        // invalidate one of the sockets we want to read from either due to size or time out
        // constraints
        this.dnsQueryQueue.handleResponses();
        if (deviceReadyToWrite) {
            writeToDevice(fileOutputStream);
        }
        if (deviceReadyToRead) {
            return readPacketFromDevice(inputStream, packet) != -1;
        }
        return true;
    }

    static boolean isIdlePollTimeout(boolean deviceWaitingToWrite, int pendingDnsQueries) {
        return !deviceWaitingToWrite && pendingDnsQueries == 0;
    }

    static int boundedPollTimeout(int watchdogTimeout) {
        return watchdogTimeout < 0 ? 1000 : Math.min(1000, watchdogTimeout);
    }

    private void writeToDevice(FileOutputStream fileOutputStream) throws IOException {
        this.packetProcessor.writeToDevice(fileOutputStream);
    }

    private int readPacketFromDevice(FileInputStream inputStream, byte[] packet) throws IOException {
        return this.packetProcessor.readPacketFromDevice(inputStream, packet);
    }

    @Override
    public void forwardPacket(DatagramPacket packet) throws IOException {
        try (DatagramSocket dnsSocket = openDnsSocket(packet, this.vpnService::protect)) {
            dnsSocket.send(packet);
        } catch (IOException e) {
            throw new IOException("Failed to forward packet.", e);
        }
    }

    @Override
    public void forwardPacket(DatagramPacket outPacket, Consumer<byte[]> callback) throws IOException {
        DatagramSocket dnsSocket = openDnsSocket(outPacket, this.vpnService::protect);
        try {
            dnsSocket.send(outPacket);
            // Enqueue DNS query
            this.dnsQueryQueue.addQuery(dnsSocket, callback);
        } catch (IOException e) {
            if (dnsSocket != null) {
                dnsSocket.close();
            }
            if (e.getCause() instanceof ErrnoException) {
                ErrnoException errnoExc = (ErrnoException) e.getCause();
                if ((errnoExc.errno == ENETUNREACH) || (errnoExc.errno == EPERM)) {
                    throw new IOException("Cannot send message:", e);
                }
            }
            Timber.w(e, "handleDnsRequest: Could not send packet to upstream");
        } catch (RuntimeException e) {
            if (dnsSocket != null) {
                dnsSocket.close();
            }
            throw e;
        }
    }

    static DatagramSocket openDnsSocket(DatagramPacket packet, SocketProtector protector)
            throws IOException {
        DatagramSocket socket = new DatagramSocket();
        try {
            if (!protector.protect(socket)) {
                throw new IOException("Failed to protect DNS socket from the VPN.");
            }
            // Accept replies only from the resolver and port this query was sent to.
            socket.connect(packet.getAddress(), packet.getPort());
            return socket;
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    @Override
    public void queueDeviceWrite(IpPacket ipOutPacket) {
        this.packetProcessor.queueDeviceWrite(ipOutPacket);
    }
}
