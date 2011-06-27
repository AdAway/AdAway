package org.adaway;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SimpleCursorAdapter;

public class HostsCursorAdapter extends SimpleCursorAdapter {
    private Context context;
    private int layout;

    public HostsCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
        super(context, layout, c, from, to);
        this.context = context;
        this.layout = layout;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {

        final LayoutInflater inflater = LayoutInflater.from(context);
        View v = inflater.inflate(layout, parent, false);

        return v;
    }

    @Override
    public void bindView(View v, Context context, Cursor c) {

        CheckBox cBox = (CheckBox) v.findViewById(R.id.hosts_entry_enabled);
        if (cBox != null) {
            int idCol = c.getPosition();

            cBox.setId(idCol);

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

            cBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean check) {
                    if (check) {
                        // add to unchecked?

                    } else {

                    }
                }
            });
        }

    }

}