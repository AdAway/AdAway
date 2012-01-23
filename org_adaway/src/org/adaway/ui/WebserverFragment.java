package org.adaway.ui;

import org.adaway.R;
import org.adaway.util.WebserverUtils;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ToggleButton;

public class WebserverFragment extends Fragment {
    private Activity mActivity;

    private ToggleButton mWebserverToggle;

    /**
     * Inflate the layout for this fragment
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.webserver_fragment, container, false);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = getActivity();

        mWebserverToggle = (ToggleButton) mActivity
                .findViewById(R.id.webserver_fragment_toggle_button);

        // install webserver if not already there
        WebserverUtils.updateWebserver(mActivity);
        // set togglebutton checked if webserver is running
        if (WebserverUtils.isWebserverRunning()) {
            mWebserverToggle.setChecked(true);
        } else {
            mWebserverToggle.setChecked(false);
        }
    }

    /**
     * Button Action to start or stop webserver
     * 
     * @param view
     */
    public void webserverOnClick(View view) {
        if (mWebserverToggle.isChecked() == true) {
            WebserverUtils.startWebserver(mActivity);
        }
        if (mWebserverToggle.isChecked() == false) {
            WebserverUtils.stopWebserver(mActivity);
        }
    }
}
