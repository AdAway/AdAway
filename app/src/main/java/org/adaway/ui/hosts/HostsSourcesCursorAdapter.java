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

package org.adaway.ui.hosts;

import org.adaway.R;
import org.adaway.provider.AdAwayContract.HostsSources;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

/**
 * This class is a {@link android.support.v4.widget.CursorAdapter} to bind hosts sources to list item view.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class HostsSourcesCursorAdapter extends SimpleCursorAdapter {
    /**
     * Constructor.
     *
     * @param context The application context.
     */
    HostsSourcesCursorAdapter(Context context) {
        super(context, R.layout.checkbox_list_two_entries, null, new String[]{}, new int[]{}, 0);
    }

    /**
     * Bind cursor to view using the checkboxes
     */
    @Override
    public void bindView(View v, Context context, Cursor cursor) {
        CheckBox cBox = v.findViewById(R.id.checkbox_list_checkbox);
        TextView hostnameTextView = v.findViewById(R.id.checkbox_list_text);
        TextView lastModifiedTextView = v.findViewById(R.id.checkbox_list_subtext);

        if (cBox != null) {
            // bind cursor position to tag of list item
            int cursorPosition = cursor.getPosition();
            cBox.setTag("checkbox_" + cursorPosition);
            hostnameTextView.setTag("url_" + cursorPosition);
            lastModifiedTextView.setTag("last_modified_" + cursorPosition);

            int enabledCol = cursor.getColumnIndexOrThrow(HostsSources.ENABLED);
            String enabled = cursor.getString(enabledCol);

            if (Integer.parseInt(enabled) == 1) {
                cBox.setChecked(true);
            } else {
                cBox.setChecked(false);
            }

            // set hostname
            int urlCol = cursor.getColumnIndex(HostsSources.URL);
            String url = cursor.getString(urlCol);
            hostnameTextView.setText(url);

            // set last modified
            int lastModifiedLocalCol = cursor.getColumnIndexOrThrow(HostsSources.LAST_MODIFIED_LOCAL);
            long lastModifiedLocal = cursor.getLong(lastModifiedLocalCol);
            int lastModifiedOnlineCol = cursor.getColumnIndexOrThrow(HostsSources.LAST_MODIFIED_ONLINE);
            long lastModifiedOnline = cursor.getLong(lastModifiedOnlineCol);

            int modifiedText;
            if (lastModifiedLocal == 0 || lastModifiedOnline == 0) {
                modifiedText = R.string.hosts_source_unknown_status;
            } else if (lastModifiedLocal < lastModifiedOnline) {
                modifiedText = R.string.hosts_source_update_available;
            } else {
                modifiedText = R.string.hosts_source_up_to_date;
            }
            lastModifiedTextView.setText(modifiedText);

        }
    }
}