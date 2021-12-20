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
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import androidx.annotation.NonNull;

import org.adaway.helper.PreferenceHelper;
import org.adaway.ui.home.HomeActivity;
import org.pcap4j.packet.IpPacket;

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
import java.util.function.Consumer;

import timber.log.Timber;

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
    /* Maximum number of responses we want to wait for */
    private static final int DNS_MAXIMUM_WAITING = 1024;
    private static final long DNS_TIMEOUT_SEC = 10;
    private final android.net.VpnService vpnService;
    private final VpnStatusNotifier statusNotifier;
    /* Data to be written to the device */
    private final Queue<byte[]> deviceWrites = new LinkedList<>();
    // HashMap that keeps an upper limit of packets
    private final WospList dnsIn = new WospList();
    // The mapping between fake and real dns addresses
    private final DnsServerMapper dnsServerMapper;
    // The object where we actually handle packets.
    private final DnsPacketProxy dnsPacketProxy;
    // Watch dog that checks our connection is alive.
    private final VpnWatchdog vpnWatchDog;

    /**
     * The VPN worker thread ({@code null} if not running.
     */
    private Thread thread;
    /**
     * File descriptor to read end of OS pipe to poll to check VPN worker stop request.
     */
    private FileDescriptor mBlockFd;
    /**
     * File descriptor to write end of OS pipe to close stop VPN worker thread.
     */
    private FileDescriptor mInterruptFd;

    VpnWorker(android.net.VpnService vpnService, VpnStatusNotifier statusNotifier) {
        this.vpnService = vpnService;
        this.statusNotifier = statusNotifier;
        this.dnsServerMapper = new DnsServerMapper(this.vpnService);
        this.dnsPacketProxy = new DnsPacketProxy(this, this.dnsServerMapper);
        this.vpnWatchDog = new VpnWatchdog();
    }

    public void start() {
        Log.i(TAG, "Starting Vpn Thread");
        this.thread = new Thread(this::work, "VpnWorker");
        this.thread.start();
        Log.i(TAG, "Vpn Thread started");
    }

    public void stop() {
        Log.i(TAG, "Stopping Vpn Thread");
        if (this.thread == null) {
            return;
        }
        this.thread.interrupt();

        mInterruptFd = FileHelper.closeOrWarn(mInterruptFd, TAG, "stop: Could not close interruptFd");
        try {
            this.thread.join(2000);
        } catch (InterruptedException e) {
            Log.w(TAG, "stop: Interrupted while joining thread", e);
            Thread.currentThread().interrupt();
        }
        if (this.thread.isAlive()) {
            Log.w(TAG, "stop: Could not kill VPN thread, it is still alive");
        } else {
            this.thread = null;
            Log.i(TAG, "Vpn Thread stopped");
        }
    }

    private void work() {
        Log.i(TAG, "Starting");
        // Initialize context
        this.dnsPacketProxy.initialize(this.vpnService);
        // Initialize the watchdog
        this.vpnWatchDog.initialize(PreferenceHelper.getVpnWatchdogEnabled(this.vpnService));

        this.statusNotifier.accept(STARTING);

        int retryTimeout = MIN_RETRY_TIME;
        // Try connecting the vpn continuously
        while (true) {
            long connectTimeMillis = 0;
            try {
                connectTimeMillis = System.currentTimeMillis();
                // If the function returns, that means it was interrupted
                runVpn();

                Log.i(TAG, "Told to stop");
                this.statusNotifier.accept(STOPPING);
                break;
            } catch (VpnNetworkException e) {
                // We want to filter out VpnNetworkException from out crash analytics as these
                // are exceptions that we expect to happen from network errors
                Log.w(TAG, "Network exception in vpn thread, ignoring and reconnecting", e);
                // If an exception was thrown, show to the user and try again
                this.statusNotifier.accept(RECONNECTING_NETWORK_ERROR);
            } catch (Exception e) {
                Log.e(TAG, "Network exception in vpn thread, reconnecting", e);
                //ExceptionHandler.saveException(e, Thread.currentThread(), null);
                this.statusNotifier.accept(RECONNECTING_NETWORK_ERROR);
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

        this.statusNotifier.accept(STOPPED);
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
            this.statusNotifier.accept(RUNNING);

            // We keep forwarding packets till something goes wrong.
            while (doOne(inputStream, outputStream, packet)) {
            }
        } finally {
            this.mBlockFd = FileHelper.closeOrWarn(mBlockFd, TAG, "runVpn: Could not close blockFd");
            this.mInterruptFd = FileHelper.closeOrWarn(mInterruptFd, TAG, "runVpn: Could not close interruptFd");
        }
    }

    private boolean doOne(FileInputStream inputStream, FileOutputStream fileOutputStream, byte[] packet)
            throws IOException, ErrnoException, VpnNetworkException {
        // Create poll FD on tunnel
        StructPollfd deviceFd = new StructPollfd();
        deviceFd.fd = inputStream.getFD();
        deviceFd.events = (short) OsConstants.POLLIN;
        if (!deviceWrites.isEmpty()) {
            deviceFd.events |= (short) OsConstants.POLLOUT;
        }
        // Create poll FD on OS pipe for interruption on VPN worker stop
        StructPollfd blockFd = new StructPollfd();
        blockFd.fd = mBlockFd;
        blockFd.events = (short) (OsConstants.POLLHUP | OsConstants.POLLERR);
        // Create poll FD on each DNS query socket
        StructPollfd[] polls = new StructPollfd[2 + this.dnsIn.size()];
        polls[0] = deviceFd;
        polls[1] = blockFd;
        {
            int i = 2;
            for (WaitingOnSocketPacket wosp : this.dnsIn) {
                StructPollfd pollFd = new StructPollfd();
                pollFd.fd = ParcelFileDescriptor.fromDatagramSocket(wosp.socket).getFileDescriptor();
                pollFd.events = (short) OsConstants.POLLIN;
                polls[i] = pollFd;
                i++;
            }
        }

        Log.d(TAG, "doOne: Polling " + polls.length + " file descriptors");
        int result = Os.poll(polls, this.vpnWatchDog.getPollTimeout());
        if (result == 0) {
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
        checkForDnsResponse(polls);
        if ((deviceFd.revents & OsConstants.POLLOUT) != 0) {
            Log.d(TAG, "Write to device");
            writeToDevice(fileOutputStream);
        }
        if ((deviceFd.revents & OsConstants.POLLIN) != 0) {
            Log.d(TAG, "Read from device");
            readPacketFromDevice(inputStream, packet);
        }

        return true;
    }

    private void checkForDnsResponse(StructPollfd[] polls) {
        int i = 2;
        Iterator<WaitingOnSocketPacket> iterator = dnsIn.iterator();
        while (iterator.hasNext()) {
            WaitingOnSocketPacket wosp = iterator.next();
            if ((polls[i].revents & OsConstants.POLLIN) != 0) {
                Log.d(TAG, "Read from DNS socket" + wosp.socket);
                iterator.remove();
                try {
                    handleRawDnsResponse(wosp);
                } catch (IOException e) {
                    Log.w(TAG, "checkForDnsResponse: Could not handle DNS response", e);
                }
            }
            i++;
        }
    }

    private void writeToDevice(FileOutputStream fileOutputStream) throws VpnNetworkException {
        try {
            byte[] ipPacketData = deviceWrites.poll();
            if (ipPacketData != null) {
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
                dnsIn.add(new WaitingOnSocketPacket(dnsSocket, parsedPacket));
            else
                FileHelper.closeOrWarn(dnsSocket, TAG, "handleDnsRequest: Cannot close socket in error");
        } catch (IOException e) {
            FileHelper.closeOrWarn(dnsSocket, TAG, "handleDnsRequest: Cannot close socket in error");
            if (e.getCause() instanceof ErrnoException) {
                ErrnoException errnoExc = (ErrnoException) e.getCause();
                if ((errnoExc.errno == OsConstants.ENETUNREACH) || (errnoExc.errno == OsConstants.EPERM)) {
                    throw new VpnNetworkException("Cannot send message:", e);
                }
            }
            Log.w(TAG, "handleDnsRequest: Could not send packet to upstream", e);
        }
    }

    private void handleRawDnsResponse(WaitingOnSocketPacket wosp) throws IOException {
        byte[] datagramData = new byte[1024];
        DatagramPacket replyPacket = new DatagramPacket(datagramData, datagramData.length);
        wosp.socket.receive(replyPacket);
        wosp.socket.close();
        dnsPacketProxy.handleDnsResponse(wosp.packet, datagramData);
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
        android.net.VpnService.Builder builder = this.vpnService.new Builder();

        InetAddress address = this.dnsServerMapper.configure(builder);
        this.vpnWatchDog.setTarget(address);

        builder.setBlocking(true);

        // Allow applications to bypass the VPN
        builder.allowBypass();

        // Set the VPN to unmetered
        if (SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }

        configurePackages(builder);

        // Explictly allow both families, so we do not block
        // traffic for ones without DNS servers (issue 129).
        builder.allowFamily(OsConstants.AF_INET);
        builder.allowFamily(OsConstants.AF_INET6);

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

    static class VpnNetworkException extends Exception {
        VpnNetworkException(String s) {
            super(s);
        }

        VpnNetworkException(String s, Throwable t) {
            super(s, t);
        }

    }

    /**
     * Helper class holding a socket, the packet we are waiting the answer for, and a time
     */
    private static class WaitingOnSocketPacket {
        final DatagramSocket socket;
        final IpPacket packet;
        private final long time;

        WaitingOnSocketPacket(DatagramSocket socket, IpPacket packet) {
            this.socket = socket;
            this.packet = packet;
            this.time = System.currentTimeMillis();
        }

        long ageSeconds() {
            return (System.currentTimeMillis() - time) / 1000;
        }
    }

    /**
     * Queue of WaitingOnSocketPacket, bound on time and space.
     */
    private static class WospList implements Iterable<WaitingOnSocketPacket> {
        private final LinkedList<WaitingOnSocketPacket> list = new LinkedList<>();

        void add(WaitingOnSocketPacket wosp) {
            if (list.size() > DNS_MAXIMUM_WAITING) {
                Log.d(TAG, "Dropping socket due to space constraints: " + list.element().socket);
                list.element().socket.close();
                list.remove();
            }
            while (!list.isEmpty() && list.element().ageSeconds() > DNS_TIMEOUT_SEC) {
                Log.d(TAG, "Timeout on socket " + list.element().socket);
                list.element().socket.close();
                list.remove();
            }
            list.add(wosp);
        }

        @NonNull
        public Iterator<WaitingOnSocketPacket> iterator() {
            return list.iterator();
        }

        int size() {
            return list.size();
        }

    }

}
