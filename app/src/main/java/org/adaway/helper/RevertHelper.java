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
import org.adaway.ui.home.HomeFragment;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.adaway.util.StatusCodes;
import org.sufficientlysecure.rootcommands.Shell;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * This class is a helper class to revert hosts file to the default configuration.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
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
     * Revert to default host file.
     */
    public void revert() {
        // Notify revert
        HomeFragment.setStatusBroadcast(
                this.mContext,
                this.mContext.getString(R.string.status_reverting),
                this.mContext.getString(R.string.status_reverting_subtitle),
                StatusCodes.CHECKING
        );
        // Declare status code
        int statusCode;
        // Create root shell
        Shell rootShell = null;
        try {
            rootShell = Shell.startRootShell();
            // Revert hosts file
            statusCode = this.revertHostFiles(rootShell);
            Log.d(Constants.TAG, "Revert status code: " + statusCode);
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Unable to revert hosts file.", exception);
            statusCode = StatusCodes.REVERT_FAIL;
        } finally {
            // Close shell
            if (rootShell != null) {
                try {
                    rootShell.close();
                } catch (IOException exception) {
                    Log.d(Constants.TAG, "Error while closing shell.", exception);
                }
            }
        }
        // Notify revert status
        ResultHelper.showNotificationBasedOnResult(
                this.mContext,
                statusCode,
                null
        );
    }

    /**
     * Reverts to default hosts file
     *
     * @return @{@link StatusCodes#REVERT_SUCCESS} or {@link StatusCodes#REVERT_FAIL}.
     */
    private int revertHostFiles(Shell shell) {
        // Create private file
        try (FileOutputStream fos =
                     this.mContext.openFileOutput(Constants.HOSTS_FILENAME, Context.MODE_PRIVATE)) {
            // Write default localhost as hosts file
            String localhost = Constants.LOCALHOST_IPv4 + " " + Constants.LOCALHOST_HOSTNAME
                    + Constants.LINE_SEPARATOR + Constants.LOCALHOST_IPv6 + " "
                    + Constants.LOCALHOST_HOSTNAME;
            fos.write(localhost.getBytes());
            fos.close();
            // Get hosts file target based on preferences
            String applyMethod = PreferenceHelper.getApplyMethod(mContext);
            String target;
            switch (applyMethod) {
                case "writeToSystem":
                    target = Constants.ANDROID_SYSTEM_ETC_HOSTS;
                    break;
                case "writeToDataData":
                    target = Constants.ANDROID_DATA_DATA_HOSTS;
                    break;
                case "writeToData":
                    target = Constants.ANDROID_DATA_HOSTS;
                    break;
                case "customTarget":
                    target = PreferenceHelper.getCustomTarget(mContext);
                    break;
                default:
                    throw new IllegalStateException("The apply method does not match any settings: " + applyMethod + ".");
            }
            // Copy generated hosts file to target location
            ApplyUtils.copyHostsFile(mContext, target, shell);
            // Delete generated hosts file after applying it
            this.mContext.deleteFile(Constants.HOSTS_FILENAME);
            // Set status to disabled
            HomeFragment.updateStatusDisabled(mContext);
            // Return as revert successful
            return StatusCodes.REVERT_SUCCESS;
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Unable to revert hosts file.", exception);
            // Return as revert failed
            return StatusCodes.REVERT_FAIL;
        }
    }
}
