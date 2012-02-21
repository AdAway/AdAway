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

import java.io.FileOutputStream;

import org.adaway.R;
import org.adaway.helper.ResultHelper;
import org.adaway.ui.BaseActivity;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.StatusCodes;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import android.content.Context;
import android.content.Intent;

public class RevertService extends WakefulIntentService {
    private Context mService;

    public RevertService() {
        super("AdAwayRevertService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mService = this;

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Asynchronous background operations of service, with wakelock
     */
    @Override
    public void doWakefulWork(Intent intent) {
        // disable buttons
        BaseActivity.setButtonsBroadcast(mService, false);

        int revertResult = revert();

        Log.d(Constants.TAG, "revert result: " + revertResult);

        // enable buttons
        BaseActivity.setButtonsBroadcast(mService, true);

        ResultHelper.showNotificationBasedOnResult(mService, revertResult, null);
    }

    /**
     * Reverts to default hosts file
     * 
     * @return Status codes REVERT_SUCCESS or REVERT_FAIL
     */
    private int revert() {
        BaseActivity.setStatusBroadcast(mService, getString(R.string.status_reverting),
                getString(R.string.status_reverting_subtitle), StatusCodes.CHECKING);

        // build standard hosts file
        try {
            FileOutputStream fos = mService.openFileOutput(Constants.HOSTS_FILENAME,
                    Context.MODE_PRIVATE);

            // default localhost
            String localhost = Constants.LOCALHOST_IPv4 + " " + Constants.LOCALHOST_HOSTNAME
                    + Constants.LINE_SEPERATOR + Constants.LOCALHOST_IPv6 + " "
                    + Constants.LOCALHOST_HOSTNAME;
            fos.write(localhost.getBytes());
            fos.close();

            // copy build hosts file with RootTools
            ApplyUtils.copyHostsFile(mService, "");

            // delete generated hosts file after applying it
            mService.deleteFile(Constants.HOSTS_FILENAME);

            // set status to disabled
            BaseActivity.updateStatusDisabled(mService);

            return StatusCodes.REVERT_SUCCESS;
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception: " + e);
            e.printStackTrace();

            return StatusCodes.REVERT_FAIL;
        }
    }
}