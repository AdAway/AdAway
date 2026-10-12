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

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static org.adaway.broadcast.Command.START;
import static org.adaway.broadcast.Command.STOP;
import static org.adaway.broadcast.CommandReceiver.SEND_COMMAND_ACTION;
import static org.adaway.helper.NotificationHelper.VPN_RESUME_SERVICE_NOTIFICATION_ID;
import static org.adaway.helper.NotificationHelper.VPN_RUNNING_SERVICE_NOTIFICATION_ID;
import static org.adaway.helper.NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL;
import static org.adaway.vpn.VpnService.NetworkType.CELLULAR;
import static org.adaway.vpn.VpnService.NetworkType.WIFI;
import static org.adaway.vpn.VpnStatus.RECONNECTING;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STARTING;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.adaway.vpn.VpnStatus.WAITING_FOR_NETWORK;
import static java.util.Objects.requireNonNull;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.adaway.R;
import org.adaway.broadcast.Command;
import org.adaway.broadcast.CommandReceiver;
import org.adaway.helper.PreferenceHelper;
import org.adaway.ui.home.HomeActivity;
import org.adaway.vpn.worker.VpnWorker;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

import timber.log.Timber;

/**
 * This class is the VPN platform service implementation.
 * <p>
 * it is in charge of:
 * <ul>
 * <li>Accepting service commands,</li>
 * <li>Starting / stopping the {@link VpnWorker} thread,</li>
 * <li>Publishing notifications and intent about the VPN state,</li>
 * <li>Reacting to network connectivity changes.</li>
 * </ul>
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class VpnService extends android.net.VpnService implements Handler.Callback {
    /*
     * Notification intent related.
     */
    private static final int REQUEST_CODE_START = 43;
    private static final int REQUEST_CODE_PAUSE = 42;
    /*
     * Handler related.
     */
    private static final int VPN_STATUS_UPDATE_MESSAGE_TYPE = 0;
    private static final int VPN_RECONNECT_MESSAGE_TYPE = 1;

    private final MyHandler handler;
    private final NetworkTypeCallback wifiNetworkCallback;
    private final NetworkTypeCallback cellularNetworkCallback;
    private final Set<NetworkType> availableNetworkTypes;
    private VpnWorker vpnWorker;
    private boolean vpnRequested;

    /**
     * Constructor.
     */
    public VpnService() {
        this.handler = new MyHandler(this);
        this.wifiNetworkCallback = new NetworkTypeCallback(WIFI);
        this.cellularNetworkCallback = new NetworkTypeCallback(CELLULAR);
        this.availableNetworkTypes = new HashSet<>();
    }

    /*
     * VPN Service.
     */

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.d("Creating VPN service…");
        registerNetworkCallback();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Timber.d("VpnService onStartCommand called. Intent: %s", intent);
        // Check null intent that happens when system restart the service
        // https://developer.android.com/reference/android/app/Service#START_STICKY
        Command command = intent == null ? START : Command.readFromIntent(intent);
        Timber.d("Command received: %s", command);
        switch (command) {
            case START:
                Timber.d("Processing START command");
                startVpn();
                return START_STICKY;
            case STOP:
                Timber.d("Processing STOP command");
                stopVpn();
                return START_NOT_STICKY;
            default:
                Timber.w("Unknown command: %s", command);
                return START_NOT_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        Timber.d("Destroying VPN service…");
        this.vpnRequested = false;
        stopWorker();
        this.handler.removeMessages(VPN_STATUS_UPDATE_MESSAGE_TYPE);
        this.handler.removeMessages(VPN_RECONNECT_MESSAGE_TYPE);
        PreferenceHelper.setVpnServiceStatus(this, STOPPED);
        unregisterNetworkCallback();
        super.onDestroy();
        Timber.d("Destroyed VPN service.");
    }

    @Override
    public void onRevoke() {
        this.handler.post(this::stopVpn);
    }

    /*
     * Handler callback.
     */

    @Override
    public boolean handleMessage(@NonNull Message message) {
        if (message.what == VPN_RECONNECT_MESSAGE_TYPE
                && this.vpnRequested && message.obj == this.vpnWorker) {
            reconnect();
        }
        if (message.what == VPN_STATUS_UPDATE_MESSAGE_TYPE
                && this.vpnRequested && message.obj == this.vpnWorker) {
            VpnStatus status = VpnStatus.fromCode(message.arg1);
            if (status == STOPPED) {
                stopVpn();
            } else {
                updateVpnStatus(status);
            }
        }
        return true;
    }

    public void requestReconnect(VpnWorker worker) {
        this.handler.obtainMessage(VPN_RECONNECT_MESSAGE_TYPE, worker).sendToTarget();
    }

    /**
     * Notify a of the new VPN status.
     *
     * @param status The new VPN status.
     */
    public void notifyVpnStatus(VpnWorker worker, VpnStatus status) {
        Message statusMessage = this.handler.obtainMessage(
                VPN_STATUS_UPDATE_MESSAGE_TYPE, status.toCode(), 0, worker);
        this.handler.sendMessage(statusMessage);
    }

    private void startVpn() {
        Timber.d("startVpn() called");
        this.vpnRequested = true;
        PreferenceHelper.setVpnServiceStatus(this, STARTING);
        updateVpnStatus(STARTING);
        startWorker();
        Timber.i("VPN service started.");
    }

    private void stopVpn() {
        Timber.d("Stopping VPN service…");
        this.vpnRequested = false;
        VpnServiceHeartbeat.stop(this);
        PreferenceHelper.setVpnServiceStatus(this, STOPPED);
        stopWorker();
        stopForeground(true);
        stopSelf();
        updateVpnStatus(STOPPED);
        Timber.i("VPN service stopped.");
    }

    private void waitForNetVpn() {
        stopWorker();
        updateVpnStatus(WAITING_FOR_NETWORK);
    }

    private void reconnect() {
        updateVpnStatus(RECONNECTING);
        startWorker();
    }

    private void startWorker() {
        stopWorker();
        // A retiring worker may still be returning from platform or database I/O.
        // Give the new tunnel its own queues, mapper and monitor.
        this.vpnWorker = new VpnWorker(this);
        this.vpnWorker.start();
    }

    private void stopWorker() {
        VpnWorker worker = this.vpnWorker;
        this.vpnWorker = null;
        if (worker != null) {
            worker.stop();
        }
    }

    private void updateVpnStatus(VpnStatus status) {
        Timber.d("updateVpnStatus called with status: %s", status);
        PreferenceHelper.setVpnServiceStatus(this, status);
        Notification notification = getNotification(status);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        switch (status) {
            case STARTING:
            case RUNNING:
                notificationManager.cancel(VPN_RESUME_SERVICE_NOTIFICATION_ID);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Timber.d("Calling startForeground with VPN special-use type");
                    try {
                        startForeground(VPN_RUNNING_SERVICE_NOTIFICATION_ID, notification,
                                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                        Timber.d("startForeground success");
                    } catch (Exception e) {
                        Timber.d(e, "startForeground FAILED");
                    }
                } else {
                    startForeground(VPN_RUNNING_SERVICE_NOTIFICATION_ID, notification);
                }
                break;
            default:
                if (checkSelfPermission(POST_NOTIFICATIONS) == PERMISSION_GRANTED) {
                    notificationManager.notify(VPN_RESUME_SERVICE_NOTIFICATION_ID, notification);
                }
        }

        // Persisted status is the UI source of truth. RUNNING is emitted by VpnWorker
        // only after VpnBuilder.establish() succeeds.
    }

    private Notification getNotification(VpnStatus status) {
        String title = getString(R.string.vpn_notification_title, getString(status.getTextResource()));

        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, VPN_SERVICE_NOTIFICATION_CHANNEL)
                .setPriority(IMPORTANCE_LOW)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.logo)
                .setColorized(true)
                .setColor(getColor(R.color.notification))
                .setContentTitle(title);
        switch (status) {
            case RUNNING:
                Intent stopIntent = new Intent(this, CommandReceiver.class)
                        .setAction(SEND_COMMAND_ACTION);
                STOP.appendToIntent(stopIntent);
                PendingIntent stopActionIntent = PendingIntent.getBroadcast(this, REQUEST_CODE_PAUSE, stopIntent,
                        FLAG_IMMUTABLE);
                builder.addAction(
                        R.drawable.ic_pause_24dp,
                        getString(R.string.vpn_notification_action_pause),
                        stopActionIntent).setOngoing(true);
                break;
            case STOPPED:
                Intent startIntent = new Intent(this, CommandReceiver.class)
                        .setAction(SEND_COMMAND_ACTION);
                START.appendToIntent(startIntent);
                PendingIntent startActionIntent = PendingIntent.getBroadcast(this, REQUEST_CODE_START, startIntent,
                        FLAG_IMMUTABLE);
                builder.addAction(
                        0,
                        getString(R.string.vpn_notification_action_resume),
                        startActionIntent);
                break;
        }
        return builder.build();
    }

    private void registerNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkRequest wifiNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .build();
        NetworkRequest cellularNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .build();
        initializeNetworkTypes(connectivityManager);
        connectivityManager.registerNetworkCallback(wifiNetworkRequest, this.wifiNetworkCallback, this.handler);
        connectivityManager.registerNetworkCallback(cellularNetworkRequest, this.cellularNetworkCallback, this.handler);

    }

    private void unregisterNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        connectivityManager.unregisterNetworkCallback(this.wifiNetworkCallback);
        connectivityManager.unregisterNetworkCallback(this.cellularNetworkCallback);
    }

    private void initializeNetworkTypes(ConnectivityManager connectivityManager) {
        this.availableNetworkTypes.clear();
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork != null) {
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            if (networkCapabilities != null) {
                if (networkCapabilities.hasTransport(TRANSPORT_WIFI)) {
                    this.availableNetworkTypes.add(WIFI);
                }
                if (networkCapabilities.hasTransport(TRANSPORT_CELLULAR)) {
                    this.availableNetworkTypes.add(CELLULAR);
                }
            }
        }
        Timber.d("Initial network types: %s ", this.availableNetworkTypes);
    }

    private void addNetworkType(NetworkType type) {
        boolean noNetwork = this.availableNetworkTypes.isEmpty();
        this.availableNetworkTypes.add(type);
        if (noNetwork && this.vpnRequested) {
            Timber.d("Reconnecting VPN on network %s.", type);
            reconnect();
        }
    }

    private void removeNetworkType(NetworkType type) {
        this.availableNetworkTypes.remove(type);
        if (!this.vpnRequested) {
            return;
        }
        if (this.availableNetworkTypes.isEmpty()) {
            Timber.d("Waiting for network…");
            waitForNetVpn();
        } else {
            reconnect();
        }
    }

    /**
     * This class receives network change events to monitor network type available.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     * @see <a href=
     *      "https://developer.android.com/training/basics/network-ops/reading-network-state#listening-events">Android
     *      Developer Documentation</a>
     */
    private class NetworkTypeCallback extends NetworkCallback {
        private final NetworkType monitoredType;

        NetworkTypeCallback(NetworkType monitoredType) {
            this.monitoredType = monitoredType;
        }

        @Override
        public void onAvailable(@NonNull Network network) {
            Timber.d("On available %s", this.monitoredType);
            addNetworkType(this.monitoredType);
        }

        @Override
        public void onLost(@NonNull Network network) {
            Timber.d("On lost %s", this.monitoredType);
            removeNetworkType(this.monitoredType);
        }
    }

    enum NetworkType {
        CELLULAR,
        WIFI,
    }

    /* The handler may only keep a weak reference around, otherwise it leaks */
    private static class MyHandler extends Handler {

        private final WeakReference<Callback> callback;

        MyHandler(Callback callback) {
            super(requireNonNull(Looper.myLooper()));
            this.callback = new WeakReference<>(callback);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            Callback callback = this.callback.get();
            if (callback != null) {
                callback.handleMessage(msg);
            }
            super.handleMessage(msg);
        }
    }
}
