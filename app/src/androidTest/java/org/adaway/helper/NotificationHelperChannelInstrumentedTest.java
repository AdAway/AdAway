package org.adaway.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.vpn.VpnService;
import org.adaway.vpn.VpnServiceControls;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class NotificationHelperChannelInstrumentedTest {
    private static final String FILTERLISTS_UPGRADE_PROBE_CHANNEL =
            "FilterListsChannel.UpgradeProbe";

    private Context context;
    private NotificationManager notificationManager;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        this.notificationManager = this.context.getSystemService(NotificationManager.class);
        assertNotNull("NotificationManager must be available on device.", this.notificationManager);
        stopVpnBeforeChannelReset();
        deleteAppChannels();
    }

    @After
    public void tearDown() throws Exception {
        stopVpnBeforeChannelReset();
        deleteAppChannels();
        NotificationHelper.createNotificationChannels(this.context);
    }

    @Test
    public void createChannelsInstallsExpectedUserVisibleMetadata() {
        NotificationHelper.createNotificationChannels(this.context);

        assertChannel(
                NotificationHelper.UPDATE_NOTIFICATION_CHANNEL,
                R.string.notification_update_channel_name,
                R.string.notification_update_channel_description,
                NotificationManager.IMPORTANCE_LOW
        );
        assertChannel(
                NotificationHelper.FILTERLISTS_NOTIFICATION_CHANNEL,
                R.string.notification_filterlists_channel_name,
                R.string.notification_filterlists_channel_description,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        assertChannel(
                NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL,
                R.string.notification_vpn_channel_name,
                R.string.notification_vpn_channel_description,
                NotificationManager.IMPORTANCE_LOW
        );
    }

    @Test
    public void existingFilterListsStyleChannelDoesNotUpgradeFromLowToDefaultImportance() {
        createExistingChannel(
                FILTERLISTS_UPGRADE_PROBE_CHANNEL,
                NotificationManager.IMPORTANCE_LOW
        );

        NotificationChannel upgradedChannel = new NotificationChannel(
                FILTERLISTS_UPGRADE_PROBE_CHANNEL,
                this.context.getString(R.string.notification_filterlists_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        this.notificationManager.createNotificationChannel(upgradedChannel);

        assertChannelImportance(
                FILTERLISTS_UPGRADE_PROBE_CHANNEL,
                NotificationManager.IMPORTANCE_LOW
        );
    }

    @Test
    public void updateAlertsBuildDistinctActionableNotificationsWithoutPermissionMutation() {
        NotificationHelper.createNotificationChannels(this.context);

        Notification hosts = NotificationHelper.buildUpdateHostsNotification(this.context);
        Notification app = NotificationHelper.buildUpdateApplicationNotification(this.context);

        assertUpdateAlertNotification(
                hosts,
                R.string.notification_update_host_available_title,
                R.string.notification_update_host_available_text
        );
        assertUpdateAlertNotification(
                app,
                R.string.notification_update_app_available_title,
                R.string.notification_update_app_available_text
        );
        assertNotEquals(
                "Hosts and app update alerts must open distinct pending intents.",
                hosts.contentIntent,
                app.contentIntent
        );
    }

    @Test
    public void updateAlertPostingMatchesCurrentNotificationPermissionState() {
        NotificationHelper.createNotificationChannels(this.context);
        NotificationHelper.clearUpdateNotifications(this.context);
        waitForNoUpdateNotifications();

        boolean alertsEnabled = this.notificationManager.areNotificationsEnabled();

        NotificationHelper.showUpdateHostsNotification(this.context);
        NotificationHelper.showUpdateApplicationNotification(this.context);

        if (alertsEnabled) {
            waitForUpdateNotification(NotificationHelper.UPDATE_HOSTS_NOTIFICATION_ID);
            waitForUpdateNotification(NotificationHelper.UPDATE_APP_NOTIFICATION_ID);
        } else {
            waitForNoUpdateNotifications();
        }
    }

    @Test(timeout = 45_000)
    public void channelResetStopsPreparedForegroundVpnBeforeDeletion() throws Exception {
        assumeTrue("VPN consent is required for the foreground-channel cleanup regression.",
                android.net.VpnService.prepare(this.context) == null);
        AdBlockMethod originalMethod = PreferenceHelper.getAdBlockMethod(this.context);
        PreferenceHelper.setAbBlockMethod(this.context, AdBlockMethod.VPN);
        try {
            NotificationHelper.createNotificationChannels(this.context);
            assertTrue(VpnServiceControls.start(this.context));
            long deadline = SystemClock.elapsedRealtime() + 20_000;
            while (!VpnServiceControls.isTunnelEstablished(this.context)
                    && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100);
            }
            assertTrue("The regression requires an actual running VPN.",
                    VpnServiceControls.isTunnelEstablished(this.context));
            stopVpnBeforeChannelReset();
            deleteAppChannels();
            assertEquals(null, this.notificationManager.getNotificationChannel(
                    NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL));
        } finally {
            try {
                stopVpnBeforeChannelReset();
            } finally {
                PreferenceHelper.setAbBlockMethod(this.context, originalMethod);
            }
        }
    }

    private void assertUpdateAlertNotification(
            Notification notification,
            int expectedTitle,
            int expectedText) {
        assertEquals(NotificationHelper.UPDATE_NOTIFICATION_CHANNEL, notification.getChannelId());
        assertEquals(Notification.PRIORITY_LOW, notification.priority);
        assertEquals(this.context.getColor(R.color.notification), notification.color);
        assertEquals(
                this.context.getString(expectedTitle),
                notification.extras.getCharSequence(Notification.EXTRA_TITLE)
        );
        assertEquals(
                this.context.getString(expectedText),
                notification.extras.getCharSequence(Notification.EXTRA_TEXT)
        );
        assertNotNull("Update alert must have an actionable content intent.",
                notification.contentIntent);
        assertTrue("Update alert must clear after tap.",
                (notification.flags & Notification.FLAG_AUTO_CANCEL) != 0);
    }

    private void assertChannel(
            String channelId,
            int expectedName,
            int expectedDescription,
            int expectedImportance) {
        NotificationChannel channel = this.notificationManager.getNotificationChannel(channelId);
        assertNotNull("Missing installed notification channel: " + channelId, channel);
        assertEquals(this.context.getString(expectedName), channel.getName().toString());
        assertEquals(this.context.getString(expectedDescription), channel.getDescription());
        assertEquals(expectedImportance, channel.getImportance());
    }

    private void assertChannelImportance(String channelId, int expectedImportance) {
        NotificationChannel channel = this.notificationManager.getNotificationChannel(channelId);
        assertNotNull("Missing installed notification channel: " + channelId, channel);
        assertEquals(expectedImportance, channel.getImportance());
    }

    private void createExistingChannel(String channelId, int importance) {
        NotificationChannel channel = new NotificationChannel(
                channelId,
                "Existing " + channelId,
                importance
        );
        channel.setDescription("Existing description for " + channelId);
        this.notificationManager.createNotificationChannel(channel);
    }

    private void deleteAppChannels() {
        NotificationHelper.clearUpdateNotifications(this.context);
        this.notificationManager.deleteNotificationChannel(
                NotificationHelper.UPDATE_NOTIFICATION_CHANNEL);
        this.notificationManager.deleteNotificationChannel(
                NotificationHelper.FILTERLISTS_NOTIFICATION_CHANNEL);
        this.notificationManager.deleteNotificationChannel(
                NotificationHelper.VPN_SERVICE_NOTIFICATION_CHANNEL);
        this.notificationManager.deleteNotificationChannel(FILTERLISTS_UPGRADE_PROBE_CHANNEL);
    }

    private void stopVpnBeforeChannelReset() throws Exception {
        // Earlier receiver tests can start the real service when VPN consent is prepared.
        WorkManager.getInstance(this.context).cancelUniqueWork("vpnHeartbeat")
                .getResult().get(5, TimeUnit.SECONDS);
        ActivityManager manager = this.context.getSystemService(ActivityManager.class);
        assertNotNull(manager);
        if (!isVpnServicePresent(manager)) return;
        VpnServiceControls.stop(this.context);
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        do {
            // STOP is asynchronous. Wait for destruction, not only foreground removal,
            // so the next test cannot START an instance with stopSelf already queued.
            if (!isVpnServicePresent(manager)) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("VPN service must be destroyed before deleting its channel.");
    }

    private static boolean isVpnServicePresent(ActivityManager manager) {
        return manager.getRunningServices(Integer.MAX_VALUE).stream()
                .anyMatch(service -> VpnService.class.getName()
                        .equals(service.service.getClassName()));
    }

    private void waitForUpdateNotification(int notificationId) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (hasUpdateNotification(notificationId)) {
                return;
            }
            sleep();
        }
        assertTrue("Expected active update notification id " + notificationId,
                hasUpdateNotification(notificationId));
    }

    private void waitForNoUpdateNotifications() {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (!hasUpdateNotification(NotificationHelper.UPDATE_HOSTS_NOTIFICATION_ID) &&
                    !hasUpdateNotification(NotificationHelper.UPDATE_APP_NOTIFICATION_ID)) {
                return;
            }
            sleep();
        }
        assertFalse("Hosts update notification should not be active.",
                hasUpdateNotification(NotificationHelper.UPDATE_HOSTS_NOTIFICATION_ID));
        assertFalse("App update notification should not be active.",
                hasUpdateNotification(NotificationHelper.UPDATE_APP_NOTIFICATION_ID));
    }

    private boolean hasUpdateNotification(int notificationId) {
        for (StatusBarNotification notification :
                this.notificationManager.getActiveNotifications()) {
            if (notification.getPackageName().equals(this.context.getPackageName()) &&
                    notification.getId() == notificationId) {
                return true;
            }
        }
        return false;
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(
                    "Interrupted while waiting for notification state.",
                    exception
            );
        }
    }
}
