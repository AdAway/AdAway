package org.adaway.ui;

import org.adaway.R;

import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;

public class BaseActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.base_activity);
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        ActionBar actionBar = this.getSupportActionBar();
        actionBar.setSubtitle(R.string.app_subtitle);
    }
}
