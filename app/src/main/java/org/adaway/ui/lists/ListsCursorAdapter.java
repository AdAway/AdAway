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

package org.adaway.ui.lists;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import org.adaway.R;
import org.adaway.provider.AdAwayContract;

/**
 * This class is a {@link android.support.v4.widget.CursorAdapter} to provide list items.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class ListsCursorAdapter extends SimpleCursorAdapter {
    /**
     * The enabled status checkbox tag.
     */
    static final String ENABLED_CHECKBOX_TAG = "enabled";
    /**
     * The hostname value text view tag.
     */
    static final String HOSTNAME_TEXTVIEW_TAG = "hostname";
    /**
     * The IP address value text view tag.
     */
    static final String IP_TEXTVIEW_TAG = "ip";

    /**
     * Constructor.
     *
     * @param context The activity context.
     * @param layout  The view layout.
     */
    ListsCursorAdapter(Context context, int layout) {
        super(
                context,
                layout,
                null,                         // No cursor to reuse
                new String[]{},               // Column to field are handled by this implementation
                new int[]{},                  // Column to field are handled by this implementation
                0                             // No flag
        );
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Get enabled checkbox
        CheckBox enabledCheckBox = view.findViewById(R.id.checkbox_list_checkbox);
        if (enabledCheckBox != null) {
            // Get enabled value
            int enabledColumnIndex = cursor.getColumnIndexOrThrow(AdAwayContract.ListColumns.ENABLED);
            String enabled = cursor.getString(enabledColumnIndex);
            // Enable checkbox according enabled value
            if (Integer.parseInt(enabled) == 1) {
                enabledCheckBox.setChecked(true);
            } else {
                enabledCheckBox.setChecked(false);
            }
            // Set tag to find view in fragment
            enabledCheckBox.setTag(ListsCursorAdapter.ENABLED_CHECKBOX_TAG);
        }
        // Get hostname text view
        TextView hostnameTextView = view.findViewById(R.id.checkbox_list_text);
        if (hostnameTextView != null) {
            // Get hostname value
            int hostnameColumnIndex = cursor.getColumnIndexOrThrow(AdAwayContract.ListColumns.HOSTNAME);
            String hostname = cursor.getString(hostnameColumnIndex);
            // Set hostname text
            hostnameTextView.setText(hostname);
            // Set tag to find view in fragment
            hostnameTextView.setTag(ListsCursorAdapter.HOSTNAME_TEXTVIEW_TAG);
        }
        // Get IP text view
        TextView ipTextView = view.findViewById(R.id.checkbox_list_subtext);
        if (ipTextView != null) {
            // Get ip value
            int hostnameColumnIndex = cursor.getColumnIndexOrThrow(AdAwayContract.RedirectionList.IP);
            String ip = cursor.getString(hostnameColumnIndex);
            // Set ip text
            ipTextView.setText(ip);
            // Set tag to find view in fragment
            ipTextView.setTag(ListsCursorAdapter.IP_TEXTVIEW_TAG);
        }
    }
}