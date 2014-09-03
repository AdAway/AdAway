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

package org.adaway.util;

import org.adaway.R;
import org.adaway.provider.AdAwayContract.Blacklist;
import org.adaway.provider.AdAwayContract.Whitelist;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.widget.CheckBox;

public class CheckboxCursorAdapter extends SimpleCursorAdapter {

    public CheckboxCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to,
                                 int flags) {
        super(context, layout, c, from, to, flags);
    }

    /**
     * Bind cursor to view using the checkboxes
     */
    @Override
    public void bindView(View v, Context context, Cursor cursor) {
        CheckBox cBox = (CheckBox) v.findViewById(R.id.checkbox_list_checkbox);
        if (cBox != null) {
            // bind cursor position to tag of list item
            int cursorPosition = cursor.getPosition();
            cBox.setTag(cursorPosition);

            // can also be used for Blacklist
            int enabledCol = cursor.getColumnIndexOrThrow(Whitelist.ENABLED);
            String enabled = cursor.getString(enabledCol);

            if (Integer.parseInt(enabled) == 1) {
                cBox.setChecked(true);
            } else {
                cBox.setChecked(false);
            }

            // can also be used for Blacklist
            int hostnameCol = cursor.getColumnIndexOrThrow(Blacklist.HOSTNAME);
            String hostname = cursor.getString(hostnameCol);

            cBox.setText(hostname);
        }
    }

}