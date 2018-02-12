/*
 * Copyright (C) 2011-2013 Dominik Schürmann <dominik@dominikschuermann.de>
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
import org.adaway.util.WebserverUtils;

import android.app.IntentService;
import android.content.Intent;

public class BootService extends IntentService {

    public BootService() {
        super("AdAwayBootService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Start web server
        Log.d(Constants.TAG, "BootService: onHandleIntent");
        WebserverUtils.startWebServerOnBoot(getApplicationContext());
    }

}