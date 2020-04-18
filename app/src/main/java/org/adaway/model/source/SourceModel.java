package org.adaway.model.source;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

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
import org.adaway.util.DateUtils;
import org.adaway.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static org.adaway.model.error.HostError.DOWNLOAD_FAILED;
import static org.adaway.model.error.HostError.NO_CONNECTION;

/**
 * This class is the model to represent hosts source management.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class SourceModel {
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
    private MutableLiveData<String> state;
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
        this.state = new MutableLiveData<>("");
        this.updateAvailable = new MutableLiveData<>();
        this.updateAvailable.setValue(false);
        checkUpdateAtStartUp();
    }

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
     * Get the model state.
     *
     * @return The model state.
     */
    public LiveData<String> getState() {
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

    /**
     * Check if there is update available in hosts sources.
     *
     * @throws HostErrorException If the hosts sources could not be checked.
     */
    public boolean checkForUpdate() throws HostErrorException {
        // Check current connection
        if (isDeviceOffline()) {
            throw new HostErrorException(NO_CONNECTION);
        }
        // Initialize update status
        boolean updateAvailable = false;
        boolean anyHostsSourceVerified = false;
        // Get hosts sources
        List<HostsSource> sources = this.hostsSourceDao.getEnabled();
        if (sources.isEmpty()) {
            // Return no update as no source
            this.updateAvailable.postValue(false);
            return false;
        }
        // Update state
        setState(R.string.status_check);
        // Check each source
        for (HostsSource source : sources) {
            // Get URL and lastModified from db
            String sourceUrl = source.getUrl();
            Date lastModifiedLocal = source.getLastLocalModification();
            // Update state
            setState(R.string.status_check_source, sourceUrl);
            // Get hosts source last update
            Date lastModifiedOnline = getHostsSourceLastUpdate(sourceUrl);
            // Some help with debug here
            Log.d(TAG, "lastModifiedLocal: "
                    + (lastModifiedLocal == null ? "not defined" : lastModifiedLocal)
                    + " (" + DateUtils.dateToString(this.context, lastModifiedLocal) + ")"
            );
            Log.d(TAG, "lastModifiedOnline: "
                    + (lastModifiedOnline == null ? "not defined" : lastModifiedOnline)
                    + " (" + DateUtils.dateToString(this.context, lastModifiedOnline) + ")"
            );
            // Save last modified online
            this.hostsSourceDao.updateOnlineModificationDate(source.getId(), lastModifiedOnline);
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
            throw new HostErrorException(DOWNLOAD_FAILED);
        }
        // Check if update is available
        if (updateAvailable) {
            setState(R.string.status_update_available);
        } else {
            setState(R.string.status_no_update_found);
        }
        Log.d(TAG, "Update check result: " + updateAvailable);
        this.updateAvailable.postValue(updateAvailable);
        return updateAvailable;
    }

    /**
     * Checks if device is offline.
     *
     * @return returns {@code true} if device is offline, {@code false} otherwise.
     */
    private boolean isDeviceOffline() {
        ConnectivityManager connectivityManager = (ConnectivityManager) this.context.getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
        return netInfo == null || !netInfo.isConnectedOrConnecting();
    }

    /**
     * Get the hosts source last online update.
     *
     * @param url The hosts source URL to get last online update.
     * @return The last online date, {@code null} if the date could not be retrieved.
     */
    @Nullable
    private Date getHostsSourceLastUpdate(String url) {
        Log.v(TAG, "Checking hosts file: " + url);
        // Check Git hosting
        if (GitHostsSource.isHostedOnGit(url)) {
            try {
                return GitHostsSource.getSource(url).getLastUpdate();
            } catch (MalformedURLException exception) {
                Log.w(TAG, "Failed to get GitHub last update for url " + url + ".", exception);
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
            Log.e(TAG, "Exception while downloading from " + url, exception);
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
        if (isDeviceOffline()) {
            throw new HostErrorException(NO_CONNECTION);
        }
        // Update state to downloading
        setState(R.string.status_retrieve);
        // Initialize copy counters
        int numberOfCopies = 0;
        int numberOfFailedCopies = 0;
        // Compute current date in UTC timezone
        Date now = Date.from(Instant.now());
        // Get each hosts source
        for (HostsSource hostsSource : this.hostsSourceDao.getEnabled()) {
            // Increment number of copy
            numberOfCopies++;
            try {
                // Check hosts source protocol
                String url = hostsSource.getUrl();
                String protocol = new URL(url).getProtocol();
                switch (protocol) {
                    case "https":
                        downloadHostSource(hostsSource);
                        break;
                    case "file":
                        copyHostSourceFile(hostsSource);
                        break;
                    default:
                        Log.w(TAG, "Hosts source protocol " + protocol + " is not supported.");
                }
                // Get hosts source last update
                Date lastModifiedOnline = getHostsSourceLastUpdate(hostsSource.getUrl());
                if (lastModifiedOnline == null) {
                    lastModifiedOnline = now;
                }
                // Update local and online modification dates to now
                this.hostsSourceDao.updateModificationDates(hostsSource.getId(), now, lastModifiedOnline);
            } catch (IOException exception) {
                Log.w(TAG, "Failed to retrieve host source " + hostsSource.getUrl() + ".", exception);
                // Increment number of failed copy
                numberOfFailedCopies++;
            }
        }
        // Check if all copies failed
        if (numberOfCopies == numberOfFailedCopies && numberOfCopies != 0) {
            throw new HostErrorException(DOWNLOAD_FAILED);
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
     * @throws IOException If the hosts source could not be downloaded.
     */
    private void downloadHostSource(HostsSource hostsSource) throws IOException {
        // Get hosts file URL
        String hostsFileUrl = hostsSource.getUrl();
        Log.v(TAG, "Downloading hosts file: " + hostsFileUrl);
        // Set state to downloading hosts source
        setState(R.string.status_download_source, hostsFileUrl);
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
        } catch (IOException exception) {
            throw new IOException("Exception while downloading hosts file from " + hostsFileUrl + ".", exception);
        }
    }

    /**
     * Copy a hosts source file and append it to a private file.
     *
     * @param hostsSource The hosts source to copy.
     * @throws IOException If the hosts source could not be copied.
     */
    private void copyHostSourceFile(HostsSource hostsSource) throws IOException {
        // Get hosts file URL
        String hostsFileUrl = hostsSource.getUrl();
        Log.v(TAG, "Copying hosts source file: " + hostsFileUrl);
        // Set state to copying hosts source
        setState(R.string.status_copy_source, hostsFileUrl);
        try {
            // Get file from URL
            File hostsSourceFile = new File(new URL(hostsFileUrl).toURI());
            // Copy hosts file source to private file
            try (InputStream inputStream = new FileInputStream(hostsSourceFile)) {
                parseSourceInputStream(hostsSource, inputStream);
            }
        } catch (IOException | URISyntaxException exception) {
            throw new IOException("Error while copying hosts file from " + hostsFileUrl + ".", exception);
        }
    }

    /**
     * Parse a source from its input stream to store it into database.
     *
     * @param hostsSource The host source to parse.
     * @param inputStream The host source input stream to read source from.
     * @throws IOException If the source could not be read.
     */
    private void parseSourceInputStream(HostsSource hostsSource, InputStream inputStream) throws IOException {
        setState(R.string.status_parse_source, hostsSource.getUrl());
        long startTime = System.currentTimeMillis();
        boolean parseRedirectedHosts = PreferenceHelper.getRedirectionRules(this.context);
        SourceParser sourceParser = new SourceParser(hostsSource, inputStream, parseRedirectedHosts);
        SourceBatchUpdater updater = new SourceBatchUpdater(this.hostListItemDao);
        updater.updateSource(hostsSource, sourceParser.getItems());
        long endTime = System.currentTimeMillis();
        Log.i(TAG, "Parsed " + hostsSource.getUrl() + " in " + (endTime - startTime) / 1000 + "s");
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

    private void setState(@StringRes int stateResId, Object... details) {
        this.state.postValue(this.context.getString(stateResId, details));
    }
}
