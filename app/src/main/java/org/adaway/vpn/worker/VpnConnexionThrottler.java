package org.adaway.vpn.worker;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.util.Log;


public class VpnConnexionThrottler {
    private static final String TAG = "VpnConnexionThrottler";
    private static final long INITAl_TIMEOUT_MS = 1_000;
    private static final long MAXIMUM_TIMEOUT_MS = 128_000;
    private long timeout;
    private long time;

    public VpnConnexionThrottler() {
        this.time = 0;
        this.timeout = INITAl_TIMEOUT_MS;
    }

    public void throttle() throws InterruptedException {
        long now = System.currentTimeMillis();
        // Check first call
        if (this.time == 0) {
            this.time = now;
            return;
        }
        // Compute time between two throttle calls
        long elapsedTimeBetweenCall = now - this.time;
        // If the call happens before the time out
        if (elapsedTimeBetweenCall < this.timeout) {
            this.time = now;
            // Increase timeout and wait the remaining time to limit connexion
            increaseTimeout();
            long remainingTime = this.timeout - elapsedTimeBetweenCall;
            Log.d(TAG, "Limiting the connexion by wait for " + remainingTime / 1000 + "s.");
            Thread.sleep(remainingTime);
        }
        // If the call happens after the timeout
        else {
            this.time = now;
            // Decrease the time out (up to restoring it) and do no limit connexion
            decreaseTimeout(elapsedTimeBetweenCall > MAXIMUM_TIMEOUT_MS);
            Log.d(TAG, "Allowing the connexion right now.");
        }
    }

    private void increaseTimeout() {
        this.timeout = min(this.timeout * 2, MAXIMUM_TIMEOUT_MS);
        Log.d(TAG, "Increasing timeout to " + this.timeout / 1000 + "s.");
    }

    private void decreaseTimeout(boolean reset) {
        this.timeout = reset ? INITAl_TIMEOUT_MS : max(this.timeout / 4, INITAl_TIMEOUT_MS);
        Log.d(TAG, "Decreasing timeout to " + this.timeout / 1000 + "s.");
    }
}
