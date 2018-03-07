package org.adaway.ui.home;

import android.os.AsyncTask;

import org.adaway.util.WebServerUtils;

import java.lang.ref.WeakReference;

/**
 * This class is an asynchronous task to update web server status of the {@link HomeFragment}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateWebServerStatusAsyncTask extends AsyncTask<Void, Void, Boolean> {
    /**
     * A reference to the {@link HomeFragment} to update.
     */
    private final WeakReference<HomeFragment> homeFragmentReference;

    /**
     * Constructor.
     *
     * @param homeFragment The home fragment.
     */
    UpdateWebServerStatusAsyncTask(HomeFragment homeFragment) {
        // Store weak reference to home fragment
        this.homeFragmentReference = new WeakReference<>(homeFragment);
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        // Check web server status
        return WebServerUtils.isWebServerRunning();
    }

    @Override
    protected void onPostExecute(Boolean isWebServerRunning) {
        // Get the home fragment
        HomeFragment homeFragment = this.homeFragmentReference.get();
        if (homeFragment == null) {
            return;
        }
        // Update home fragment
        homeFragment.notifyWebServerRunning(isWebServerRunning);
    }
}
