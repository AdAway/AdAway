package org.adaway.vpn;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.os.Message;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.vpn.worker.VpnWorker;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class VpnServiceRuntimeStateTest {
    @Test
    public void queuedMonitorRecoveryCannotRestartStoppedServiceOrReplaceNewWorker() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                VpnService service = new VpnService();
                Message stale = Message.obtain();
                stale.what = 1;
                stale.obj = new VpnWorker(service);
                service.handleMessage(stale);
                assertNull(field("vpnWorker").get(service));

                VpnWorker replacement = new VpnWorker(service);
                field("vpnWorker").set(service, replacement);
                field("vpnRequested").setBoolean(service, true);
                service.handleMessage(stale);
                assertSame(replacement, field("vpnWorker").get(service));
                stale.recycle();
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    public void retiredWorkerStatusCannotStopReplacement() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                VpnService service = new VpnService();
                VpnWorker current = new VpnWorker(service);
                Field worker = field("vpnWorker");
                worker.set(service, current);
                field("vpnRequested").setBoolean(service, true);
                Message stale = Message.obtain();
                stale.what = 0;
                stale.arg1 = VpnStatus.STOPPED.toCode();
                stale.obj = new VpnWorker(service);
                service.handleMessage(stale);
                stale.recycle();
                assertSame(current, worker.get(service));
                assertTrue(field("vpnRequested").getBoolean(service));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    public void lateNetworkAvailableDoesNotStartStoppedService() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                VpnService service = new VpnService();
                Method available = VpnService.class.getDeclaredMethod(
                        "addNetworkType", VpnService.NetworkType.class);
                available.setAccessible(true);
                available.invoke(service, VpnService.NetworkType.WIFI);
                assertNull(field("vpnWorker").get(service));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = VpnService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
