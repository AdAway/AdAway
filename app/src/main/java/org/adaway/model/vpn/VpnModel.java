package org.adaway.model.vpn;

import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.model.error.HostError.ENABLE_VPN_FAIL;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.LruCache;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.entity.HostEntry;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.vpn.VpnService;
import org.adaway.vpn.VpnServiceControls;
import org.adaway.vpn.VpnStartDecision;
import org.adaway.vpn.VpnStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import timber.log.Timber;

/**
 * This class is the model to represent VPN service configuration.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class VpnModel extends AdBlockModel {
    private final HostEntryDao hostEntryDao;
    private final LruCache<String, HostEntry> blockCache;
    private final LinkedHashSet<String> logs;
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
        this.hostEntryDao = database.hostEntryDao();
        this.blockCache = new LruCache<String, HostEntry>(4 * 1024) {
            @Override
            protected HostEntry create(String key) {
                return VpnModel.this.hostEntryDao.getEntry(key);
            }
        };
        this.logs = new LinkedHashSet<>();
        this.recordingLogs = false;
        this.requestCount = 0;
        this.applied.postValue(VpnServiceControls.isRunning(context));
        registerVpnStatusReceiver();
    }

    @Override
    public AdBlockMethod getMethod() {
        return VPN;
    }

    @Override
    public void apply() throws HostErrorException {
        // Clear cache
        this.blockCache.evictAll();
        // Treat any direct call to apply() as user intent ON: tile, notification "Resume",
        // home toggle, snackbar from a fresh user click, etc. Background callers must use
        // applyIfActive() instead.
        Timber.i("VpnModel.apply: user-initiated start of VPN.");
        PreferenceHelper.setVpnServiceUserEnabled(this.context, true);
        boolean started = VpnServiceControls.start(this.context);
        this.applied.postValue(started);
        if (!started) {
            Timber.w("VpnModel.apply: VPN service failed to start.");
            throw new HostErrorException(ENABLE_VPN_FAIL);
        }
        setState(R.string.status_vpn_configuration_updated);
    }

    @Override
    public void applyIfActive() throws HostErrorException {
        // Always refresh the in-memory cache. A running VPN picks up new hosts on the
        // fly via the cache; if the user has stopped the VPN we still want a fresh
        // cache for the moment they resume it.
        this.blockCache.evictAll();
        boolean userEnabled = PreferenceHelper.getVpnServiceUserEnabled(this.context);
        if (!VpnStartDecision.mayBackgroundStart(userEnabled)) {
            Timber.i("VpnModel.applyIfActive: skipping VPN start — user has disabled the VPN.");
            // Keep displayed state honest.
            this.applied.postValue(VpnServiceControls.isRunning(this.context));
            return;
        }
        Timber.i("VpnModel.applyIfActive: refreshing VPN (user-enabled).");
        // Idempotent: returns true immediately if already running, otherwise (re)starts.
        boolean started = VpnServiceControls.start(this.context);
        this.applied.postValue(started);
        if (!started) {
            Timber.w("VpnModel.applyIfActive: VPN service failed to (re)start.");
            throw new HostErrorException(ENABLE_VPN_FAIL);
        }
        setState(R.string.status_vpn_configuration_updated);
    }

    @Override
    public void revert() {
        Timber.i("VpnModel.revert: user-initiated stop of VPN.");
        PreferenceHelper.setVpnServiceUserEnabled(this.context, false);
        VpnServiceControls.stop(this.context);
        this.applied.postValue(false);
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
        return new ArrayList<>(this.logs);
    }

    @Override
    public void clearLogs() {
        this.logs.clear();
    }

    /**
     * Checks host entry related to an host name.
     *
     * @param host A hostname to check.
     * @return The related host entry.
     */
    public HostEntry getEntry(String host) {
        // Compute miss rate periodically
        this.requestCount++;
        if (this.requestCount >= 1000) {
            int hits = this.blockCache.hitCount();
            int misses = this.blockCache.missCount();
            double missRate = 100D * (hits + misses) / misses;
            Timber.d("Host cache miss rate: %s.", missRate);
            this.requestCount = 0;
        }
        // Add host to logs
        if (this.recordingLogs) {
            this.logs.add(host);
        }
        // Check cache
        return this.blockCache.get(host);
    }

    /**
     * Listen to status broadcasts from {@link VpnService} and keep the {@code applied}
     * LiveData consistent with the real service state. Without this the tile and main UI
     * could remain stuck on "active" if the service is killed externally.
     */
    private void registerVpnStatusReceiver() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                Object extra = intent.getSerializableExtra(VpnService.VPN_UPDATE_STATUS_EXTRA);
                if (!(extra instanceof VpnStatus)) {
                    return;
                }
                VpnStatus status = (VpnStatus) extra;
                Timber.d("VpnModel: received VPN status broadcast: %s.", status);
                switch (status) {
                    case RUNNING:
                        applied.postValue(true);
                        break;
                    case STOPPED:
                        applied.postValue(false);
                        break;
                    default:
                        // STARTING / STOPPING / RECONNECTING / WAITING_FOR_NETWORK don't
                        // change applied state — service is still considered active.
                        break;
                }
            }
        };
        LocalBroadcastManager.getInstance(this.context).registerReceiver(
                receiver,
                new IntentFilter(VpnService.VPN_UPDATE_STATUS_INTENT)
        );
    }
}
