package org.adaway.vpn;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

@RunWith(AndroidJUnit4.class)
public class VpnLifecycleInstrumentedTest {
    private static final String HEARTBEAT_WORK_NAME = "vpnHeartbeat";
    private static final long LIFECYCLE_TIMEOUT_MS = 20_000L;
    private static final long HEARTBEAT_TIMEOUT_MS = 5_000L;
    private static final long POLL_INTERVAL_MS = 250L;

    private Context context;
    private RecordingTree recordingTree;
    private VpnStatus originalStatus;
    private AdBlockMethod originalMethod;
    private boolean heartbeatTouched;
    private boolean lifecycleStarted;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        this.originalStatus = PreferenceHelper.getVpnServiceStatus(this.context);
        this.recordingTree = new RecordingTree();
        Timber.plant(this.recordingTree);
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (this.lifecycleStarted) {
                VpnServiceControls.stop(this.context);
                waitUntilStopped();
            }
        } finally {
            try {
                if (this.heartbeatTouched || this.lifecycleStarted) {
                    cancelHeartbeat();
                }
            } finally {
                PreferenceHelper.setVpnServiceStatus(this.context,
                        this.lifecycleStarted ? STOPPED : this.originalStatus);
                if (this.originalMethod != null) {
                    PreferenceHelper.setAbBlockMethod(this.context, this.originalMethod);
                }
                Timber.uproot(this.recordingTree);
            }
        }
    }

    @Test
    public void heartbeatStartAndStopAreLocallyObservable() throws Exception {
        this.heartbeatTouched = true;
        cancelHeartbeat();

        VpnServiceHeartbeat.start(this.context);

        assertTrue("Starting the VPN heartbeat must enqueue unique WorkManager work.",
                waitForHeartbeatActive());

        VpnServiceHeartbeat.stop(this.context);

        assertTrue("Stopping the VPN heartbeat must cancel unique WorkManager work.",
                waitForHeartbeatStopped());
    }

    @Test
    public void startStopResumeEstablishesTunnelStatusTunAndHeartbeatWhenVpnConsentExists()
            throws Exception {
        assumeFalse("Skipping full VPN lifecycle proof: device already has an active VPN.",
                hasAnyVpnNetwork());
        Intent prepareIntent = android.net.VpnService.prepare(this.context);
        assumeTrue("Skipping full VPN lifecycle proof: AdAway VPN consent is not pre-granted.",
                prepareIntent == null);
        this.originalMethod = PreferenceHelper.getAdBlockMethod(this.context);
        PreferenceHelper.setAbBlockMethod(this.context, VPN);
        this.heartbeatTouched = true;
        cancelHeartbeat();
        PreferenceHelper.setVpnServiceStatus(this.context, STOPPED);

        this.lifecycleStarted = true;
        assertTrue("VPN service start command must be accepted.",
                VpnServiceControls.start(this.context));
        waitUntilStatus(RUNNING, LIFECYCLE_TIMEOUT_MS);
        assertTrue("RUNNING must mean Android reports AdAway's VPN network.",
                VpnServiceControls.isTunnelEstablished(this.context));
        assertNotNull("An established VPN should expose an up tun* interface.",
                findUpTunnelInterface());
        assertTrue("Starting the VPN must schedule the heartbeat.",
                waitForHeartbeatActive());
        assertTrue("Lifecycle logs must include a start signal.",
                waitForLog("VPN service started."));

        VpnServiceControls.stop(this.context);
        waitUntilStopped();
        assertFalse("STOPPED must clear established tunnel status.",
                VpnServiceControls.isTunnelEstablished(this.context));
        assertTrue("Stopping the VPN must cancel the heartbeat.",
                waitForHeartbeatStopped());
        assertTrue("Lifecycle logs must include a stop signal.",
                waitForLog("VPN service stopped."));

        assertTrue("VPN service resume command must be accepted.",
                VpnServiceControls.start(this.context));
        waitUntilStatus(RUNNING, LIFECYCLE_TIMEOUT_MS);
        assertTrue("Resumed VPN must re-establish the tunnel.",
                VpnServiceControls.isTunnelEstablished(this.context));
        assertNotNull("A resumed VPN should expose an up tun* interface.",
                findUpTunnelInterface());
    }

    private void waitUntilStopped() throws Exception {
        ActivityManager manager = this.context.getSystemService(ActivityManager.class);
        assertNotNull(manager);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LIFECYCLE_TIMEOUT_MS);
        do {
            boolean servicePresent = manager.getRunningServices(Integer.MAX_VALUE).stream()
                    .anyMatch(service -> VpnService.class.getName()
                            .equals(service.service.getClassName()));
            if (!servicePresent && !hasAnyVpnNetwork()
                    && PreferenceHelper.getVpnServiceStatus(this.context) == STOPPED) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Stopped VPN must release both its service and network.");
    }

    private void waitUntilStatus(VpnStatus expected, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        VpnStatus lastStatus;
        do {
            lastStatus = PreferenceHelper.getVpnServiceStatus(this.context);
            if (lastStatus == expected) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        } while (System.nanoTime() < deadline);

        throw new AssertionError("Timed out waiting for VPN status " + expected
                + "; last status was " + lastStatus);
    }

    private boolean waitForHeartbeatActive() throws Exception {
        return waitForHeartbeatState(false);
    }

    private boolean waitForHeartbeatStopped() throws Exception {
        return waitForHeartbeatState(true);
    }

    private boolean waitForLog(String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LIFECYCLE_TIMEOUT_MS);
        do {
            if (this.recordingTree.contains(expected)) {
                return true;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        } while (System.nanoTime() < deadline);
        return false;
    }

    private boolean waitForHeartbeatState(boolean stopped) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(HEARTBEAT_TIMEOUT_MS);
        do {
            List<WorkInfo> workInfos = heartbeatWorkInfos();
            boolean hasActiveWork = false;
            for (WorkInfo workInfo : workInfos) {
                if (!workInfo.getState().isFinished()) {
                    hasActiveWork = true;
                    break;
                }
            }
            if (stopped != hasActiveWork) {
                return true;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        } while (System.nanoTime() < deadline);
        return false;
    }

    private List<WorkInfo> heartbeatWorkInfos() throws Exception {
        return WorkManager.getInstance(this.context)
                .getWorkInfosForUniqueWork(HEARTBEAT_WORK_NAME)
                .get(HEARTBEAT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeat() throws Exception {
        WorkManager.getInstance(this.context)
                .cancelUniqueWork(HEARTBEAT_WORK_NAME)
                .getResult()
                .get(HEARTBEAT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private boolean hasAnyVpnNetwork() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) this.context.getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(TRANSPORT_VPN)) {
                return true;
            }
        }
        return false;
    }

    private static NetworkInterface findUpTunnelInterface() throws Exception {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (networkInterface.getName().matches("tun[0-9]+") && networkInterface.isUp()) {
                return networkInterface;
            }
        }
        return null;
    }

    private static final class RecordingTree extends Timber.Tree {
        private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected void log(int priority, String tag, String message, Throwable throwable) {
            this.messages.add(message);
        }

        private boolean contains(String expected) {
            synchronized (this.messages) {
                for (String message : this.messages) {
                    if (message.contains(expected)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
