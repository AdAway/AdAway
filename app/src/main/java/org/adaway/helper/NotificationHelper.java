package org.adaway.helper;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.adaway.R;
import org.adaway.ui.MainActivity;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

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
     * The update hosts notification identifier.
     */
    private static final int UPDATE_HOSTS_NOTIFICATION_ID = 10;

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
    public static void createNotificationChannel(@NonNull Context context) {
        // Create the NotificationChannel, but only on API 26+ because the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.notification_channel_update_name);
            String description = context.getString(R.string.notification_channel_update_description);
            // Create description channel
            NotificationChannel channel = new NotificationChannel(
                    UPDATE_NOTIFICATION_CHANNEL,
                    name,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
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
        NotificationManager notificationManager = (NotificationManager) context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        // Build notification
        String title = context.getString(R.string.status_update_available);
        String text = context.getString(R.string.status_update_available_subtitle);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationHelper.UPDATE_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.status_bar_icon)
                .setShowWhen(false)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true);
        // Set action on notification tap
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
        builder.setContentIntent(pendingIntent).setAutoCancel(true);
        // Set color if supported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setColorized(true).setColor(context.getColor(R.color.notification));
        }
        // Notify the built notification
        notificationManager.notify(UPDATE_HOSTS_NOTIFICATION_ID, builder.build());
    }

    /**
     * Hide the notification about new hosts update available.
     *
     * @param context The application context.
     */
    public static void clearUpdateHostsNotification(@NonNull Context context) {
        // Get notification manager
        NotificationManager notificationManager = (NotificationManager) context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        // Cancel the notification
        notificationManager.cancel(UPDATE_HOSTS_NOTIFICATION_ID);
    }
}
