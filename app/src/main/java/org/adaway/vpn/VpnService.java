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
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
    public static final String VPN_UPDATE_STATUS_INTENT = "org.jak_linux.dns66.VPN_UPDATE_STATUS";
    public static final String VPN_UPDATE_STATUS_EXTRA = "VPN_STATUS";
    /*
     * Notification intent related.
     */
    private static final int REQUEST_CODE_START = 43;
    private static final int REQUEST_CODE_PAUSE = 42;
    /*
     * Handler related.
     */
    private static final int VPN_STATUS_UPDATE_MESSAGE_TYPE = 0;

    private final MyHandler handler;
    private final NetworkTypeCallback wifiNetworkCallback;
    private final NetworkTypeCallback cellularNetworkCallback;
    private final Set<NetworkType> availableNetworkTypes;
    private final VpnWorker vpnWorker;

    /**
     * Constructor.
     */
    public VpnService() {
        this.handler = new MyHandler(this);
        this.wifiNetworkCallback = new NetworkTypeCallback(WIFI);
        this.cellularNetworkCallback = new NetworkTypeCallback(CELLULAR);
        this.availableNetworkTypes = new HashSet<>();
        this.vpnWorker = new VpnWorker(this);
    }

    /*
     * VPN Service.
     */

    @Override
    public void onCreate() {
        Timber.d("Creating VPN service…");
        registerNetworkCallback();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Timber.d("onStartCommand %s", intent == null ? "null intent" : intent);
        // Check null intent that happens when system restart the service
        // https://developer.android.com/reference/android/app/Service#START_STICKY
        Command command = intent == null ?
                START :
                Command.readFromIntent(intent);
        switch (command) {
            case START:
                startVpn();
                return START_STICKY;
            case STOP:
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
        unregisterNetworkCallback();
        Timber.d("Destroyed VPN service.");
    }

    /*
     * Handler callback.
     */

    @Override
    public boolean handleMessage(@NonNull Message message) {
        if (message.what == VPN_STATUS_UPDATE_MESSAGE_TYPE) {
            updateVpnStatus(VpnStatus.fromCode(message.arg1));
        }
        return true;
    }

    /**
     * Notify a of the new VPN status.
     *
     * @param status The new VPN status.
     */
    public void notifyVpnStatus(VpnStatus status) {
        Message statusMessage = this.handler.obtainMessage(VPN_STATUS_UPDATE_MESSAGE_TYPE, status.toCode(), 0);
        this.handler.sendMessage(statusMessage);
    }

    private void startVpn() {
        Timber.d("Starting VPN service…");
        PreferenceHelper.setVpnServiceStatus(this, RUNNING);
        updateVpnStatus(STARTING);
        this.vpnWorker.start();
        Timber.i("VPN service started.");
    }

    private void stopVpn() {
        Timber.d("Stopping VPN service…");
        PreferenceHelper.setVpnServiceStatus(this, STOPPED);
        this.vpnWorker.stop();
        stopForeground(true);
        stopSelf();
        updateVpnStatus(STOPPED);
        Timber.i("VPN service stopped.");
    }

    private void waitForNetVpn() {
        this.vpnWorker.stop();
        updateVpnStatus(WAITING_FOR_NETWORK);
    }

    private void reconnect() {
        updateVpnStatus(RECONNECTING);
        this.vpnWorker.start();
    }

    private void updateVpnStatus(VpnStatus status) {
        Notification notification = getNotification(status);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        switch (status) {
            case STARTING:
            case RUNNING:
                notificationManager.cancel(VPN_RESUME_SERVICE_NOTIFICATION_ID);
                startForeground(VPN_RUNNING_SERVICE_NOTIFICATION_ID, notification);
                break;
            default:
                if (checkSelfPermission(POST_NOTIFICATIONS) == PERMISSION_GRANTED) {
                    notificationManager.notify(VPN_RESUME_SERVICE_NOTIFICATION_ID, notification);
                }
        }

        // TODO BUG - Nobody is listening to this intent
        // TODO BUG - VpnModel can lister to it to update the MainActivity according its current state
        Intent intent = new Intent(VPN_UPDATE_STATUS_INTENT);
        intent.putExtra(VPN_UPDATE_STATUS_EXTRA, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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
                PendingIntent stopActionIntent = PendingIntent.getBroadcast(this, REQUEST_CODE_PAUSE, stopIntent, FLAG_IMMUTABLE);
                builder.addAction(
                        R.drawable.ic_pause_24dp,
                        getString(R.string.vpn_notification_action_pause),
                        stopActionIntent
                ).setOngoing(true);
                break;
            case STOPPED:
                Intent startIntent = new Intent(this, CommandReceiver.class)
                        .setAction(SEND_COMMAND_ACTION);
                START.appendToIntent(startIntent);
                PendingIntent startActionIntent = PendingIntent.getBroadcast(this, REQUEST_CODE_START, startIntent, FLAG_IMMUTABLE);
                builder.addAction(
                        0,
                        getString(R.string.vpn_notification_action_resume),
                        startActionIntent
                );
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
        if (noNetwork) {
            Timber.d("Reconnecting VPN on network %s.", type);
            reconnect();
        }
    }

    private void removeNetworkType(NetworkType type) {
        this.availableNetworkTypes.remove(type);
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
     * @see <a href="https://developer.android.com/training/basics/network-ops/reading-network-state#listening-events">Android Developer Documentation</a>
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
