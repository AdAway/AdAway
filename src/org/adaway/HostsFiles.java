package org.adaway;

import java.util.ArrayList;
import java.util.List;

import android.app.ListActivity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import android.app.ListActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;

public class HostsFiles extends ListActivity {
    String[] listItems = { "exploring", "android", "list", "activities" };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hostname_files);
        
        ArrayList<String> test = new ArrayList<String>();
        test.add("test");
        
        setListAdapter(new HostsArrayAdapter(this, R.layout.hostname_files, test));
    }
}