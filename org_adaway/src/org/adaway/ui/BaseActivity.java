package org.adaway.ui;

import org.adaway.R;
import org.adaway.helper.WebserverHelper;
import org.adaway.util.CommandException;

import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.View;

public class BaseActivity extends FragmentActivity {
    BaseFragment mBaseFragment;

    /**
     * Instantiate View and initialize fragments for this Activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.base_activity);
        
        FragmentManager fragmentManager = getSupportFragmentManager();
        mBaseFragment = (BaseFragment) fragmentManager.findFragmentById(R.id.base_fragment);
    }

    /**
     * Set Design of ActionBar
     */
    @Override
    protected void onStart() {
        super.onStart();
        ActionBar actionBar = this.getSupportActionBar();
        actionBar.setSubtitle(R.string.app_subtitle);
    }

    /**
     * hand over onClick events, defined in layout from Activity to Fragment
     */
    public void applyOnClick(View view) {
        mBaseFragment.applyOnClick(view);
    }
    
    /**
     * hand over onClick events, defined in layout from Activity to Fragment
     */
    public void revertOnClick(View view) {
        mBaseFragment.revertOnClick(view);
    }
    
    /**
     * hand over onClick events, defined in layout from Activity to Fragment
     */
    public void startWebserver(View view) {
        try {
            WebserverHelper.startWebserver(this);
        } catch (CommandException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    /**
     * hand over onClick events, defined in layout from Activity to Fragment
     */
    public void stopWebserver(View view) {
    }
}
