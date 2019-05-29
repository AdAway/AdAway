/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway;

import android.app.Application;

import com.topjohnwu.superuser.Shell;

import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.hostlist.HostListModel;
import org.adaway.model.hostsinstall.HostsInstallModel;
import org.adaway.model.source.SourceModel;
import org.adaway.model.vpn.VpnModel;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.SentryLog;

/**
 * This class is a custom {@link Application} for AdAway app.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class AdAwayApplication extends Application {
    /**
     * The common source model for the whole application.
     */
    private SourceModel sourceModel;
    /**
     * The common VPN model for the whole application.
     */
    private VpnModel vpnModel;
    /**
     * The common hosts install model for the whole application.
     */
    private HostsInstallModel hostsInstallModel;

    @Override
    public void onCreate() {
        // Delegate application creation
        super.onCreate();
        // Initialize sentry
        SentryLog.init(this);
        // Set Debug level based on preference
        if (PreferenceHelper.getDebugEnabled(this)) {
            Log.d(Constants.TAG, "Debug set to true by preference!");
            Constants.enableDebug();
            Shell.Config.verboseLogging(true);
        } else {
            Constants.disableDebug();
            Shell.Config.verboseLogging(false);
        }
        // Create notification channels
        NotificationHelper.createNotificationChannels(this);
        // Create models
        this.sourceModel = new SourceModel(this);
        this.vpnModel = new VpnModel(this);
        this.hostsInstallModel = new HostsInstallModel(this);
    }

    /**
     * Get the source model.
     * @return The common source model for the whole application.
     */
    public SourceModel getSourceModel() {
        return this.sourceModel;
    }

    /**
     * Get the hosts install model.
     *
     * @return The common hosts install model for the whole application.
     */
    public HostsInstallModel getHostsInstallModel() {
        return this.hostsInstallModel;
    }

    /**
     * Get the hosts install model.
     *
     * @return The common hosts install model for the whole application.
     */
    public VpnModel getVpnModel() {
        return this.vpnModel;
    }

    /**
     * Get the hosts list model.
     *
     * @return The common hosts list model for the whole application.
     */
    public HostListModel getHostsListModel() {
        // TODO Check VPN or hosts install
        return this.hostsInstallModel;
    }
}
