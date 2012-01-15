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
import org.adaway.util.WebserverUtils;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import android.content.Context;
import android.content.Intent;

/**
 * CheckUpdateService checks every 24 hours at about 9 am for updates of hosts sources
 */
public class BootService extends WakefulIntentService {
    private Context mApplicationContext;

    public BootService() {
        super("AdAwayBootService");
    }

    /**
     * Asynchronous background operations of service
     */
    @Override
    public void doWakefulWork(Intent intent) {
        mApplicationContext = getApplicationContext();
        

        WebserverUtils.startWebserverOnBoot(mApplicationContext);
        
        Log.d(Constants.TAG, "after reg alarm and webserver");
    }

}
