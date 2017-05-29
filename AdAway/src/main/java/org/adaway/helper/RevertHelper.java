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

package org.adaway.helper;

import android.content.Context;

import org.adaway.R;
import org.adaway.ui.BaseActivity;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.StatusCodes;
import org.sufficientlysecure.rootcommands.Shell;

import java.io.FileOutputStream;

// TODO Add Javadoc
public class RevertHelper {
    /**
     * The application context.
     */
    private final Context mContext;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public RevertHelper(Context context) {
        this.mContext = context;
    }

    /**
     * Asynchronous background operations of service, with wakelock
     */
    public void revert() {
        // disable buttons
        BaseActivity.setButtonsDisabledBroadcast(mContext, true);

        try {
            Shell rootShell = Shell.startRootShell();   // TODO Close shell
            int revertResult = revertHostFiles(rootShell);
            rootShell.close();

            Log.d(Constants.TAG, "revert result: " + revertResult);


            ResultHelper.showNotificationBasedOnResult(mContext, revertResult, null);
        } catch (Exception e) {
            Log.e(Constants.TAG, "Problem while reverting!", e);
        }
        // enable buttons
        BaseActivity.setButtonsDisabledBroadcast(mContext, false);
    }

    /**
     * Reverts to default hosts file
     *
     * @return @{@link StatusCodes#REVERT_SUCCESS} or {@link StatusCodes#REVERT_FAIL}.
     */
    private int revertHostFiles(Shell shell) {
        BaseActivity.setStatusBroadcast(mContext, mContext.getString(R.string.status_reverting),
                mContext.getString(R.string.status_reverting_subtitle), StatusCodes.CHECKING);

        // build standard hosts file
        try {
            // TODO Close stream
            FileOutputStream fos = mContext.openFileOutput(Constants.HOSTS_FILENAME,
                    Context.MODE_PRIVATE);

            // default localhost
            String localhost = Constants.LOCALHOST_IPv4 + " " + Constants.LOCALHOST_HOSTNAME
                    + Constants.LINE_SEPERATOR + Constants.LOCALHOST_IPv6 + " "
                    + Constants.LOCALHOST_HOSTNAME;
            fos.write(localhost.getBytes());
            fos.close();

            // copy build hosts file with RootTools, based on target from preferences
            if (PreferenceHelper.getApplyMethod(mContext).equals("writeToSystem")) {

                ApplyUtils.copyHostsFile(mContext, Constants.ANDROID_SYSTEM_ETC_HOSTS, shell);
            } else if (PreferenceHelper.getApplyMethod(mContext).equals("writeToDataData")) {

                ApplyUtils.copyHostsFile(mContext, Constants.ANDROID_DATA_DATA_HOSTS, shell);
            } else if (PreferenceHelper.getApplyMethod(mContext).equals("writeToData")) {

                ApplyUtils.copyHostsFile(mContext, Constants.ANDROID_DATA_HOSTS, shell);
            } else if (PreferenceHelper.getApplyMethod(mContext).equals("customTarget")) {

                ApplyUtils.copyHostsFile(mContext, PreferenceHelper.getCustomTarget(mContext),
                        shell);
            }

            // delete generated hosts file after applying it
            mContext.deleteFile(Constants.HOSTS_FILENAME);

            // set status to disabled
            BaseActivity.updateStatusDisabled(mContext);

            return StatusCodes.REVERT_SUCCESS;
        } catch (Exception e) {
            Log.e(Constants.TAG, "Exception", e);

            return StatusCodes.REVERT_FAIL;
        }
    }
}
