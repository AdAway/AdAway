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

import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.os.Build.VERSION.SDK_INT;
import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.POLLERR;
import static android.system.OsConstants.POLLHUP;
import static android.system.OsConstants.POLLIN;
import static android.system.OsConstants.POLLOUT;
import static org.adaway.vpn.VpnStatus.RECONNECTING_NETWORK_ERROR;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STARTING;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.adaway.vpn.VpnStatus.STOPPING;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import androidx.annotation.Nullable;

import org.adaway.helper.PreferenceHelper;
import org.adaway.ui.home.HomeActivity;
import org.adaway.vpn.dns.DnsPacketProxy;
import org.adaway.vpn.dns.DnsPacketProxy2;
import org.adaway.vpn.dns.DnsQuery;
import org.adaway.vpn.dns.DnsQueryQueue;
import org.adaway.vpn.dns.DnsServerMapper;
import org.pcap4j.packet.IpPacket;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import timber.log.Timber;

// TODO Write document
// TODO It is thread safe
class VpnWorker implements DnsPacketProxy.EventLoop {
    private static final String TAG = "VpnWorker";
    /**
     * Maximum packet size is constrained by the MTU, which is given as a signed short.
     */
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    private static final int MIN_RETRY_TIME = 5;
    private static final int MAX_RETRY_TIME = 2 * 60;
    /* If we had a successful connection for that long, reset retry timeout */
    private static final long RETRY_RESET_SEC = 60;

    /**
     * The Android VPN service, also used as {@link android.content.Context}.
     */
    private final android.net.VpnService vpnService;
    /**
     * The callback to notify of VPN status update.
     */
    private final VpnStatusNotifier vpnStatusNotifier;
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
    private final DnsPacketProxy2 dnsPacketProxy;
    // Watch dog that checks our connection is alive.
    private final VpnWatchdog vpnWatchDog;

    /**
     * The VPN worker thread reference, ({@code null} if not running.
     */
    private final AtomicReference<Thread> thread;

    /**
     * File descriptor to read end of OS pipe to poll to check VPN worker stop request.
     */
    private FileDescriptor mBlockFd;
    /**
     * File descriptor to write end of OS pipe to close stop VPN worker thread.
     */
    private FileDescriptor mInterruptFd;

    /**
     * Constructor.
     *
     * @param vpnService        The Android VPN service, also used as {@link android.content.Context}.
     * @param vpnStatusNotifier The callback to notify of VPN status update.
     */
    VpnWorker(android.net.VpnService vpnService, VpnStatusNotifier vpnStatusNotifier) {
        this.vpnService = vpnService;
        this.vpnStatusNotifier = vpnStatusNotifier;
        this.deviceWrites = new LinkedList<>();
        this.dnsQueryQueue = new DnsQueryQueue();
        this.dnsServerMapper = new DnsServerMapper(this.vpnService);
        this.dnsPacketProxy = new DnsPacketProxy2(this, this.dnsServerMapper);
        this.vpnWatchDog = new VpnWatchdog();
        this.thread = new AtomicReference<>(null);
    }

    /**
     * Start the VPN worker.
     */
    public void start() {
        Log.i(TAG, "Starting VPN thread…");
        Thread workerThread = new Thread(this::work, "VpnWorker");
        setWorkerThread(workerThread);
        workerThread.start();
        Log.i(TAG, "VPN thread started.");
    }

    /**
     * Stop the VPN worker.
     */
    public void stop() {
        Log.i(TAG, "Stopping VPN thread.");
        setWorkerThread(null);
        Log.i(TAG, "VPN thread stopped.");
    }

    /**
     * Keep track of the worker thread.<br>
     * Interrupt the previous one if exists.
     *
     * @param workerThread The new worker thread, {@code null} if no worker thread any more.
     */
    private void setWorkerThread(@Nullable Thread workerThread) {
        Thread oldWorkerThread = this.thread.getAndSet(workerThread);
        if (oldWorkerThread != null) {
            oldWorkerThread.interrupt();
        }
    }

    private void work() {
        Log.i(TAG, "Starting");
        // Initialize context
        this.dnsPacketProxy.initialize(this.vpnService);
        // Initialize the watchdog
        this.vpnWatchDog.initialize(PreferenceHelper.getVpnWatchdogEnabled(this.vpnService));

        this.vpnStatusNotifier.accept(STARTING);

        int retryTimeout = MIN_RETRY_TIME;
        // Try connecting the vpn continuously
        while (true) {
            long connectTimeMillis = 0;
            try {
                connectTimeMillis = System.currentTimeMillis();
                // If the function returns, that means it was interrupted
                runVpn();

                Log.i(TAG, "Told to stop");
                this.vpnStatusNotifier.accept(STOPPING);
                break;
            } catch (VpnNetworkException e) {
                // We want to filter out VpnNetworkException from out crash analytics as these
                // are exceptions that we expect to happen from network errors
                Log.w(TAG, "Network exception in vpn thread, ignoring and reconnecting", e);
                // If an exception was thrown, show to the user and try again
                this.vpnStatusNotifier.accept(RECONNECTING_NETWORK_ERROR);
            } catch (Exception e) {
                Log.e(TAG, "Network exception in vpn thread, reconnecting", e);
                //ExceptionHandler.saveException(e, Thread.currentThread(), null);
                this.vpnStatusNotifier.accept(RECONNECTING_NETWORK_ERROR);
            }

            if (System.currentTimeMillis() - connectTimeMillis >= RETRY_RESET_SEC * 1000) {
                Log.i(TAG, "Resetting timeout");
                retryTimeout = MIN_RETRY_TIME;
            }

            // ...wait and try again
            Log.i(TAG, "Retrying to connect in " + retryTimeout + "seconds...");
            try {
                Thread.sleep((long) retryTimeout * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (retryTimeout < MAX_RETRY_TIME)
                retryTimeout *= 2;
        }

        this.vpnStatusNotifier.accept(STOPPED);
        Log.i(TAG, "Exiting");
    }

    private void runVpn() throws IOException, ErrnoException, VpnNetworkException {
        // Allocate the buffer for a single packet.
        byte[] packet = new byte[MAX_PACKET_SIZE];

        // A pipe we can interrupt the poll() call with by closing the interruptFd end
        FileDescriptor[] pipes = Os.pipe();
        this.mInterruptFd = pipes[0];
        this.mBlockFd = pipes[1];

        // Authenticate and configure the virtual network interface.
        try (ParcelFileDescriptor pfd = configure();
             // Read and write views of the tunnel device
             FileInputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
             FileOutputStream outputStream = new FileOutputStream(pfd.getFileDescriptor())) {

            // Now we are connected. Set the flag and show the message.
            this.vpnStatusNotifier.accept(RUNNING);

            // We keep forwarding packets till something goes wrong.
            while (doOne(inputStream, outputStream, packet)) {
            }
        } finally {
            this.mBlockFd = closeOrWarn(mBlockFd, TAG, "runVpn: Could not close blockFd");
            this.mInterruptFd = closeOrWarn(mInterruptFd, TAG, "runVpn: Could not close interruptFd");
        }
    }

    private boolean doOne(FileInputStream inputStream, FileOutputStream fileOutputStream, byte[] packet)
            throws IOException, ErrnoException, VpnNetworkException {
        // Create poll FD on tunnel
        StructPollfd deviceFd = new StructPollfd();
        deviceFd.fd = inputStream.getFD();
        deviceFd.events = (short) POLLIN;
        if (!deviceWrites.isEmpty()) {
            deviceFd.events |= (short) POLLOUT;
        }
        // Create poll FD on OS pipe for interruption on VPN worker stop
        StructPollfd blockFd = new StructPollfd();
        blockFd.fd = mBlockFd;
        blockFd.events = (short) (POLLHUP | POLLERR);
        // Create poll FD on each DNS query socket
        StructPollfd[] polls = new StructPollfd[2 + this.dnsQueryQueue.size()];
        polls[0] = deviceFd;
        polls[1] = blockFd;
        {
            int i = 2;
            for (DnsQuery query : this.dnsQueryQueue) {
                polls[i] = query.pollfd;
                i++;
            }
        }

        Log.d(TAG, "doOne: Polling " + polls.length + " file descriptors");
        int numberOfEvents = Os.poll(polls, this.vpnWatchDog.getPollTimeout());
        // TODO BUG - There is a bug where the watchdog keeps doing timeout if there is no network activity
        // TODO BUG - 0 Might be a valid value if no current DNS query and everything was already sent back to device
        if (numberOfEvents == 0) {
            this.vpnWatchDog.handleTimeout();
            return true;
        }
        if (blockFd.revents != 0) {
            Log.i(TAG, "Told to stop VPN");
            return false;
        }

        // Need to do this before reading from the device, otherwise a new insertion there could
        // invalidate one of the sockets we want to read from either due to size or time out
        // constraints
        checkForDnsResponse();
        if ((deviceFd.revents & POLLOUT) != 0) {
            Log.d(TAG, "Write to device " + this.deviceWrites.size() + " packets");
            writeToDevice(fileOutputStream);
        }
        if ((deviceFd.revents & POLLIN) != 0) {
            Log.d(TAG, "Read from device");
            readPacketFromDevice(inputStream, packet);
        }

        return true;
    }

    private void checkForDnsResponse() {
        Iterator<DnsQuery> iterator = this.dnsQueryQueue.iterator();
        while (iterator.hasNext()) {
            DnsQuery query = iterator.next();
            if (query.isAnswered()) {
                Log.d(TAG, "Read from DNS socket" + query.socket);
                iterator.remove();
                try {
                    handleRawDnsResponse(query);
                } catch (IOException e) {
                    Log.w(TAG, "checkForDnsResponse: Could not handle DNS response", e);
                }
            }
        }
    }

    private void writeToDevice(FileOutputStream fileOutputStream) throws VpnNetworkException {
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

    private void readPacketFromDevice(FileInputStream inputStream, byte[] packet) throws VpnNetworkException {
        try {
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
        } catch (IOException e) {
            throw new VpnNetworkException("Cannot read from device", e);
        }
    }

    public void forwardPacket(DatagramPacket outPacket, IpPacket parsedPacket) throws VpnNetworkException {
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
                    throw new VpnNetworkException("Cannot send message:", e);
                }
            }
            Log.w(TAG, "handleDnsRequest: Could not send packet to upstream", e);
        }
    }

    private void handleRawDnsResponse(DnsQuery query) throws IOException {
        byte[] datagramData = new byte[1024];
        DatagramPacket replyPacket = new DatagramPacket(datagramData, datagramData.length);
        query.socket.receive(replyPacket);
        query.socket.close();
        this.dnsPacketProxy.handleDnsResponse(query.packet, datagramData);
    }

    public void queueDeviceWrite(IpPacket ipOutPacket) {
        deviceWrites.add(ipOutPacket.getRawData());
    }

    private void configurePackages(VpnService.Builder builder) {
        PackageManager packageManager = this.vpnService.getPackageManager();

        ApplicationInfo self = this.vpnService.getApplicationInfo();
        Set<String> excludedApps = PreferenceHelper.getVpnExcludedApps(this.vpnService);
        String vpnExcludedSystemApps = PreferenceHelper.getVpnExcludedSystemApps(this.vpnService);
        Set<String> webBrowserPackageName = vpnExcludedSystemApps.equals("allExceptBrowsers") ? getWebBrowserPackageName(packageManager) : Collections.emptySet();

        List<ApplicationInfo> installedApplications = packageManager.getInstalledApplications(0);
        for (ApplicationInfo applicationInfo : installedApplications) {
            boolean excluded = false;
            // Skip itself
            if (applicationInfo.packageName.equals(self.packageName)) {
                continue;
            }
            // Check system app
            if ((applicationInfo.flags & FLAG_SYSTEM) != 0) {
                excluded = vpnExcludedSystemApps.equals("all") ||
                        (vpnExcludedSystemApps.equals("allExceptBrowsers") && !webBrowserPackageName.contains(applicationInfo.packageName));
            }
            // Check user excluded applications
            else if (excludedApps.contains(applicationInfo.packageName)) {
                excluded = true;
            }
            if (excluded) {
                try {
                    builder.addDisallowedApplication(applicationInfo.packageName);
                } catch (NameNotFoundException e) {
                    Timber.w(e, "Failed to exclude application " + applicationInfo.packageName + " from VPN");
                }
            }
        }
    }

    private Set<String> getWebBrowserPackageName(PackageManager packageManager) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://isabrowser.adaway.org/"));
        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, 0);
        Set<String> packageNames = new HashSet<>();
        for (ResolveInfo resolveInfo : resolveInfoList) {
            packageNames.add(resolveInfo.activityInfo.packageName);
        }

        packageNames.add("com.google.android.webview");
        packageNames.add("com.android.htmlviewer");
        packageNames.add("com.google.android.backuptransport");
        packageNames.add("com.google.android.gms");
        packageNames.add("com.google.android.gsf");

        return packageNames;
    }

    private ParcelFileDescriptor configure() throws VpnNetworkException {
        Log.i(TAG, "Configuring" + this);

        // TODO User configuration
//        Configuration config = FileHelper.loadCurrentSettings(vpnService);

        // Configure a builder while parsing the parameters.
        VpnService.Builder builder = this.vpnService.new Builder();

        InetAddress address = this.dnsServerMapper.configure(builder);
        this.vpnWatchDog.setTarget(address);

        builder.setBlocking(true);

        // Allow applications to bypass the VPN
        builder.allowBypass();

        // Set the VPN to unmetered
        if (SDK_INT >= VERSION_CODES.Q) {
            builder.setMetered(false);
        }

        configurePackages(builder);

        // Explicitly allow both families, so we do not block
        // traffic for ones without DNS servers (issue 129).
        builder.allowFamily(AF_INET);
        builder.allowFamily(AF_INET6);

        // Create a new interface using the builder and save the parameters.
        ParcelFileDescriptor pfd = builder
                .setSession("AdAway")
                .setConfigureIntent(PendingIntent.getActivity(
                        vpnService,
                        1,
                        new Intent(vpnService, HomeActivity.class),
                        FLAG_CANCEL_CURRENT | FLAG_IMMUTABLE
                ))
                .establish();
        Log.i(TAG, "Configured");
        return pfd;
    }

    @FunctionalInterface
    interface VpnStatusNotifier extends Consumer<VpnStatus> {
    }

    static FileDescriptor closeOrWarn(FileDescriptor fd, String tag, String message) {
        try {
            if (fd != null)
                Os.close(fd);
        } catch (ErrnoException e) {
            Log.e(tag, "closeOrWarn: " + message, e);
        }
        return null;
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
