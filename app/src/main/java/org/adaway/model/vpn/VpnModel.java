package org.adaway.model.vpn;

import android.content.Context;
import android.util.LruCache;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.util.Log;
import org.adaway.vpn.VpnService;

import java.util.ArrayList;
import java.util.List;

import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.model.error.HostError.DISABLE_VPN_FAIL;
import static org.adaway.model.error.HostError.ENABLE_VPN_FAIL;

/**
 * This class is the model to represent VPN service configuration.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class VpnModel extends AdBlockModel {
    private static final String TAG = "VpnModel";
    private final HostListItemDao hostListItemDao;
    private final LruCache<String, Boolean> blockCache;
    private final List<String> logs;
    private boolean recordingLogs;
    private int requestCount;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public VpnModel(Context context) {
        super(context);
        AppDatabase database = AppDatabase.getInstance(context);
        this.hostListItemDao = database.hostsListItemDao();
        this.blockCache = new LruCache<String, Boolean>(4 * 1024) {
            @Override
            protected Boolean create(String key) {
                return VpnModel.this.hostListItemDao.isHostBlocked(key);
            }
        };
        this.logs = new ArrayList<>(128);
        this.recordingLogs = false;
        this.requestCount = 0;
        this.applied.postValue(VpnService.isStarted(context));
    }

    @Override
    public AdBlockMethod getMethod() {
        return VPN;
    }

    @Override
    public void apply() throws HostErrorException {
        // Clear cache
        this.blockCache.evictAll();
        // Start VPN
        boolean started = VpnService.isStarted(this.context);
        if (!started) {
            started = VpnService.start(this.context);
        }
        this.applied.postValue(started);
        if (!started) {
            throw new HostErrorException(ENABLE_VPN_FAIL);
        }
    }

    @Override
    public void revert() throws HostErrorException {
        VpnService.stop(this.context);
        boolean started = VpnService.isStarted(this.context);
        this.applied.postValue(started);
        if (started) {
            throw new HostErrorException(DISABLE_VPN_FAIL);
        }
    }

    @Override
    public boolean isRecordingLogs() {
        return this.recordingLogs;
    }

    @Override
    public void setRecordingLogs(boolean recording) {
        this.recordingLogs = recording;
    }

    @Override
    public List<String> getLogs() {
        return this.logs;
    }

    @Override
    public void clearLogs() {
        this.logs.clear();
    }

    /**
     * Checks if a host is blocked.
     *
     * @param host A hostname to check.
     * @return {@code true} if the host is blocked, {@code false} otherwise.
     */
    public boolean isBlocked(String host) {
        // Compute miss rate periodically
        this.requestCount++;
        if (this.requestCount >= 1000) {
            int hits = this.blockCache.hitCount();
            int misses = this.blockCache.missCount();
            double missRate = 100D * (hits + misses) / misses;
            Log.d(TAG, "Host cache miss rate: " + missRate);
            this.requestCount = 0;
        }
        // Add host to logs
        if (this.recordingLogs) {
            this.logs.add(host);
        }
        // Check cache
        return this.blockCache.get(host);
    }
}
