package org.adaway.helper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.adaway.R
import org.adaway.ui.home.HomeActivity
import org.adaway.ui.navigation.AdAwayRoute
import org.adaway.ui.navigation.NavigationRequest

object NotificationHelper {
    const val UPDATE_NOTIFICATION_CHANNEL = "UpdateChannel"
    const val VPN_SERVICE_NOTIFICATION_CHANNEL = "VpnServiceChannel"
    
    private const val UPDATE_HOSTS_NOTIFICATION_ID = 10
    private const val UPDATE_APP_NOTIFICATION_ID = 11
    private const val UPDATE_HOSTS_PROGRESS_NOTIFICATION_ID = 16
    private const val UPDATE_APP_PROGRESS_NOTIFICATION_ID = 17
    
    @JvmField
    val VPN_RUNNING_SERVICE_NOTIFICATION_ID = 20
    
    @JvmField
    val VPN_RESUME_SERVICE_NOTIFICATION_ID = 21

    @JvmStatic
    fun createNotificationChannels(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

        // Create update notification channel
        val updateChannel = NotificationChannel(
            UPDATE_NOTIFICATION_CHANNEL,
            context.getString(R.string.notification_update_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_update_channel_description)
        }

        // Create VPN service notification channel
        val vpnServiceChannel = NotificationChannel(
            VPN_SERVICE_NOTIFICATION_CHANNEL,
            context.getString(R.string.notification_vpn_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_vpn_channel_description)
        }

        notificationManager.createNotificationChannel(updateChannel)
        notificationManager.createNotificationChannel(vpnServiceChannel)
    }

    @JvmStatic
    fun showUpdateHostsNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager == null || !notificationManager.areNotificationsEnabled()) {
            return
        }

        val color = context.getColor(R.color.notification)
        val title = context.getString(R.string.notification_update_host_available_title)
        val text = context.getString(R.string.notification_update_host_available_text)
        
        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.logo)
            .setColorized(true)
            .setColor(color)
            .setShowWhen(false)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        notificationManager.notify(UPDATE_HOSTS_NOTIFICATION_ID, builder.build())
    }

    @JvmStatic
    fun showUpdateHostsProgressNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager == null || !notificationManager.areNotificationsEnabled()) {
            return
        }

        val builder = buildUpdateProgressNotification(
            context = context,
            notificationManager = notificationManager,
            title = context.getString(R.string.notification_update_host_progress_title),
            text = context.getString(R.string.notification_update_host_progress_text),
            progress = null,
            route = AdAwayRoute.HOSTS
        )

        notificationManager.notify(UPDATE_HOSTS_PROGRESS_NOTIFICATION_ID, builder.build())
    }

    @JvmStatic
    fun clearUpdateHostsProgressNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        notificationManager.cancel(UPDATE_HOSTS_PROGRESS_NOTIFICATION_ID)
    }

    @JvmStatic
    fun showUpdateApplicationNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager == null || !notificationManager.areNotificationsEnabled()) {
            return
        }

        val color = context.getColor(R.color.notification)
        val title = context.getString(R.string.notification_update_app_available_title)
        val text = context.getString(R.string.notification_update_app_available_text)
        
        val intent = Intent(context, HomeActivity::class.java).apply {
            putExtra(NavigationRequest.EXTRA_ROUTE, AdAwayRoute.UPDATE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.logo)
            .setColorized(true)
            .setColor(color)
            .setShowWhen(false)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        notificationManager.notify(UPDATE_APP_NOTIFICATION_ID, builder.build())
    }

    @JvmStatic
    fun showUpdateApplicationProgressNotification(context: Context, progress: Int, text: String) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager == null || !notificationManager.areNotificationsEnabled()) {
            return
        }

        val builder = buildUpdateProgressNotification(
            context = context,
            notificationManager = notificationManager,
            title = context.getString(R.string.notification_update_app_progress_title),
            text = text,
            progress = progress.coerceIn(0, 100),
            route = AdAwayRoute.UPDATE
        )

        notificationManager.notify(UPDATE_APP_PROGRESS_NOTIFICATION_ID, builder.build())
    }

    @JvmStatic
    fun showUpdateApplicationProgressNotification(context: Context) {
        showUpdateApplicationProgressNotification(
            context,
            0,
            context.getString(R.string.update_notification_description)
        )
    }

    @JvmStatic
    fun clearUpdateApplicationProgressNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        notificationManager.cancel(UPDATE_APP_PROGRESS_NOTIFICATION_ID)
    }

    @JvmStatic
    fun clearUpdateNotifications(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        notificationManager.cancel(UPDATE_HOSTS_NOTIFICATION_ID)
        notificationManager.cancel(UPDATE_APP_NOTIFICATION_ID)
        notificationManager.cancel(UPDATE_HOSTS_PROGRESS_NOTIFICATION_ID)
        notificationManager.cancel(UPDATE_APP_PROGRESS_NOTIFICATION_ID)
    }

    private fun buildUpdateProgressNotification(
        context: Context,
        notificationManager: NotificationManager,
        title: String,
        text: String,
        progress: Int?,
        route: String
    ): NotificationCompat.Builder {
        val intent = Intent(context, HomeActivity::class.java).apply {
            putExtra(NavigationRequest.EXTRA_ROUTE, route)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.logo)
            .setColor(context.getColor(R.color.notification))
            .setColorized(false)
            .setShowWhen(false)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, progress ?: 0, progress == null)
            .apply {
                requestPromotedOngoing(notificationManager)
            }
    }

    private fun NotificationCompat.Builder.requestPromotedOngoing(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= 36 && notificationManager.canPostPromotedNotifications()) {
            extras.putBoolean("android.requestPromotedOngoing", true)
        }
    }
}
