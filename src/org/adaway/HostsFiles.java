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

package org.adaway;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;

public class HostsFiles extends ListActivity {

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.hosts_files);

        HostsDatabase db = new HostsDatabase(this);
        Cursor cur = db.getHostsCursor();

        String[] displayFields = new String[] { "url" };
        int[] displayViews = new int[] { R.id.hosts_entry_enabled };
        setListAdapter(new HostsCursorAdapter(this, R.layout.hosts_files_entry, cur, displayFields,
                displayViews));
    }

}