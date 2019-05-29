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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.ui.MainActivity;

import java.lang.ref.WeakReference;

import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static org.adaway.helper.NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL;
import static org.adaway.helper.NotificationHelper.VPN_SERVICE_NOTIFICATION_ID;
import static org.adaway.vpn.VpnCommand.PAUSE;
import static org.adaway.vpn.VpnCommand.RESUME;
import static org.adaway.vpn.VpnCommand.START;
import static org.adaway.vpn.VpnCommand.STOP;
import static org.adaway.vpn.VpnStatus.PAUSED;
import static org.adaway.vpn.VpnStatus.RECONNECTING;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STARTING;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.adaway.vpn.VpnStatus.WAITING_FOR_NETWORK;

public class VpnService extends android.net.VpnService implements Handler.Callback {
    public static final int REQUEST_CODE_START = 43;
    public static final int REQUEST_CODE_PAUSE = 42;
    public static final String INTENT_EXTRA_COMMAND = "COMMAND";
    public static final String VPN_UPDATE_STATUS_INTENT = "org.jak_linux.dns66.VPN_UPDATE_STATUS";
    public static final String VPN_UPDATE_STATUS_EXTRA = "VPN_STATUS";
    private static final int VPN_MSG_STATUS_UPDATE = 0;
    private static final int VPN_MSG_NETWORK_CHANGED = 1;
    private static final String TAG = "VpnService";

    // TODO: Temporary Hack til refactor is done
    public static VpnStatus vpnStatus = STOPPED;


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
                handler.sendMessage(handler.obtainMessage(VPN_MSG_NETWORK_CHANGED, intent));
            }
        };
        this.vpnWorker = new VpnWorker(
                this,
                status -> this.handler.sendMessage(this.handler.obtainMessage(VPN_MSG_STATUS_UPDATE, status.toCode(), 0))
        );
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
        if (status.isStarted() && !networkVpnCapability && !status.isPaused()) {
            status = STOPPED;
            PreferenceHelper.setVpnServiceStatus(context, status);
        } else if (status.isPaused() && networkVpnCapability) {
            status = RUNNING;
            PreferenceHelper.setVpnServiceStatus(context, status);
        }
        return status.isStarted();
    }

    /**
     * Start the VPN service.
     *
     * @param context The application context.
     */
    public static void start(Context context) {
        // Check if VPN is already running
        VpnStatus status = PreferenceHelper.getVpnServiceStatus(context);
        if (status.isStarted() && !status.isPaused()) {
            return;
        }
        // Start the VPN service
        Intent intent = getStartIntent(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // TODO && config.showNotification
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Stop the VPN service.
     *
     * @param context The application context.
     */
    public static void stop(Context context) {
        context.startService(getStopIntent(context));
    }

    private static boolean checkAnyNetworkVpnCapability(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        Network[] networks = connectivityManager.getAllNetworks();
        for (Network network : networks) {
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
            if (networkCapabilities != null && networkCapabilities.hasTransport(TRANSPORT_VPN)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static Intent getStartIntent(Context context) {
        Intent intent = new Intent(context, VpnService.class);
        intent.putExtra(INTENT_EXTRA_COMMAND, START.toExtra());

        return intent;
    }

//    @NonNull
//    private static Intent getResumeIntent(Context context) {
//        Intent intent = new Intent(context, VpnService.class);
//        intent.putExtra(INTENT_EXTRA_COMMAND, RESUME.toExtra());
//        return intent;
//    }

    @NonNull
    private static Intent getStopIntent(Context context) {
        Intent intent = new Intent(context, VpnService.class);
        intent.putExtra(INTENT_EXTRA_COMMAND, STOP.toExtra());
        return intent;
    }


    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand" + intent);
        // Get command
        VpnCommand command = START;
        if (intent != null && intent.hasExtra(INTENT_EXTRA_COMMAND)) {
            command = VpnCommand.fromExtra(intent.getIntExtra(INTENT_EXTRA_COMMAND, command.toExtra()));
        }
        // Apply command
        switch (command) {
            case RESUME:
                PreferenceHelper.setVpnServiceStatus(this, RUNNING);
                resumeVpn();
                break;
            case START:
                PreferenceHelper.setVpnServiceStatus(this, RUNNING);
                startVpn();
                break;
            case STOP:
                PreferenceHelper.setVpnServiceStatus(this, STOPPED);
                stopVpn();
                break;
            case PAUSE:
                PreferenceHelper.setVpnServiceStatus(this, PAUSED);
                pauseVpn();
                break;
        }
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Destroyed, shutting down");
        stopVpn();
    }

    @Override
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
        updateVpnStatus(STARTING);
        registerReceiver(connectivityChangedReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        restartWorker();
    }

    private void stopVpn() {
        Log.i(TAG, "Stopping Service");
//        if (vpnWorker != null)
        stopVpnWorker();
//        vpnWorker = null;
        try {
            unregisterReceiver(connectivityChangedReceiver);
        } catch (IllegalArgumentException e) {
            Log.i(TAG, "Ignoring exception on unregistering receiver");
        }
        updateVpnStatus(STOPPED);
        stopSelf();
    }

    private void resumeVpn() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(VPN_SERVICE_NOTIFICATION_ID);
        }
        startVpn();
    }

    private void pauseVpn() {
        stopVpn();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(VPN_SERVICE_NOTIFICATION_ID, this.getNotification(STOPPED));
        }
    }

    private void updateVpnStatus(VpnStatus status) {
        vpnStatus = status;


//        if (FileHelper.loadCurrentSettings(getApplicationContext()).showNotification) { // TODO
        startForeground(VPN_SERVICE_NOTIFICATION_ID, this.getNotification(status));
//        }

        Intent intent = new Intent(VPN_UPDATE_STATUS_INTENT);
        intent.putExtra(VPN_UPDATE_STATUS_EXTRA, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private Notification getNotification(VpnStatus status) {
        String title = getString(R.string.vpn_notification_title, getString(status.getTextResource()));
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, VPN_SERVICE_NOTIFICATION_CHANNEL)
                .setPriority(IMPORTANCE_LOW)
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0,
                        new Intent(getApplicationContext(), MainActivity.class), 0))
                .setSmallIcon(R.drawable.icon)
                .setColorized(true)
                .setColor(this.getColor(R.color.notification))
                .setContentTitle(title);
        switch (status) {
            case RUNNING:
                builder.addAction(
                        R.drawable.ic_pause_black_24dp,
                        getString(R.string.vpn_notification_action_pause),
                        PendingIntent.getService(
                                this,
                                REQUEST_CODE_PAUSE,
                                new Intent(this, VpnService.class)
                                        .putExtra(INTENT_EXTRA_COMMAND, PAUSE.toExtra()),
                                0
                        )
                );
                break;
            case STOPPED:
                builder.setContentText(this.getString(R.string.vpn_notification_paused_text));
                builder.setContentIntent(
                        PendingIntent.getService(
                                this,
                                REQUEST_CODE_START,
                                new Intent(this, VpnService.class)
                                        .putExtra(INTENT_EXTRA_COMMAND, RESUME.toExtra()),
                                0
                        )
                );
                break;
        }
        return builder.build();
    }

    private void restartWorker() {
        vpnWorker.stop();
        vpnWorker.start();
    }

    private void stopVpnWorker() {
        vpnWorker.stop();
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
        if (intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, 0) == ConnectivityManager.TYPE_VPN) {
            Log.i(TAG, "Ignoring connectivity changed for our own network");
            return;
        }

        if (!ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
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
    private static class MyHandler extends Handler {
        private final WeakReference<Callback> callback;

        MyHandler(Callback callback) {
            this.callback = new WeakReference<>(callback);
        }

        @Override
        public void handleMessage(Message msg) {
            Callback callback = this.callback.get();
            if (callback != null) {
                callback.handleMessage(msg);
            }
            super.handleMessage(msg);
        }
    }
}
