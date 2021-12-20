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

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.StringRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;

import timber.log.Timber;

/**
 * This class is an utility class to help install missing applications.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class MissingAppDialog {
    /**
     * Show a dialog to install a text editor.
     *
     * @param context The application context.
     */
    public static void showTextEditorMissingDialog(Context context) {
        showMissingAppDialog(
                context,
                R.string.no_text_editor_title,
                R.string.no_text_editor,
                "market://details?id=jp.sblo.pandora.jota",
                "Text Edit"
        );
    }

    /**
     * Show a dialog to install a file manager.
     *
     * @param context The application context.
     */
    public static void showFileManagerMissingDialog(Context context) {
        showMissingAppDialog(
                context,
                R.string.no_file_manager_title,
                R.string.no_file_manager,
                "market://details?id=org.openintents.filemanager",
                "OI File Manager"
        );
    }

    private static void showMissingAppDialog(
            Context context,
            @StringRes int title,
            @StringRes int message,
            String appGooglePlayUri,
            String appFdroidQuery
    ) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.button_yes, (dialog, id) -> {
                    Intent intentGooglePlay = new Intent(Intent.ACTION_VIEW);
                    intentGooglePlay.setData(Uri.parse(appGooglePlayUri));

                    try {
                        context.startActivity(intentGooglePlay);
                    } catch (ActivityNotFoundException e) {
                        Timber.e(e, "No Google Play Store installed!, Trying FDroid...");

                        Intent intentFDroid = new Intent(Intent.ACTION_SEARCH);
                        intentFDroid.setComponent(new ComponentName("org.fdroid.fdroid",
                                "org.fdroid.fdroid.SearchResults"));
                        intentFDroid.putExtra(SearchManager.QUERY, appFdroidQuery);

                        try {
                            context.startActivity(intentFDroid);
                        } catch (ActivityNotFoundException e2) {
                            Timber.e(e2, "No FDroid installed!");
                        }
                    }
                })
                .setNegativeButton(R.string.button_no, (dialog, id) -> dialog.dismiss())
                .create()
                .show();
    }
}
