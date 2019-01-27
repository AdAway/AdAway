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

package org.adaway.ui.dialog;

import org.adaway.R;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

public class ActivityNotFoundDialogFragment extends DialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";
    private static final String ARG_APP_GOOGLE_PLAY_URI = "app_google_play_uri";
    private static final String ARG_APP_FDROID_QUERY = "app_fdroid_query";

    /**
     * Creates new instance of this delete file dialog fragment
     */
    public static ActivityNotFoundDialogFragment newInstance(int title, int message,
                                                             String appGooglePlayUri, String appFDroidQuery) {
        ActivityNotFoundDialogFragment frag = new ActivityNotFoundDialogFragment();
        Bundle args = new Bundle();

        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_MESSAGE, message);
        args.putString(ARG_APP_GOOGLE_PLAY_URI, appGooglePlayUri);
        args.putString(ARG_APP_FDROID_QUERY, appFDroidQuery);

        frag.setArguments(args);

        return frag;
    }

    /**
     * Creates dialog
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final FragmentActivity activity = getActivity();

        final String appGooglePlayUri = getArguments().getString(ARG_APP_GOOGLE_PLAY_URI);
        final String appFDroidQuery = getArguments().getString(ARG_APP_FDROID_QUERY);
        final int title = getArguments().getInt(ARG_TITLE);
        final int message = getArguments().getInt(ARG_MESSAGE);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setPositiveButton(R.string.button_yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent intentGooglePlay = new Intent(Intent.ACTION_VIEW);
                intentGooglePlay.setData(Uri.parse(appGooglePlayUri));

                try {
                    activity.startActivity(intentGooglePlay);
                } catch (ActivityNotFoundException e) {
                    Log.e(Constants.TAG, "No Google Play Store installed!, Trying FDroid...", e);

                    Intent intentFDroid = new Intent(Intent.ACTION_SEARCH);
                    intentFDroid.setComponent(new ComponentName("org.fdroid.fdroid",
                            "org.fdroid.fdroid.SearchResults"));
                    intentFDroid.putExtra(SearchManager.QUERY, appFDroidQuery);

                    try {
                        activity.startActivity(intentFDroid);
                    } catch (ActivityNotFoundException e2) {
                        Log.e(Constants.TAG, "No FDroid installed!", e2);
                    }
                }
            }
        });
        builder.setNegativeButton(R.string.button_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });

        builder.setTitle(title);
        builder.setMessage(message);

        return builder.create();
    }
}