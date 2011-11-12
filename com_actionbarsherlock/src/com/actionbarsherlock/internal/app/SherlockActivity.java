package com.actionbarsherlock.internal.app;

import android.support.v4.view.ActionMode;
import android.support.v4.view.MenuItem;

/**
 * Required callbacks for an activity to work with ActionBarSherlock.
 */
public interface SherlockActivity {
    void onActionModeFinished(ActionMode mode);
    void onActionModeStarted(ActionMode mode);
    boolean onOptionsItemSelected(MenuItem item);
}
