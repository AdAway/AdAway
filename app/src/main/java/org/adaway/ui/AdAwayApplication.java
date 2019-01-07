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

package org.adaway.ui;

import android.app.Application;

import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.hostsinstall.HostsInstallModel;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.SentryLog;
import org.sufficientlysecure.rootcommands.RootCommands;

/**
 * This class is a custom {@link Application} for AdAway app.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class AdAwayApplication extends Application {
    /**
     * The common hosts install model for the whole application.
     */
    private HostsInstallModel mHostsInstallModel;

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
            RootCommands.enableDebug();
        } else {
            Constants.disableDebug();
            RootCommands.disableDebug();
        }
        // Create notification channel
        NotificationHelper.createNotificationChannel(this);
        // Create hosts install model
        this.mHostsInstallModel = new HostsInstallModel(this);
    }

    /**
     * Get the hosts install model.
     *
     * @return The common hosts install model for the whole application.
     */
    public HostsInstallModel getHostsInstallModel() {
        return this.mHostsInstallModel;
    }
}
