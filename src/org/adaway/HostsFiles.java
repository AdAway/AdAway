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