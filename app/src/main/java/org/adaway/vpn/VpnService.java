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

import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.EXTRA_NETWORK_TYPE;
import static android.net.ConnectivityManager.TYPE_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static org.adaway.broadcast.Command.START;
import static org.adaway.broadcast.Command.STOP;
import static org.adaway.broadcast.CommandReceiver.SEND_COMMAND_ACTION;
import static org.adaway.helper.NotificationHelper.VPN_RESUME_SERVICE_NOTIFICATION_ID;
import static org.adaway.helper.NotificationHelper.VPN_RUNNING_SERVICE_NOTIFICATION_ID;
import static org.adaway.helper.NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL;
import static org.adaway.vpn.VpnService.MyHandler.VPN_MSG_NETWORK_CHANGED;
import static org.adaway.vpn.VpnService.MyHandler.VPN_MSG_STATUS_UPDATE;
import static org.adaway.vpn.VpnStatus.RECONNECTING;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STARTING;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.adaway.vpn.VpnStatus.WAITING_FOR_NETWORK;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

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
import org.adaway.vpn.VpnWorker.VpnStatusNotifier;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Objects;

public class VpnService extends android.net.VpnService implements Handler.Callback {
    public static final int REQUEST_CODE_START = 43;
    public static final int REQUEST_CODE_PAUSE = 42;
    public static final String VPN_UPDATE_STATUS_INTENT = "org.jak_linux.dns66.VPN_UPDATE_STATUS";
    public static final String VPN_UPDATE_STATUS_EXTRA = "VPN_STATUS";
    private static final String TAG = "VpnService";

    private final Handler handler;
    private final BroadcastReceiver connectivityChangedReceiver;
    private final VpnWorker vpnWorker;

    /**
     * Constructor.
     */
    public VpnService() {
        this.handler = new MyHandler(this);
        this.connectivityChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Message networkMessage = handler.obtainMessage(VPN_MSG_NETWORK_CHANGED, intent);
                handler.sendMessage(networkMessage);
            }
        };
        VpnStatusNotifier statusNotifier = status -> {
            Message statusMessage = this.handler.obtainMessage(VPN_MSG_STATUS_UPDATE, status.toCode(), 0);
            this.handler.sendMessage(statusMessage);
        };
        this.vpnWorker = new VpnWorker(this, statusNotifier);
    }

    /**
     * Check if the VPN service is started.
     *
     * @param context The application context.
     * @return {@code true} if the VPN service is started, {@code false} otherwise.
     */
    public static boolean isStarted(Context context) {
        boolean networkVpnCapability = checkAnyNetworkVpnCapability(context);
        VpnStatus status = PreferenceHelper.getVpnServiceStatus(context);
        if (status.isStarted() && !networkVpnCapability) {
            status = STOPPED;
            PreferenceHelper.setVpnServiceStatus(context, status);
        }
        return status.isStarted();
    }

    /**
     * Start the VPN service.
     *
     * @param context The application context.
     * @return {@code true} if the service is started, {@code false} otherwise.
     */
    public static boolean start(Context context) {
        // Check if VPN is already running
        if (isStarted(context)) {
            return true;
        }
        // Start the VPN service
        Intent intent = new Intent(context, VpnService.class);
        START.appendToIntent(intent);
        return context.startForegroundService(intent) != null;
    }

    /**
     * Stop the VPN service.
     *
     * @param context The application context.
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, VpnService.class);
        STOP.appendToIntent(intent);
        context.startService(intent);
    }

    private static boolean checkAnyNetworkVpnCapability(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        return Arrays.stream(connectivityManager.getAllNetworks())
                .map(connectivityManager::getNetworkCapabilities)
                .filter(Objects::nonNull)
                .anyMatch(networkCapabilities -> networkCapabilities.hasTransport(TRANSPORT_VPN));
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand" + intent);
        switch (Command.readFromIntent(intent)) {
            case START:
                startVpn();
                break;
            case STOP:
                stopVpn();
                break;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Destroyed, shutting down");
        stopVpn();
    }

    public boolean handleMessage(Message message) {
        if (message == null) {
            return true;
        }

        switch (message.what) {
            case VPN_MSG_STATUS_UPDATE:
                updateVpnStatus(VpnStatus.fromCode(message.arg1));
                break;
            case VPN_MSG_NETWORK_CHANGED:
                connectivityChanged((Intent) message.obj);
                break;
            default:
                throw new IllegalArgumentException("Invalid message with what = " + message.what);
        }
        return true;
    }

    private void startVpn() {
        PreferenceHelper.setVpnServiceStatus(this, RUNNING);
        updateVpnStatus(STARTING);
        registerReceiver(this.connectivityChangedReceiver, new IntentFilter(CONNECTIVITY_ACTION));
        restartWorker();
    }

    private void stopVpn() {
        Log.i(TAG, "Stopping Service");
        PreferenceHelper.setVpnServiceStatus(this, STOPPED);
//        if (vpnWorker != null)
        stopVpnWorker();
//        vpnWorker = null;
        try {
            unregisterReceiver(this.connectivityChangedReceiver);
        } catch (IllegalArgumentException e) {
            Log.i(TAG, "Ignoring exception on unregistering receiver");
        }
        updateVpnStatus(STOPPED);
        stopSelf();
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
                notificationManager.notify(VPN_RESUME_SERVICE_NOTIFICATION_ID, notification);
        }

        Intent intent = new Intent(VPN_UPDATE_STATUS_INTENT);
        intent.putExtra(VPN_UPDATE_STATUS_EXTRA, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private Notification getNotification(VpnStatus status) {
        String title = getString(R.string.vpn_notification_title, getString(status.getTextResource()));

        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
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
                );
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

    private void restartWorker() {
        this.vpnWorker.stop();
        this.vpnWorker.start();
    }

    private void stopVpnWorker() {
        this.vpnWorker.stop();
    }

    private void waitForNetVpn() {
        stopVpnWorker();
        updateVpnStatus(WAITING_FOR_NETWORK);
    }

    private void reconnect() {
        updateVpnStatus(RECONNECTING);
        restartWorker();
    }

    private void connectivityChanged(Intent intent) {
        if (intent.getIntExtra(EXTRA_NETWORK_TYPE, 0) == TYPE_VPN) {
            Log.i(TAG, "Ignoring connectivity changed for our own network");
            return;
        }
        if (!CONNECTIVITY_ACTION.equals(intent.getAction())) {
            Log.e(TAG, "Got bad intent on connectivity changed " + intent.getAction());
        }
        if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
            Log.i(TAG, "Connectivity changed to no connectivity, wait for a network");
            waitForNetVpn();
        } else {
            Log.i(TAG, "Network changed, try to reconnect");
            reconnect();
        }
    }

    /* The handler may only keep a weak reference around, otherwise it leaks */
    static class MyHandler extends Handler {
        static final int VPN_MSG_STATUS_UPDATE = 0;
        static final int VPN_MSG_NETWORK_CHANGED = 1;

        private final WeakReference<Callback> callback;

        MyHandler(Callback callback) {
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
