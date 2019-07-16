package org.adaway.model.source;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.git.GitHostsSource;
import org.adaway.util.AppExecutors;
import org.adaway.util.Constants;
import org.adaway.util.DateUtils;
import org.adaway.util.Log;
import org.adaway.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Observable;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static org.adaway.model.error.HostError.DOWNLOAD_FAIL;
import static org.adaway.model.error.HostError.NO_CONNECTION;

/**
 * This class is the model to represent hosts source management.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class SourceModel extends Observable {
    /**
     * The log tag.
     */
    private static final String TAG = "SourceModel";
    /**
     * The HTTP client cache size.
     */
    private static final long CACHE_SIZE = 100L * 1024L * 1024L;
    /**
     * The application context.
     */
    private final Context context;
    /**
     * The {@link HostsSource} DAO.
     */
    private final HostsSourceDao hostsSourceDao;
    /**
     * The {@link HostListItem} DAO.
     */
    private final HostListItemDao hostListItemDao;
    /**
     * The update available status.
     */
    private MutableLiveData<Boolean> updateAvailable;
    /**
     * The model state.
     */
    private String state;
//    /**
//     * The model detailed state.
//     */
//    private String detailedState;
    /**
     * The HTTP client to download hosts sources ({@code null} until initialized by {@link #getHttpClient()}).
     */
    private OkHttpClient httpClient;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public SourceModel(Context context) {
        this.context = context;
        AppDatabase database = AppDatabase.getInstance(this.context);
        this.hostsSourceDao = database.hostsSourceDao();
        this.hostListItemDao = database.hostsListItemDao();
        this.state = "";
        this.updateAvailable = new MutableLiveData<>();
        this.updateAvailable.setValue(false);
        checkUpdateAtStartUp();
    }

    /**
     * Get the model state.
     *
     * @return The model state.
     */
    public String getState() {
        return this.state;
    }

    /**
     * Get the update available status.
     *
     * @return {@code true} if source update is available, {@code false} otherwise.
     */
    public LiveData<Boolean> isUpdateAvailable() {
        return this.updateAvailable;
    }

//    /**
//     * Get the model detailed state.
//     *
//     * @return The model detailed state.
//     */
//    public String getDetailedState() {
//        return this.detailedState;
//    }

    private void checkUpdateAtStartUp() {
        boolean checkUpdateAtStartup = PreferenceHelper.getUpdateCheck(this.context);
        if (checkUpdateAtStartup) {
            AppExecutors.getInstance().networkIO().execute(() -> {
                try {
                    checkForUpdate();
                } catch (HostErrorException exception) {
                    Log.w(TAG, "Fail to check update at startup.", exception);
                }
            });
        }
    }

    /**
     * Check if there is update available in hosts sources.
     *
     * @throws HostErrorException If the hosts sources could not be checked.
     */
    public boolean checkForUpdate() throws HostErrorException {
        HostsSourceDao hostsSourceDao = AppDatabase.getInstance(this.context).hostsSourceDao();
        // Check current connection
        if (!Utils.isAndroidOnline(this.context)) {
            throw new HostErrorException(NO_CONNECTION);
        }
        // Initialize update status
        boolean updateAvailable = false;
        boolean anyHostsSourceVerified = false;
        // Get hosts sources
        List<HostsSource> sources = hostsSourceDao.getEnabled();
        if (sources.isEmpty()) {
            // Return no update as no source
            this.updateAvailable.postValue(false);
            return false;
        }
        // Update state
        setStateAndDetails(R.string.status_checking, R.string.status_checking);
        // Check each source
        for (HostsSource source : sources) {
            // Get URL and lastModified from db
            String sourceUrl = source.getUrl();
            Date lastModifiedLocal = source.getLastLocalModification();
            // Update state
            setStateAndDetails(R.string.status_checking, sourceUrl);
            // Get hosts source last update
            Date lastModifiedOnline = getHostsSourceLastUpdate(sourceUrl);
            // Some help with debug here
            Log.d(Constants.TAG, "lastModifiedLocal: "
                    + (lastModifiedLocal == null ? "not defined" : lastModifiedLocal)
                    + " (" + DateUtils.dateToString(this.context, lastModifiedLocal) + ")"
            );
            Log.d(Constants.TAG, "lastModifiedOnline: "
                    + (lastModifiedOnline == null ? "not defined" : lastModifiedOnline)
                    + " (" + DateUtils.dateToString(this.context, lastModifiedOnline) + ")"
            );
            // Save last modified online
            hostsSourceDao.updateOnlineModificationDate(sourceUrl, lastModifiedOnline);
            // Check if last modified online retrieved
            if (lastModifiedOnline != null) {
                anyHostsSourceVerified = true;
                // Check if update is available for this source and source enabled
                if (source.isEnabled() && (lastModifiedLocal == null || lastModifiedOnline.after(lastModifiedLocal))) {
                    updateAvailable = true;
                }
            }
        }
        // Check if any hosts source was verified
        if (!anyHostsSourceVerified) {
            throw new HostErrorException(DOWNLOAD_FAIL);
        }
        // Check if update is available
        if (updateAvailable) {
            setStateAndDetails(R.string.status_update_available, R.string.status_update_available_subtitle);
        } else {
            setStateAndDetails(R.string.status_enabled, R.string.status_enabled_subtitle);
        }
        Log.d(Constants.TAG, "Update check result: " + updateAvailable);
        this.updateAvailable.postValue(updateAvailable);
        return updateAvailable;
    }

    /**
     * Get the hosts source last online update.
     *
     * @param url The hosts source URL to get last online update.
     * @return The last online date, {@code null} if the date could not be retrieved.
     */
    @Nullable
    private Date getHostsSourceLastUpdate(String url) {
        Log.v(Constants.TAG, "Checking hosts file: " + url);
        // Check Git hosting
        if (GitHostsSource.isHostedOnGit(url)) {
            try {
                return GitHostsSource.getSource(url).getLastUpdate();
            } catch (MalformedURLException exception) {
                Log.w(Constants.TAG, "Failed to get GitHub last update for url " + url + ".", exception);
                return null;
            }
        }
        // Default hosting
        URLConnection connection = null;
        try {
            /* build connection */
            URL mURL = new URL(url);
            connection = mURL.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            long lastModified = connection.getLastModified();
            return new Date(lastModified);
        } catch (Exception exception) {
            Log.e(Constants.TAG, "Exception while downloading from " + url, exception);
            return null;
        } finally {
            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection).disconnect();
            }
        }
    }

    /**
     * Retrieve all hosts sources files to copy into a private local file.
     *
     * @throws HostErrorException If the hosts sources could not be downloaded.
     */
    public void retrieveHostsSources() throws HostErrorException {
        // Check connection status
        if (!Utils.isAndroidOnline(this.context)) {
            throw new HostErrorException(NO_CONNECTION);
        }
        // Update state to downloading
        setStateAndDetails(R.string.download_dialog, "");
        // Initialize copy counters
        int numberOfCopies = 0;
        int numberOfFailedCopies = 0;
        // Get each hosts source
        for (HostsSource hostsSource : this.hostsSourceDao.getEnabled()) {
            // Increment number of copy
            numberOfCopies++;
            boolean copySuccess = false;
            try {
                // Check hosts source protocol
                String url = hostsSource.getUrl();
                String protocol = new URL(url).getProtocol();
                switch (protocol) {
                    case "https":
                        copySuccess = downloadHostSource(hostsSource);
                        break;
                    case "file":
                        copySuccess = copyHostSourceFile(hostsSource);
                        break;
                    default:
                        Log.w(Constants.TAG, "Hosts source protocol " + protocol + " is not supported.");
                }
            } catch (IOException exception) {
                Log.w(Constants.TAG, "Failed to retrieve host source " + hostsSource.getUrl() + ".", exception);
            }
            if (!copySuccess) {
                // Increment number of failed copy
                numberOfFailedCopies++;
            }
        }
        // Check if all copies failed
        if (numberOfCopies == numberOfFailedCopies && numberOfCopies != 0) {
            throw new HostErrorException(DOWNLOAD_FAIL);
        }
        // Mark no update available
        this.updateAvailable.postValue(false);
    }

    /**
     * Get the HTTP client to download hosts sources.
     *
     * @return The HTTP client to download hosts sources.
     */
    @NonNull
    private OkHttpClient getHttpClient() {
        if (this.httpClient == null) {
            this.httpClient = new OkHttpClient.Builder()
                    .cache(new Cache(this.context.getCacheDir(), CACHE_SIZE))
                    .build();
        }
        return this.httpClient;
    }

    /**
     * Download an hosts source file and append it to a private file.
     *
     * @param hostsSource The hosts source to download.
     * @return {@code true} if the hosts was successfully downloaded, {@code false} otherwise.
     */
    private boolean downloadHostSource(HostsSource hostsSource) {
        // Get hosts file URL
        String hostsFileUrl = hostsSource.getUrl();
        Log.v(Constants.TAG, "Downloading hosts file: " + hostsFileUrl);
        // Set state to downloading hosts source
        setStateAndDetails(R.string.download_dialog, hostsFileUrl);
        // Get HTTP client
        OkHttpClient httpClient = getHttpClient();
        // Create request
        Request request = new Request.Builder()
                .url(hostsFileUrl)
                .build();
        // Request hosts file and open byte stream
        try (Response response = httpClient.newCall(request).execute();
             InputStream inputStream = response.body().byteStream()) {
            parseSourceInputStream(hostsSource, inputStream);
            // Save last modified online for later use
            String lastModifiedHeader = response.header("Last-Modified");
            if (lastModifiedHeader != null) {
                try {
                    // Parse last modified date
                    SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                    Date lastModified = format.parse(lastModifiedHeader);
                    // Update last_modified_online (null if not available or error happened)
                    this.hostsSourceDao.updateOnlineModificationDate(hostsFileUrl, lastModified);
                } catch (ParseException exception) {
                    Log.w(Constants.TAG, "Failed to parse Last-Modified header from " + hostsFileUrl + ": " + lastModifiedHeader + ".", exception);
                }
            }
        } catch (IOException exception) {
            Log.e(Constants.TAG, "Exception while downloading hosts file from " + hostsFileUrl + ".", exception);
            // Return download failed
            return false;
        }
        // Return download successful
        return true;
    }

    /**
     * Copy a hosts source file and append it to a private file.
     *
     * @param hostsSource The hosts source to download.
     * @return {@code true} if the hosts was successfully downloaded, {@code false} otherwise.
     */
    private boolean copyHostSourceFile(HostsSource hostsSource) {
        // Get hosts file URL
        String hostsFileUrl = hostsSource.getUrl();
        Log.v(Constants.TAG, "Copying hosts source file: " + hostsFileUrl);
        // Set state to downloading hosts source
        setStateAndDetails(R.string.download_dialog, hostsFileUrl);
        // Declare last modification date
        Date lastModified = null;
        try {
            // Get file from URL
            File hostsSourceFile = new File(new URL(hostsFileUrl).toURI());
            // Copy hosts file source to private file
            try (InputStream inputStream = new FileInputStream(hostsSourceFile)) {
                parseSourceInputStream(hostsSource, inputStream);
            }
            // Get last modified date
            lastModified = new Date(hostsSourceFile.lastModified());
        } catch (IOException | URISyntaxException exception) {
            Log.e(Constants.TAG, "Error while copying hosts file from " + hostsFileUrl + ".", exception);
            // Return copy failed
            return false;
        } finally {
            // Update last_modified_online (null if not available or error happened)
            this.hostsSourceDao.updateOnlineModificationDate(hostsFileUrl, lastModified);
        }
        // Return copy successful
        return true;
    }

    /**
     * Parse a source from its input stream to store it into database.
     *
     * @param hostsSource The host source to parse.
     * @param inputStream The host source input stream to read source from.
     * @throws IOException If the source could not be read.
     */
    private void parseSourceInputStream(HostsSource hostsSource, InputStream inputStream) throws IOException {
        boolean parseRedirectedHosts = PreferenceHelper.getRedirectionRules(this.context);
        SourceParser sourceParser = new SourceParser(inputStream, parseRedirectedHosts);
        SourceBatchUpdater updater = new SourceBatchUpdater(this.hostListItemDao);
        updater.updateSource(hostsSource, sourceParser);
    }

    /**
     * Enable all hosts sources.
     *
     * @return {@code true} if at least one source was updated, {@code false} otherwise.
     */
    public boolean enableAllSources() {
        boolean updated = false;
        for (HostsSource source : this.hostsSourceDao.getAll()) {
            if (!source.isEnabled()) {
                updated = true;
                source.setEnabled(true);
                this.hostsSourceDao.update(source);
            }
        }
        return updated;
    }

    /**
     * Set local modifications date to now for all enabled hosts sources.
     */
    public void markHostsSourcesAsInstalled() {
        // Get application context and database
        Date now = new Date();
        this.hostsSourceDao.updateEnabledLocalModificationDates(now);
    }

    /**
     * Clear local modification dates for all hosts sources.
     */
    public void markHostsSourcesAsUninstalled() {
        // Get application context and database
        this.hostsSourceDao.clearLocalModificationDates();
    }

    private void setStateAndDetails(@StringRes int stateResId, @StringRes int detailsResId) {
        setStateAndDetails(stateResId, this.context.getString(detailsResId));
    }

    private void setStateAndDetails(@StringRes int stateResId, String details) {
        this.state = this.context.getString(stateResId);
//        this.detailedState = details;
        setChanged();
        notifyObservers();
    }
}
