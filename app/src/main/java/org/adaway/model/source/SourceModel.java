package org.adaway.model.source;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED;
import static org.adaway.model.error.HostError.DOWNLOAD_FAILED;
import static org.adaway.model.error.HostError.NO_CONNECTION;
import static java.time.ZoneOffset.UTC;
import static java.time.format.FormatStyle.MEDIUM;
import static java.time.temporal.ChronoUnit.WEEKS;
import static java.util.Objects.requireNonNull;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.converter.ZonedDateTimeConverter;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.git.GitHostsSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

/**
 * This class is the model to represent hosts source management.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class SourceModel {
    /**
     * The HTTP client cache size (100Mo).
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
     * The {@link HostEntry} DAO.
     */
    private final HostEntryDao hostEntryDao;
    /**
     * The update available status.
     */
    private final MutableLiveData<Boolean> updateAvailable;
    /**
     * The model state.
     */
    private final MutableLiveData<String> state;
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
        this.hostEntryDao = database.hostEntryDao();
        this.state = new MutableLiveData<>("");
        this.updateAvailable = new MutableLiveData<>();
        this.updateAvailable.setValue(false);
        SourceUpdateService.syncPreferences(context);
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
     * Check if there is update available for hosts sources.
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
            ZonedDateTime lastModifiedLocal = source.getLocalModificationDate();
            // Update state
            setState(R.string.status_check_source, source.getLabel());
            // Get hosts source last update
            ZonedDateTime lastModifiedOnline = getHostsSourceLastUpdate(source);
            // Some help with debug here
            Timber.d("lastModifiedLocal: %s", dateToString(lastModifiedLocal));
            Timber.d("lastModifiedOnline: %s", dateToString(lastModifiedOnline));
            // Save last modified online
            this.hostsSourceDao.updateOnlineModificationDate(source.getId(), lastModifiedOnline);
            // Check if last modified online retrieved
            if (lastModifiedOnline == null) {
                // If not, consider update is available if install is older than a day
                ZonedDateTime lastWeek = ZonedDateTime.now().minus(1, WEEKS);
                if (lastModifiedLocal != null && lastModifiedLocal.isBefore(lastWeek)) {
                    updateAvailable = true;
                }
            } else {
                // Check if update is available for this source and source enabled
                if (source.isEnabled() && (lastModifiedLocal == null || lastModifiedOnline.isAfter(lastModifiedLocal))) {
                    updateAvailable = true;
                }
            }
        }
        // Check if update is available
        Timber.d("Update check result: %s.", updateAvailable);
        if (updateAvailable) {
            setState(R.string.status_update_available);
        } else {
            setState(R.string.status_no_update_found);
        }
        this.updateAvailable.postValue(updateAvailable);
        return updateAvailable;
    }

    /**
     * Format {@link ZonedDateTime} for printing.
     *
     * @param zonedDateTime The date to format.
     * @return The formatted date string.
     */
    private String dateToString(ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return "not defined";
        } else {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(MEDIUM);
            return zonedDateTime.toString() + " (" + zonedDateTime.format(dateTimeFormatter) + ")";
        }
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
     * @param source The hosts source to get last online update.
     * @return The last online date, {@code null} if the date could not be retrieved.
     */
    @Nullable
    private ZonedDateTime getHostsSourceLastUpdate(HostsSource source) {
        switch (source.getType()) {
            case URL:
                return getUrlLastUpdate(source.getUrl());
            case FILE:
                Uri fileUri = Uri.parse(source.getUrl());
                return getFileLastUpdate(fileUri);
            default:
                return null;
        }
    }

    /**
     * Get the url last online update.
     *
     * @param url The url to get last online update.
     * @return The last online date, {@code null} if the date could not be retrieved.
     */
    private ZonedDateTime getUrlLastUpdate(String url) {
        Timber.v("Checking hosts file: %s.", url);
        // Check Git hosting
        if (GitHostsSource.isHostedOnGit(url)) {
            try {
                return GitHostsSource.getSource(url).getLastUpdate();
            } catch (MalformedURLException e) {
                Timber.w(e, "Failed to get GitHub last update for url " + url + ".");
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
            long lastModified = connection.getLastModified() / 1000;
            if (lastModified == 0) {
                return null;
            }
            return ZonedDateTime.of(LocalDateTime.ofEpochSecond(lastModified, 0, UTC), UTC);
        } catch (Exception e) {
            Timber.e(e, "Exception while downloading from %s.", url);
            return null;
        } finally {
            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection).disconnect();
            }
        }
    }

    /**
     * Get the file last modified date.
     *
     * @param fileUri The file uri to get last modified date.
     * @return The file last modified date, {@code null} if date could not be retrieved.
     */
    private ZonedDateTime getFileLastUpdate(Uri fileUri) {
        ContentResolver contentResolver = this.context.getContentResolver();
        try (Cursor cursor = contentResolver.query(fileUri, null, null, null, null)) {
            if (cursor == null) {
                Timber.w("The content resolver could not find %s.", fileUri);
                return null;
            }
            if (!cursor.moveToFirst()) {
                Timber.w("The content resolver could not find %s.", fileUri);
                return null;
            }
            int columnIndex = cursor.getColumnIndex(COLUMN_LAST_MODIFIED);
            if (columnIndex == -1) {
                Timber.w("The content resolver does not support last modified column %s.", fileUri);
                return null;
            }
            return ZonedDateTimeConverter.fromTimestamp(cursor.getLong(columnIndex));
        } catch (SecurityException e) {
            Timber.i(e, "The SAF permission was removed.");
            return null;
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
        ZonedDateTime now = ZonedDateTime.now();
        // Get each hosts source
        for (HostsSource source : this.hostsSourceDao.getAll()) {
            int sourceId = source.getId();
            String url = source.getUrl();
            // Clear disabled source
            if (!source.isEnabled()) {
                this.hostListItemDao.clearSourceHosts(sourceId);
                this.hostsSourceDao.clearProperties(sourceId);
                continue;
            }
            // Get hosts source last update
            ZonedDateTime onlineModificationDate = getHostsSourceLastUpdate(source);
            if (onlineModificationDate == null) {
                onlineModificationDate = now;
            }
            // Check if update available
            ZonedDateTime localModificationDate = source.getLocalModificationDate();
            if (localModificationDate != null && localModificationDate.isAfter(onlineModificationDate)) {
                Timber.i("Skip source " + source.getUrl() + ": no update.");
                continue;
            }
            // Increment number of copy
            numberOfCopies++;
            try {
                // Check hosts source type
                switch (source.getType()) {
                    case URL:
                        downloadHostSource(source);
                        break;
                    case FILE:
                        readSourceFile(source);
                        break;
                    default:
                        Timber.w("Hosts source type  is not supported.");
                }
                // Update local and online modification dates to now
                localModificationDate = onlineModificationDate.isAfter(now) ? onlineModificationDate : now;
                this.hostsSourceDao.updateModificationDates(sourceId, localModificationDate, onlineModificationDate);
                // Update size
                this.hostsSourceDao.updateSize(sourceId);
            } catch (IOException e) {
                Timber.w(e, "Failed to retrieve host source " + url + ".");
                // Increment number of failed copy
                numberOfFailedCopies++;
            }
        }
        // Check if all copies failed
        if (numberOfCopies == numberOfFailedCopies && numberOfCopies != 0) {
            throw new HostErrorException(DOWNLOAD_FAILED);
        }
        // Synchronize hosts entries
        syncHostEntries();
        // Mark no update available
        this.updateAvailable.postValue(false);
    }

    /**
     * Synchronize hosts entries from current source states.
     */
    public void syncHostEntries() {
        setState(R.string.status_sync_database);
        this.hostEntryDao.sync();
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
     * Download an hosts source file and append it to the database.
     *
     * @param source The hosts source to download.
     * @throws IOException If the hosts source could not be downloaded.
     */
    private void downloadHostSource(HostsSource source) throws IOException {
        // Get hosts file URL
        String hostsFileUrl = source.getUrl();
        Timber.v("Downloading hosts file: %s.", hostsFileUrl);
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
             Reader reader = requireNonNull(response.body()).charStream();
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            parseSourceInputStream(source, bufferedReader);
        } catch (IOException e) {
            throw new IOException("Exception while downloading hosts file from " + hostsFileUrl + ".", e);
        }
    }

    /**
     * Read a hosts source file and append it to the database.
     *
     * @param hostsSource The hosts source to copy.
     * @throws IOException If the hosts source could not be copied.
     */
    private void readSourceFile(HostsSource hostsSource) throws IOException {
        // Get hosts file URI
        String hostsFileUrl = hostsSource.getUrl();
        Uri fileUri = Uri.parse(hostsFileUrl);
        Timber.v("Reading hosts source file: %s.", hostsFileUrl);
        // Set state to copying hosts source
        setState(R.string.status_read_source, hostsFileUrl);
        try (InputStream inputStream = this.context.getContentResolver().openInputStream(fileUri);
             InputStreamReader reader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            parseSourceInputStream(hostsSource, bufferedReader);
        } catch (IOException e) {
            throw new IOException("Error while reading hosts file from " + hostsFileUrl + ".", e);
        }
    }

    /**
     * Parse a source from its input stream to store it into database.
     *
     * @param hostsSource The host source to parse.
     * @param reader      The host source reader.
     */
    private void parseSourceInputStream(HostsSource hostsSource, BufferedReader reader) {
        setState(R.string.status_parse_source, hostsSource.getLabel());
        long startTime = System.currentTimeMillis();
        new SourceLoader(hostsSource).parse(reader, this.hostListItemDao);
        long endTime = System.currentTimeMillis();
        Timber.i("Parsed " + hostsSource.getUrl() + " in " + (endTime - startTime) / 1000 + "s");
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
                this.hostsSourceDao.toggleEnabled(source);
                updated = true;
            }
        }
        return updated;
    }

    private void setState(@StringRes int stateResId, Object... details) {
        String state = this.context.getString(stateResId, details);
        Timber.d(state);
        this.state.postValue(state);
    }
}
