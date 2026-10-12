package org.adaway.vpn;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static org.adaway.broadcast.Command.START;
import static org.adaway.broadcast.Command.STOP;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STOPPED;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;

import org.adaway.helper.PreferenceHelper;

import java.util.Arrays;
import java.util.Objects;

import timber.log.Timber;

/**
 * This utility class allows controlling (start and stop) the AdAway VPN
 * service.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class VpnServiceControls {
    /**
     * Private constructor.
     */
    private VpnServiceControls() {

    }

    /**
     * Check if the VPN service is currently running.
     *
     * @param context The application context.
     * @return {@code true} if the VPN service is currently running, {@code false}
     *         otherwise.
     */
    public static boolean isRunning(Context context) {
        return isTunnelEstablished(context);
    }

    /**
     * Check if AdAway has an established VPN tunnel.
     *
     * @param context The application context.
     * @return {@code true} only when AdAway has persisted a post-establish
     *         {@link VpnStatus#RUNNING} status and Android reports a VPN network.
     */
    public static boolean isTunnelEstablished(Context context) {
        boolean networkVpnCapability = checkAdAwayNetworkVpnCapability(context);
        VpnStatus status = PreferenceHelper.getVpnServiceStatus(context);
        if (status == RUNNING && !networkVpnCapability) {
            status = STOPPED;
            PreferenceHelper.setVpnServiceStatus(context, status);
        }
        return status == RUNNING && networkVpnCapability;
    }

    /**
     * Check if the VPN service is started.
     *
     * @param context The application context.
     * @return {@code true} if the VPN service is started, {@code false} otherwise.
     */
    public static boolean isStarted(Context context) {
        return PreferenceHelper.getVpnServiceStatus(context).isStarted();
    }

    /**
     * Start the VPN service.
     *
     * @param context The application context.
     * @return {@code true} if the service is started, {@code false} otherwise.
     */
    public static boolean start(Context context) {
        Timber.d("VpnServiceControls.start() called");
        // Check if VPN is already running
        if (isRunning(context)) {
            Timber.d("VPN already running");
            return true;
        }
        return launchVpn(context);
    }

    /**
     * Reload VPN configuration by replacing the worker in the existing service.
     *
     * @param context The application context.
     * @return {@code true} if the restart command was accepted, {@code false} otherwise.
     */
    public static boolean restart(Context context) {
        // A STOP/START pair can lose START while the old tunnel is still reported running.
        return launchVpn(context);
    }

    private static boolean launchVpn(Context context) {
        // Start the VPN service
        Intent intent = new Intent(context, VpnService.class);
        START.appendToIntent(intent);
        try {
            Timber.d("Attempting to launch service intent");
            android.content.ComponentName component = context.startForegroundService(intent);

            boolean started = component != null;
            Timber.d("Service launch result: %s", component);

            if (started) {
                // Start the heartbeat
                VpnServiceHeartbeat.start(context);
            }
            return started;
        } catch (Exception e) {
            Timber.d(e, "Failed to start service with exception");
            return false;
        }
    }

    /**
     * Stop the VPN service.
     *
     * @param context The application context.
     */
    public static void stop(Context context) {
        // Stop the heartbeat
        VpnServiceHeartbeat.stop(context);
        // Stop the service
        Intent intent = new Intent(context, VpnService.class);
        STOP.appendToIntent(intent);
        context.startService(intent);
    }

    private static boolean checkAdAwayNetworkVpnCapability(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        return Arrays.stream(connectivityManager.getAllNetworks())
                .map(connectivityManager::getNetworkCapabilities)
                .filter(Objects::nonNull)
                .anyMatch(networkCapabilities -> isAdAwayVpnNetwork(context, networkCapabilities));
    }

    private static boolean isAdAwayVpnNetwork(Context context, NetworkCapabilities networkCapabilities) {
        if (!networkCapabilities.hasTransport(TRANSPORT_VPN)) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        return networkCapabilities.getOwnerUid() == context.getApplicationInfo().uid;
    }
}
