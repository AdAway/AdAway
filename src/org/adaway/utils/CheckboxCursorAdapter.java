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

package org.adaway.utils;

import org.adaway.R;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.SimpleCursorAdapter;

public class CheckboxCursorAdapter extends SimpleCursorAdapter {
    // private Context context;
    private int layout;

    public CheckboxCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
        super(context, layout, c, from, to);
        // this.context = context;
        this.layout = layout;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        final LayoutInflater inflater = LayoutInflater.from(context);
        View v = inflater.inflate(layout, parent, false);

        return v;
    }

    /**
     * Bind cursor to view using the checkboxes
     */
    @Override
    public void bindView(View v, Context context, Cursor c) {
        CheckBox cBox = (CheckBox) v.findViewById(R.id.checkbox_list_enabled);
        if (cBox != null) {
            // bind cursor position to tag of list item
            int cursorPosition = c.getPosition();
            cBox.setTag(cursorPosition);

            int enabledCol = c.getColumnIndex("enabled");
            String enabled = c.getString(enabledCol);

            if (Integer.parseInt(enabled) == 1) {
                cBox.setChecked(true);
            } else {
                cBox.setChecked(false);
            }

            int urlCol = c.getColumnIndex("url");
            String url = c.getString(urlCol);

            cBox.setText(url);
        }
    }

}