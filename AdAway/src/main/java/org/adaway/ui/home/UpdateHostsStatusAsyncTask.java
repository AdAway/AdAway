package org.adaway.ui.home;

import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;

import org.adaway.helper.PreferenceHelper;
import org.adaway.service.UpdateService;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.Utils;

import java.lang.ref.WeakReference;

/**
 * This class is an asynchronous task to update hosts status of the {@link HomeFragment}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateHostsStatusAsyncTask extends AsyncTask<Void, Void, Void> {
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
    protected Void doInBackground(Void... voids) {
        // Get the home fragment
        HomeFragment homeFragment = this.homeFragmentReference.get();
        if (homeFragment == null) {
            return null;
        }
        // Get the current activity
        FragmentActivity activity = homeFragment.getActivity();
        if (activity == null) {
            return null;
        }
        // Check if Android is rooted
        if (!Utils.isAndroidRooted(activity)) {
            return null;
        }
        // Check if hosts was file installed by AdAway
        if (ApplyUtils.isHostsFileCorrect(Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
            // Notify AdAway is enabled
            HomeFragment.updateStatusEnabled(activity);
            // Check preferences if application must check updates on startup
            if (PreferenceHelper.getUpdateCheck(activity)) {
                // Check updates
                UpdateService.checkAsync(activity); // TODO Already in an AsyncTask. Possible improvement?
            }
        } else {
            // Otherwise notify AdAway is disabled
            HomeFragment.updateStatusDisabled(activity);
        }
        // Return void
        return null;
    }
}
