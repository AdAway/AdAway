package org.adaway;

import java.util.ArrayList;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;

public class HostsArrayAdapter extends ArrayAdapter<String> {
    private Context mContext;
    private ArrayList<String> mCheckList = new ArrayList<String>();

    public HostsArrayAdapter(Context context, int textViewResourceId, ArrayList<String> checkList) {
        super(context, textViewResourceId, checkList);
        this.mContext = context;
        this.mCheckList = checkList;
    }

    public View getView(int pos, View inView, ViewGroup parent) {
        View v = inView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.check_list, null);
        }
        
        
//        this.c.moveToPosition(pos);
//        String bookmark = this.c.getString(this.c.getColumnIndex(Browser.BookmarkColumns.TITLE));
        
        // ONLY TEST DATA:
        String hostname = "test";
        CheckBox cBox = (CheckBox) v.findViewById(R.id.bcheck);
        cBox.setTag(1);
        cBox.setText(hostname);
        cBox.setChecked(true);
        
        return (v);
        
//        cBox.setTag(Integer.parseInt(this.c.getString(this.c.getColumnIndex(Browser.BookmarkColumns._ID))));
//        cBox.setText(bookmark);
//        if (Integer.parseInt(this.c.getString(this.c.getColumnIndex(Browser.BookmarkColumns._ID))) % 2 != 0) {
//            cBox.setChecked(true);
//        }
//        return (v);
    }

}
