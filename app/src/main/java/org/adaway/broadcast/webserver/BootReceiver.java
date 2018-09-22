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

package org.adaway.broadcast.webserver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.adaway.helper.PreferenceHelper;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.WebServerUtils;

/**
 * This broadcast receiver is executed after boot
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(Constants.TAG, "BootReceiver invoked");
            // Start web server on boot if enabled in preferences
            if (PreferenceHelper.getWebServerOnBoot(context)) {
                WebServerUtils.startWebServer(context);
            }
        }
    }
}
