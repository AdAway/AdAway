package org.adaway.vpn.worker;

import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNull;

import android.content.Context;

import org.adaway.vpn.VpnServiceControls;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * This class monitors the VPN network interface is still up while the VPN is running.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class VpnConnectionMonitor {
    private static final int CONNECTION_CHECK_DELAY_MS = 10_000;
    private static final Pattern TUNNEL_PATTERN = Pattern.compile("tun([0-9]+)");
    /**
     * The application context.
     */
    private final Context context;
    /**
     * Whether the monitor is running (<code>true</code> if running, <code>false</code> if stopped).
     */
    private final AtomicBoolean running;
    /**
     * The network interface to monitor (<code>null</code> if not initialized).
     */
    private NetworkInterface networkInterface;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    VpnConnectionMonitor(Context context) {
        this.context = context;
        this.running = new AtomicBoolean(true);
        this.networkInterface = null;
    }

    private static NetworkInterface findVpnNetworkInterface() {
        try {
            NetworkInterface vpnNetworkInterface = null;
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                vpnNetworkInterface = pickLastVpnNetworkInterface(vpnNetworkInterface, networkInterface);
            }
            if (vpnNetworkInterface == null) {
                throw new IllegalStateException("Failed to find a network interface.");
            }
            return vpnNetworkInterface;
        } catch (SocketException e) {
            throw new IllegalStateException("Failed to find VPN network interface.", e);
        }
    }

    private static NetworkInterface pickLastVpnNetworkInterface(NetworkInterface current, NetworkInterface other) {
        Matcher otherMatcher = TUNNEL_PATTERN.matcher(other.getName());
        if (otherMatcher.matches()) {
            if (current == null) {
                return other;
            } else {
                Matcher currentMatcher = TUNNEL_PATTERN.matcher(current.getName());
                int currentTunnelNumber = parseInt(requireNonNull(currentMatcher.group(1)));
                int otherTunnelNumber = parseInt(requireNonNull(otherMatcher.group(1)));
                return otherTunnelNumber > currentTunnelNumber ? other : current;
            }
        } else {
            return current;
        }
    }

    /**
     * Initialize the monitor once the VPN connection is up.
     */
    void initialize() {
        Timber.d("Initializing connection monitor…");
        this.networkInterface = findVpnNetworkInterface();
        Timber.d("Connection monitor initialized to watch interface %s.", this.networkInterface.getName());
    }

    /**
     * Monitor the VPN network interface is still up while the VPN is running.
     */
    void monitor() {
        try {
            while (this.running.get()) {
                if (!this.networkInterface.isUp()) {
                    stop();
                    Timber.i("VPN network interface is down. Starting VPN service…");
                    VpnServiceControls.start(this.context);
                }
                try {
                    Thread.sleep(CONNECTION_CHECK_DELAY_MS);
                } catch (InterruptedException e) {
                    Timber.d(e, "Stop monitoring.");
                    break;
                }
            }
        } catch (SocketException e) {
            Timber.w(e, "Failed to test VPN network interface. Starting VPN service…");
            VpnServiceControls.start(this.context);
        }
    }

    /**
     * Reset the connection monitor if the VPN connection changed.
     */
    void reset() {
        this.networkInterface = null;
    }

    /**
     * Stop the monitor.
     */
    void stop() {
        this.running.set(false);
    }
}
