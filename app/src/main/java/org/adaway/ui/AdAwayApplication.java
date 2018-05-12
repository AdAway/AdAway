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

import org.adaway.helper.PreferenceHelper;
import org.adaway.service.UpdateService;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.sufficientlysecure.rootcommands.RootCommands;

import android.app.Application;

import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobManager;

public class AdAwayApplication extends Application {

    @Override
    public void onCreate() {

        // workaround for http://code.google.com/p/android/issues/detail?id=20915
        try {
            Log.d(Constants.TAG, "Setting workaround for AsyncTask...");
            Class.forName("android.os.AsyncTask");
        } catch (Exception e) { // silently catch all
        }

        // Set Debug level based on preference
        if (PreferenceHelper.getDebugEnabled(this)) {
            Constants.enableDebug();
            Log.d(Constants.TAG, "Debug set to true by preference!");
            RootCommands.enableDebug();
        } else {
            Constants.disableDebug();
            RootCommands.disableDebug();
        }

        // Create job manager an register job creators
        JobManager.create(this).addJobCreator(new UpdateService.UpdateJobCreator());

        super.onCreate();
    }

}
