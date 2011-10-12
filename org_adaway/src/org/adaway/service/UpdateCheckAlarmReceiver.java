/*
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.adaway.service;

import org.adaway.util.Constants;
import org.adaway.util.Log;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UpdateCheckAlarmReceiver extends BroadcastReceiver {
    // onReceive must be very quick and not block, so it just fires up a Service
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(Constants.TAG,
                "UpdateCheckAlarmReceiver invoked, starting CheckUpdateService in background");
        context.startService(new Intent(context, UpdateCheckService.class));
    }
}
