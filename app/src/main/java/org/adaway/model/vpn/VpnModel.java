package org.adaway.model.vpn;

import android.content.Context;

import org.adaway.model.error.HostErrorException;
import org.adaway.model.hostlist.HostListModel;
import org.adaway.vpn.VpnService;

import static org.adaway.model.error.HostError.DISABLE_VPN_FAIL;
import static org.adaway.model.error.HostError.ENABLE_VPN_FAIL;

/**
 * This class is the model to represent VPN service configuration.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class VpnModel extends HostListModel {
    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public VpnModel(Context context) {
        super(context);
    }

    @Override
    public void apply() throws HostErrorException {
        // TODO Clear VPN rule cache
        boolean started = VpnService.isStarted(context);
        if (!started) {
            VpnService.start(context);
            started = VpnService.isStarted(context);
        }
        this.applied.postValue(started);
        if (!started) {
            throw new HostErrorException(ENABLE_VPN_FAIL);
        }
    }

    @Override
    public void revert() throws HostErrorException {
        VpnService.stop(context);
        boolean started = VpnService.isStarted(context);
        this.applied.postValue(started);
        if (started) {
            throw new HostErrorException(DISABLE_VPN_FAIL);
        }
    }
}
