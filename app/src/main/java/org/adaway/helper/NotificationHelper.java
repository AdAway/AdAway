package org.adaway.helper;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.adaway.R;
import org.adaway.ui.home.HomeActivity;

import static android.app.PendingIntent.getActivity;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static androidx.core.app.NotificationCompat.PRIORITY_LOW;

/**
 * This class is an helper class to deals with notifications.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class NotificationHelper {
    /**
     * The notification channel for updates.
     */
    public static final String UPDATE_NOTIFICATION_CHANNEL = "UpdateChannel";
    /**
     * The notification channel for VPN service.
     */
    public static final String VPN_SERVICE_NOTIFICATION_CHANNEL = "VpnServiceChannel";
    /**
     * The update hosts notification identifier.
     */
    private static final int UPDATE_HOSTS_NOTIFICATION_ID = 10;
    /**
     * The update application notification identifier.
     */
    private static final int UPDATE_APP_NOTIFICATION_ID = 11;
    /**
     * The VPN running service notification identifier.
     */
    public static final int VPN_RUNNING_SERVICE_NOTIFICATION_ID = 20;
    /**
     * The VPN resume service notification identifier.
     */
    public static final int VPN_RESUME_SERVICE_NOTIFICATION_ID = 21;

    /**
     * Private constructor.
     */
    private NotificationHelper() {

    }

    /**
     * Create the application notification channel.
     *
     * @param context The application context.
     */
    public static void createNotificationChannels(@NonNull Context context) {
        // Create the NotificationChannel, but only on API 26+ because the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create update notification channel
            NotificationChannel updateChannel = new NotificationChannel(
                    UPDATE_NOTIFICATION_CHANNEL,
                    context.getString(R.string.notification_update_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            updateChannel.setDescription(context.getString(R.string.notification_update_channel_description));
            // Create VPN service notification channel
            NotificationChannel vpnServiceChannel = new NotificationChannel(
                    VPN_SERVICE_NOTIFICATION_CHANNEL,
                    context.getString(R.string.notification_vpn_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            updateChannel.setDescription(context.getString(R.string.notification_vpn_channel_description));
            // Register the channels with the system; you can't change the importance or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(updateChannel);
                notificationManager.createNotificationChannel(vpnServiceChannel);
            }
        }
    }

    /**
     * Show the notification about new hosts update available.
     *
     * @param context The application context.
     */
    public static void showUpdateHostsNotification(@NonNull Context context) {
        // Get notification manager
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        // Build notification
        int color = context.getColor(R.color.notification);
        String title = context.getString(R.string.notification_update_host_available_title);
        String text = context.getString(R.string.notification_update_host_available_text);
        Intent intent = new Intent(context, HomeActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = getActivity(context, 0, intent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.logo)
                .setColorized(true)
                .setColor(color)
                .setShowWhen(false)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setPriority(PRIORITY_LOW)
                .setAutoCancel(true);
        // Notify the built notification
        notificationManager.notify(UPDATE_HOSTS_NOTIFICATION_ID, builder.build());
    }

    /**
     * Show the notification about new application update available.
     *
     * @param context The application context.
     */
    public static void showUpdateApplicationNotification(@NonNull Context context) {
        // Get notification manager
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        // Build notification
        int color = context.getColor(R.color.notification);
        String title = context.getString(R.string.notification_update_app_available_title);
        String text = context.getString(R.string.notification_update_app_available_text);
        Intent intent = new Intent(context, HomeActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = getActivity(context, 0, intent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.logo)
                .setColorized(true)
                .setColor(color)
                .setShowWhen(false)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setPriority(PRIORITY_LOW)
                .setAutoCancel(true);
        // Notify the built notification
        notificationManager.notify(UPDATE_HOSTS_NOTIFICATION_ID, builder.build());
    }

    /**
     * Hide the notification about new hosts update available.
     *
     * @param context The application context.
     */
    public static void clearUpdateNotifications(@NonNull Context context) {
        // Get notification manager
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        // Cancel the notification
        notificationManager.cancel(UPDATE_HOSTS_NOTIFICATION_ID);
        notificationManager.cancel(UPDATE_APP_NOTIFICATION_ID);
    }
}
