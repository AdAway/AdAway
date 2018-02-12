package org.adaway.ui.home;

import android.app.Activity;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;

import org.adaway.helper.PreferenceHelper;
import org.adaway.service.UpdateService;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.Utils;
import org.adaway.util.WebserverUtils;

import java.lang.ref.WeakReference;

/**
 * This class is an asynchronous task to update statuses of the {@link HomeFragment}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateStatusAsyncTask extends AsyncTask<UpdateStatusAsyncTask.UpdateStatus, Void, Void> {
    /**
     * A reference to the {@link HomeFragment} to update.
     */
    private final WeakReference<HomeFragment> homeFragmentReference;

    /**
     * Constructor.
     *
     * @param homeFragment The home fragment.
     */
    UpdateStatusAsyncTask(HomeFragment homeFragment) {
        // Store weak reference to home fragment
        this.homeFragmentReference = new WeakReference<>(homeFragment);
    }

    @Override
    protected Void doInBackground(UpdateStatus... updateStatuses) {
        // Get the home fragment
        HomeFragment homeFragment = this.homeFragmentReference.get();
        if (homeFragment == null) {
            return null;
        }
        // Check update status to do
        for (UpdateStatus updateStatus : updateStatuses) {
            switch (updateStatus) {
                case HOSTS:
                    // Get the current activity
                    FragmentActivity activity = homeFragment.getActivity();
                    if (activity == null) {
                        continue;
                    }
                    // Check hosts status
                    this.checkHostStatus(activity);
                    break;
                case WEB_SERVER:
                    // Check web server status
                    this.checkWebServerRunning(homeFragment);
                    break;
            }
        }
        // Return void
        return null;
    }

    /**
     * Check hosts status.
     *
     * @param activity The current activity.
     */
    private void checkHostStatus(Activity activity) {
        // Check if Android is rooted
        if (!Utils.isAndroidRooted(activity)) {
            return;
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
    }

    /**
     * Check web server status.
     *
     * @param homeFragment The home fragment.
     */
    private void checkWebServerRunning(HomeFragment homeFragment) {
        boolean webServerRunning = WebserverUtils.isWebServerRunning();
        homeFragment.notifyWebServerRunning(webServerRunning);
    }

    /**
     * This enumerate represents the statuses of the {@link HomeFragment} to update.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    enum UpdateStatus {
        WEB_SERVER,
        HOSTS
    }
}
