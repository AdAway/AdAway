package org.adaway.ui.home;

import android.app.Activity;
import android.os.AsyncTask;
import android.support.annotation.Nullable;

import org.adaway.helper.PreferenceHelper;
import org.adaway.service.hosts.UpdateChecker;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.Utils;

import java.lang.ref.WeakReference;

/**
 * This class is an asynchronous task to update hosts status of the {@link HomeFragment}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateHostsStatusAsyncTask extends AsyncTask<Void, Void, Boolean> {
    /**
     * A reference to the {@link HomeFragment} to update.
     */
    private final WeakReference<HomeFragment> homeFragmentReference;

    /**
     * Constructor.
     *
     * @param homeFragment The home fragment.
     */
    UpdateHostsStatusAsyncTask(HomeFragment homeFragment) {
        // Store weak reference to home fragment
        this.homeFragmentReference = new WeakReference<>(homeFragment);
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        // Get activity
        Activity activity = this.getActivity();
        if (activity == null) {
            return true; // Suppose to be root
        }
        // Check if Android is rooted
        if (!Utils.isAndroidRooted()) {
            return false;
        }
        // Check if hosts was file installed by AdAway
        if (ApplyUtils.isHostsFileCorrect(Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
            // Notify AdAway is enabled
            HomeFragment.updateStatusEnabled(activity);
            // Check preferences if application must check updates on startup
            if (PreferenceHelper.getUpdateCheck(activity)) {
                // Check updates
                UpdateChecker.check(activity); // TODO Already in an AsyncTask. Possible improvement?
            }
        } else {
            // Otherwise notify AdAway is disabled
            HomeFragment.updateStatusDisabled(activity);
        }
        // Return device is well rooted
        return true;
    }

    @Override
    protected void onPostExecute(Boolean rooted) {
        // Get activity
        Activity activity = this.getActivity();
        // Display warning if not rooted
        if (activity != null && !rooted) {
            Utils.displayNoRootDialog(activity);
        }
    }

    /**
     * Get the current activity.
     *
     * @return The current activity, <code>null</code> if recycled.
     */
    // SHOULD USE OPTIONAL WHEN AVAILABLE IN API
    @Nullable
    private Activity getActivity() {
        // Get the home fragment
        HomeFragment homeFragment = this.homeFragmentReference.get();
        if (homeFragment == null) {
            return null;
        }
        // Get the current activity
        return homeFragment.getActivity();
    }
}
