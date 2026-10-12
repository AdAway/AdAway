package org.adaway.ui.prefs;

import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.util.Constants.PREFS_NAME;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;

import androidx.preference.SwitchPreferenceCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.vpn.VpnService;
import org.adaway.vpn.VpnServiceControls;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

/** Requires adawayLiveVpnSmoke=true, prepared consent, and no existing VPN. */
@RunWith(AndroidJUnit4.class)
public class PrefsVpnRestartInstrumentedTest {
    private static final long TIMEOUT_MS = 20_000;

    @Test(timeout = 90_000)
    public void bypassPreferenceReplacesTunnelWithoutStoppingService() throws Exception {
        assumeTrue("Live VPN smoke is opt-in.", "true".equals(
                InstrumentationRegistry.getArguments().getString("adawayLiveVpnSmoke")));
        Context context = ApplicationProvider.getApplicationContext();
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        assertNotNull(connectivity);
        assumeTrue("Do not interrupt an existing VPN.", vpnNetwork(connectivity) == null
                && !servicePresent(context)
                && PreferenceHelper.getVpnServiceStatus(context) == STOPPED);
        assumeTrue("VPN consent must already be prepared.",
                android.net.VpnService.prepare(context) == null);

        AdBlockMethod originalMethod = PreferenceHelper.getAdBlockMethod(context);
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String bypassKey = context.getString(R.string.pref_vpn_allow_app_bypass_key);
        boolean hadBypass = preferences.contains(bypassKey);
        boolean originalBypass = PreferenceHelper.getVpnAllowAppBypass(context);
        LifecycleTree lifecycle = new LifecycleTree();
        Timber.plant(lifecycle);
        try {
            PreferenceHelper.setAbBlockMethod(context, VPN);
            assertTrue(VpnServiceControls.start(context));
            Network originalNetwork = waitForTunnel(context, connectivity, null);
            int originalStarts = lifecycle.workerStarts.get();
            int originalDestructions = lifecycle.serviceDestructions.get();

            try (ActivityScenario<PrefsActivity> scenario = ActivityScenario.launch(PrefsActivity.class)) {
                scenario.onActivity(activity -> {
                    PrefsVpnFragment fragment = new PrefsVpnFragment();
                    activity.getSupportFragmentManager().beginTransaction()
                            .replace(android.R.id.content, fragment).commitNow();
                    SwitchPreferenceCompat bypass = fragment.findPreference(bypassKey);
                    assertNotNull(bypass);
                    boolean changed = !bypass.isChecked();
                    // Invoke the registered production callback, then persist as the switch does.
                    assertTrue(bypass.callChangeListener(changed));
                    bypass.setChecked(changed);
                });

                waitForTunnel(context, connectivity, originalNetwork);
                assertTrue("Restart must start a replacement worker.",
                        lifecycle.workerStarts.get() > originalStarts);
                assertEquals("Preference restart must not destroy the VPN service.",
                        originalDestructions, lifecycle.serviceDestructions.get());
                assertEquals(!originalBypass, PreferenceHelper.getVpnAllowAppBypass(context));
            }
        } finally {
            try {
                // Do not create an absent service just to send STOP during fixture cleanup.
                if (servicePresent(context)) {
                    VpnServiceControls.stop(context);
                }
                waitForStopped(context, connectivity);
            } finally {
                try {
                    WorkManager.getInstance(context).cancelUniqueWork("vpnHeartbeat")
                            .getResult().get(5, TimeUnit.SECONDS);
                } finally {
                    SharedPreferences.Editor editor = preferences.edit();
                    if (hadBypass) {
                        editor.putBoolean(bypassKey, originalBypass);
                    } else {
                        editor.remove(bypassKey);
                    }
                    editor.apply();
                    PreferenceHelper.setAbBlockMethod(context, originalMethod);
                    Timber.uproot(lifecycle);
                }
            }
        }
    }

    private static Network waitForTunnel(Context context, ConnectivityManager connectivity,
                                        Network previous) {
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        do {
            Network network = vpnNetwork(connectivity);
            LinkProperties link = network == null ? null : connectivity.getLinkProperties(network);
            if (network != null && !network.equals(previous) && link != null
                    && link.getInterfaceName() != null && !link.getDnsServers().isEmpty()
                    && VpnServiceControls.isTunnelEstablished(context)) {
                return network;
            }
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Expected a newly established VPN network, not the previous tunnel.");
    }

    private static void waitForStopped(Context context, ConnectivityManager connectivity) {
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        do {
            if (!servicePresent(context) && vpnNetwork(connectivity) == null
                    && PreferenceHelper.getVpnServiceStatus(context) == STOPPED) {
                return;
            }
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("VPN fixture must release its service and network.");
    }

    private static Network vpnNetwork(ConnectivityManager connectivity) {
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(TRANSPORT_VPN)) {
                return network;
            }
        }
        return null;
    }

    private static boolean servicePresent(Context context) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        assertNotNull(manager);
        return manager.getRunningServices(Integer.MAX_VALUE).stream().anyMatch(
                service -> VpnService.class.getName().equals(service.service.getClassName()));
    }

    private static final class LifecycleTree extends Timber.Tree {
        final AtomicInteger workerStarts = new AtomicInteger();
        final AtomicInteger serviceDestructions = new AtomicInteger();

        @Override
        protected void log(int priority, String tag, String message, Throwable throwable) {
            if (message.startsWith("Starting VPN thread")) {
                workerStarts.incrementAndGet();
            } else if (message.startsWith("Destroying VPN service")) {
                serviceDestructions.incrementAndGet();
            }
        }
    }
}
