package org.adaway.service.hosts;

import android.content.Context;
import android.support.annotation.NonNull;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.util.ApplyUtils;
import org.adaway.util.Constants;
import org.adaway.util.DateUtils;
import org.adaway.util.Log;
import org.adaway.util.StatusCodes;
import org.adaway.util.Utils;

import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

/**
 * This class fetches hosts sources last online update.<br>
 * It update source update time in database and return global update needed status.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class UpdateFetcher {
    /**
     * Private constructor.
     */
    private UpdateFetcher() {

    }

    /**
     * Check for updates of hosts sources.
     *
     * @param context The application context.
     * @return return code (from {@link StatusCodes}).
     */
    @NonNull
    public static UpdateResult checkForUpdates(Context context) {
        boolean updateAvailable = false;

        // Declare update result
        UpdateResult updateResult = new UpdateResult();
        // Check current connection
        if (!Utils.isAndroidOnline(context)) {
            updateResult.mCode = StatusCodes.NO_CONNECTION;
            return updateResult;
        }

        // Get enabled sources
        AppDatabase database = AppDatabase.getInstance(context);
        HostsSourceDao hostsSourceDao = database.hostsSourceDao();
        for (HostsSource source : hostsSourceDao.getEnabled()) {
            // Increase number of downloads
            updateResult.mNumberOfDownloads++;

            // Get URL and lastModified from db
            String currentUrl = source.getUrl();
            Date currentLastModifiedLocal = source.getLastLocalModification();

            try {
                Log.v(Constants.TAG, "Checking hosts file: " + currentUrl);

                /* build connection */
                URL mURL = new URL(currentUrl);
                URLConnection connection = mURL.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);

                Date currentLastModifiedOnline = new Date(connection.getLastModified());

                Log.d(Constants.TAG,
                        "mConnectionLastModified: "
                                + currentLastModifiedOnline
                                + " ("
                                + DateUtils.dateToString(context, currentLastModifiedOnline)
                                + ")"
                );

                Log.d(Constants.TAG,
                        "mCurrentLastModified: "
                                + (currentLastModifiedLocal == null ? "not defined" : currentLastModifiedLocal)
                                + " ("
                                + DateUtils.dateToString(context, currentLastModifiedLocal)
                                + ")"
                );

                // Check if file is available
                connection.connect();
                connection.getInputStream().close();

                // Check if update is available for this hosts file
                if (currentLastModifiedLocal == null ||
                        currentLastModifiedOnline.after(currentLastModifiedLocal)) {
                    updateAvailable = true;
                }

                // Save last modified online for later viewing in list
                hostsSourceDao.updateOnlineModificationDate(currentUrl, currentLastModifiedOnline);
            } catch (Exception exception) {
                Log.e(Constants.TAG, "Exception while downloading from " + currentUrl, exception);

                // Increase number of failed download
                updateResult.mNumberOfFailedDownloads++;

                // Set last_modified_online of failed download to 0 (not available)
                hostsSourceDao.updateOnlineModificationDate(currentUrl, null);
            }
        }

        // Check if all downloads failed
        if (updateResult.mNumberOfDownloads == updateResult.mNumberOfFailedDownloads &&
                updateResult.mNumberOfDownloads != 0) {
            // Return download fails
            updateResult.mCode = StatusCodes.DOWNLOAD_FAIL;
            return updateResult;
        }
        // Check if update is available
        if (updateAvailable) {
            updateResult.mCode = StatusCodes.UPDATE_AVAILABLE;
        }
        // Otherwise, return current status
        else if (ApplyUtils.isHostsFileCorrect(Constants.ANDROID_SYSTEM_ETC_HOSTS)) {
            updateResult.mCode = StatusCodes.ENABLED;
        } else {
            updateResult.mCode = StatusCodes.DISABLED;
        }
        Log.d(Constants.TAG, "Update check result: " + updateResult);
        return updateResult;
    }
}
