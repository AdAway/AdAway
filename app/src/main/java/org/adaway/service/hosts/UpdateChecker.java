package org.adaway.service.hosts;

import android.content.Context;
import android.os.AsyncTask;

import org.adaway.R;
import org.adaway.helper.ResultHelper;
import org.adaway.ui.home.HomeFragment;
import org.adaway.util.StatusCodes;

import java.lang.ref.WeakReference;

// TODO Improve with AppExecutor and LiveData
public final class UpdateChecker {
    /**
     * Private constructor.
     */
    private UpdateChecker() {

    }

    /**
     * Check if there is hosts file update.<br>
     * The check will be asynchronous.
     *
     * @param context The application context.
     */
    public static void check(Context context) {
        new UpdateChecker.CheckAsyncTask(context).execute();
    }

    /**
     * This class is an asynchronous task to check if there is hosts to update.
     *
     * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
     */
    private static class CheckAsyncTask extends AsyncTask<Void, Void, UpdateResult> {
        /**
         * A weak reference to application context.
         */
        private WeakReference<Context> mWeakContext;

        /**
         * Constructor.
         *
         * @param context The application context.
         */
        private CheckAsyncTask(Context context) {
            // Store context into weak reference to prevent memory leak
            this.mWeakContext = new WeakReference<>(context);
        }

        @Override
        protected void onPreExecute() {
            // Get context from weak reference
            Context context = this.mWeakContext.get();
            if (context == null) {
                return;
            }
            // Notify base activity
            HomeFragment.setStatusBroadcast(
                    context,
                    context.getString(R.string.status_checking),
                    context.getString(R.string.status_checking_subtitle),
                    StatusCodes.CHECKING
            );
        }

        @Override
        protected UpdateResult doInBackground(Void... params) {
            // Get context from weak reference
            Context context = this.mWeakContext.get();
            if (context == null) {
                return new UpdateResult();
            }
            return UpdateFetcher.checkForUpdates(context);
        }

        @Override
        protected void onPostExecute(UpdateResult result) {
            // Get context from weak reference
            Context context = this.mWeakContext.get();
            if (context == null) {
                return;
            }
            // Display update checked notification
            String successfulDownloads = (result.mNumberOfDownloads - result.mNumberOfFailedDownloads)
                    + "/" + result.mNumberOfDownloads;
            ResultHelper.showNotificationBasedOnResult(context, result.mCode, successfulDownloads);
        }
    }
}
