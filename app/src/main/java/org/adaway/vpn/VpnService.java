/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
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

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
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

import static org.adaway.helper.NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL;
import static org.adaway.helper.NotificationHelper.VPN_SERVICE_NOTIFICATION_ID;
import static org.adaway.vpn.VpnCommand.PAUSE;
import static org.adaway.vpn.VpnCommand.RESUME;
import static org.adaway.vpn.VpnCommand.START;
import static org.adaway.vpn.VpnCommand.STOP;

public class VpnService extends android.net.VpnService implements Handler.Callback {
    /**
     * The activity request/result code for starting VPN service.
     */
    public static final int VPN_START_REQUEST_CODE = 10;

    public static final int REQUEST_CODE_START = 43;
    public static final int REQUEST_CODE_PAUSE = 42;
    public static final String INTENT_EXTRA_COMMAND = "COMMAND";
    public static final int VPN_STATUS_STARTING = 0;
    public static final int VPN_STATUS_RUNNING = 1;
    public static final int VPN_STATUS_STOPPING = 2;
    public static final int VPN_STATUS_WAITING_FOR_NETWORK = 3;
    public static final int VPN_STATUS_RECONNECTING = 4;
    public static final int VPN_STATUS_RECONNECTING_NETWORK_ERROR = 5;
    public static final int VPN_STATUS_STOPPED = 6;
    public static final String VPN_UPDATE_STATUS_INTENT = "org.jak_linux.dns66.VPN_UPDATE_STATUS";
    public static final String VPN_UPDATE_STATUS_EXTRA = "VPN_STATUS";
    private static final int VPN_MSG_STATUS_UPDATE = 0;
    private static final int VPN_MSG_NETWORK_CHANGED = 1;
    private static final String TAG = "VpnService";


    // TODO: Temporary Hack til refactor is done
    public static int vpnStatus = VPN_STATUS_STOPPED;


    private final Handler handler;
    private final BroadcastReceiver connectivityChangedReceiver;
    private final NotificationCompat.Builder notificationBuilder;
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
        this.notificationBuilder = new NotificationCompat.Builder(this, VPN_SERVICE_NOTIFICATION_CHANNEL)
//            .setSmallIcon(R.drawable.ic_state_deny) // TODO: Notification icon
                .setPriority(Notification.PRIORITY_MIN);
        VpnWorker.Notify notify = value -> handler.sendMessage(handler.obtainMessage(VPN_MSG_STATUS_UPDATE, value, 0));
        this.vpnWorker = new VpnWorker(this, notify);
    }

    public static int vpnStatusToTextId(int status) {
        switch (status) {
            case VPN_STATUS_STARTING:
                return R.string.notification_starting;
            case VPN_STATUS_RUNNING:
                return R.string.notification_running;
            case VPN_STATUS_STOPPING:
                return R.string.notification_stopping;
            case VPN_STATUS_WAITING_FOR_NETWORK:
                return R.string.notification_waiting_for_net;
            case VPN_STATUS_RECONNECTING:
                return R.string.notification_reconnecting;
            case VPN_STATUS_RECONNECTING_NETWORK_ERROR:
                return R.string.notification_reconnecting_error;
            case VPN_STATUS_STOPPED:
                return R.string.notification_stopped;
            default:
                throw new IllegalArgumentException("Invalid vpnStatus value (" + status + ")");
        }
    }

    /**
     * Start the VPN service.
     *
     * @param context The application context (or activity if available).
     */
    public static void start(Context context) {
        // Check if VPN is already started
        if (PreferenceHelper.getVpnServiceEnabled(context)) {
            return;
        }
        // Check user authorization
        Intent prepareIntent = android.net.VpnService.prepare(context);
        if (prepareIntent != null) {
            if (context instanceof Activity) {
                ((Activity) context).startActivityForResult(prepareIntent, VPN_START_REQUEST_CODE);
            }
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
     * @param activity The application activity.
     */
    public static void stop(Activity activity) {
        activity.startService(getStopIntent(activity));
    }

    @NonNull
    private static Intent getStartIntent(Context context) {
        Intent intent = new Intent(context, VpnService.class);
        intent.putExtra(INTENT_EXTRA_COMMAND, START.toExtra());
        intent.putExtra("NOTIFICATION_INTENT",
                PendingIntent.getActivity(context, 0,
                        new Intent(context, MainActivity.class), 0));
        return intent;
    }

    @NonNull
    private static Intent getResumeIntent(Context context) {
        Intent intent = new Intent(context, VpnService.class);
        intent.putExtra(INTENT_EXTRA_COMMAND, RESUME.toExtra());
        intent.putExtra("NOTIFICATION_INTENT",
                PendingIntent.getActivity(context, 0,
                        new Intent(context, MainActivity.class), 0));
        return intent;
    }

    @NonNull
    private static Intent getStopIntent(Context context) {
        Intent intent = new Intent(context, VpnService.class);
        intent.putExtra(INTENT_EXTRA_COMMAND, STOP.toExtra());
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // NotificationChannels.onCreate(this); TODO Useful?
        notificationBuilder.addAction(
                R.drawable.ic_pause_black_24dp,
                getString(R.string.notification_action_pause),
                PendingIntent.getService(
                        this,
                        REQUEST_CODE_PAUSE,
                        new Intent(this, VpnService.class)
                                .putExtra(INTENT_EXTRA_COMMAND, PAUSE.toExtra()),
                        0
                )
        );
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
//        android.os.Debug.waitForDebugger(); // TODO DEBUG
        Log.i(TAG, "onStartCommand" + intent);
        // Get command
        VpnCommand command = START;
        if (intent != null && intent.hasExtra(INTENT_EXTRA_COMMAND)) {
            command = VpnCommand.fromExtra(intent.getIntExtra(INTENT_EXTRA_COMMAND, command.toExtra()));
        }
        // Apply command
        switch (command) {
            case RESUME:
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancelAll();
                // fallthrough
            case START:
                PreferenceHelper.setVpnServiceEnabled(this, true);
                startVpn(intent == null ? null : (PendingIntent) intent.getParcelableExtra("NOTIFICATION_INTENT"));
                break;
            case STOP:
                PreferenceHelper.setVpnServiceEnabled(this, false);
                stopVpn();
                break;
            case PAUSE:
                pauseVpn();
                break;
        }
        return Service.START_STICKY;
    }

    private void pauseVpn() {
        stopVpn();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(VPN_SERVICE_NOTIFICATION_ID, new NotificationCompat.Builder(this, VPN_SERVICE_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_state_deny) // TODO: Notification icon
                .setPriority(Notification.PRIORITY_LOW)
                .setContentTitle(getString(R.string.notification_paused_title))
                .setContentText(getString(R.string.notification_paused_text))
                .setContentIntent(PendingIntent.getService(this, REQUEST_CODE_START, getResumeIntent(this), PendingIntent.FLAG_ONE_SHOT))
                .build());
    }

    private void updateVpnStatus(int status) {
        vpnStatus = status;
        int notificationTextId = vpnStatusToTextId(status);
        notificationBuilder.setContentText(getString(notificationTextId));

//        if (FileHelper.loadCurrentSettings(getApplicationContext()).showNotification) { // TODO
        startForeground(VPN_SERVICE_NOTIFICATION_ID, notificationBuilder.build());
//        }

        Intent intent = new Intent(VPN_UPDATE_STATUS_INTENT);
        intent.putExtra(VPN_UPDATE_STATUS_EXTRA, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void startVpn(PendingIntent notificationIntent) {
        notificationBuilder.setContentTitle(getString(R.string.notification_title));
        if (notificationIntent != null)
            notificationBuilder.setContentIntent(notificationIntent);
        updateVpnStatus(VPN_STATUS_STARTING);

        registerReceiver(connectivityChangedReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        restartWorker();
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
        updateVpnStatus(VPN_STATUS_WAITING_FOR_NETWORK);
    }

    private void reconnect() {
        updateVpnStatus(VPN_STATUS_RECONNECTING);
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
        updateVpnStatus(VPN_STATUS_STOPPED);
        stopSelf();
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
                updateVpnStatus(message.arg1);
                break;
            case VPN_MSG_NETWORK_CHANGED:
                connectivityChanged((Intent) message.obj);
                break;
            default:
                throw new IllegalArgumentException("Invalid message with what = " + message.what);
        }
        return true;
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

        public MyHandler(Callback callback) {
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
