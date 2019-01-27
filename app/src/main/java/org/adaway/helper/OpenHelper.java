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

import org.adaway.R;
import org.adaway.ui.dialog.ActivityNotFoundDialogFragment;
import org.adaway.util.Constants;
import org.adaway.util.Log;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.Toolbox;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;

public class OpenHelper {

    /**
     * Open hosts file with default text app
     *
     * @param activity
     */
    public static void openHostsFile(FragmentActivity activity) {
        try {
            Shell rootShell = Shell.startRootShell();

            Toolbox tb = new Toolbox(rootShell);
            /* remount for write access */
            if (tb.remount(Constants.ANDROID_SYSTEM_ETC_HOSTS, "RW")) {
                openFileWithEditor(activity, Constants.ANDROID_SYSTEM_ETC_HOSTS);
            } else {
                Log.e(Constants.TAG, "System partition could not be remounted as rw!");
            }

            rootShell.close();
        } catch (Exception e) {
            Log.e(Constants.TAG, "Problem with root shell!", e);
        }
    }

    /**
     * Open default app for opening plain text files
     *
     * @param activity
     * @param file
     */
    private static void openFileWithEditor(final FragmentActivity activity, String file) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        Uri uri = Uri.parse("file://" + file);
        intent.setDataAndType(uri, "text/plain");

        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            ActivityNotFoundDialogFragment notFoundDialog = ActivityNotFoundDialogFragment
                    .newInstance(R.string.no_text_editor_title, R.string.no_text_editor,
                            "market://details?id=jp.sblo.pandora.jota", "Text Edit");

            notFoundDialog.show(activity.getSupportFragmentManager(), "notFoundDialog");
        }
    }

}
