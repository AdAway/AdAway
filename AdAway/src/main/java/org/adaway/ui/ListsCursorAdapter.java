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

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import org.adaway.R;
import org.adaway.provider.AdAwayContract.Blacklist;

/**
 * This class is a {@link android.support.v4.widget.CursorAdapter} to provide blacklist and whitelist items.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class ListsCursorAdapter extends SimpleCursorAdapter {
    /** The enabled status checkbox tag. */
    static final String ENABLED_CHECKBOX_TAG = "enabled";
    /** The hostname value text view tag. */
    static final String HOSTNAME_TEXTVIEW_TAG = "hostname";

    /**
     * Constructor.
     *
     * @param context The activity context.
     */
    ListsCursorAdapter(Context context) {
        super(
                context,
                R.layout.checkbox_list_entry,
                null,                         // No cursor to reuse
                new String[]{},               // Column to field are handled by this implementation
                new int[]{},                  // Column to field are handled by this implementation
                0                             // No flag
        );
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Get the checkbox
        CheckBox checkBox = view.findViewById(R.id.checkbox_list_checkbox);
        if (checkBox != null) {
            // Get enabled value
            int enabledColumnIndex = cursor.getColumnIndexOrThrow(Blacklist.ENABLED);
            String enabled = cursor.getString(enabledColumnIndex);
            // Enable checkbox according enabled value
            if (Integer.parseInt(enabled) == 1) {
                checkBox.setChecked(true);
            } else {
                checkBox.setChecked(false);
            }
            // Set tag to find view in fragment
            checkBox.setTag(ListsCursorAdapter.ENABLED_CHECKBOX_TAG);
        }
        // Get text view
        TextView textView = view.findViewById(R.id.checkbox_list_text);
        if (textView != null) {
            // Get hostname value
            int hostnameColumnIndex = cursor.getColumnIndexOrThrow(Blacklist.HOSTNAME);
            String hostname = cursor.getString(hostnameColumnIndex);
            // Set hostname text
            textView.setText(hostname);
            // Set tag to find view in fragment
            textView.setTag(ListsCursorAdapter.HOSTNAME_TEXTVIEW_TAG);
        }
    }
}