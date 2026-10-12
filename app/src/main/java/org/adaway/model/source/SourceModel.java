package org.adaway.model.source;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED;
import static org.adaway.model.error.HostError.DOWNLOAD_FAILED;
import static org.adaway.model.error.HostError.NO_CONNECTION;
import static org.adaway.model.error.HostError.UPDATE_IN_PROGRESS;
import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.time.format.FormatStyle.MEDIUM;
import static java.time.temporal.ChronoUnit.WEEKS;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.adaway.AdAwayApplication;
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
import org.adaway.util.AppExecutors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * This class is the model to represent hosts source management.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class SourceModel {
    private static final long FILTER_OPERATION_PROGRESS_THROTTLE_MS = 250L;
    /**
     * The HTTP client cache size.
     */
    private static final long CACHE_SIZE = 250L * 1024L * 1024L; // 250MB

    // Hardware-adaptive parallelism - computed once based on device capabilities
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();
    private static final long MAX_MEMORY = Runtime.getRuntime().maxMemory();

    /**
     * Check parallelism: I/O bound (network HEAD requests), scales with cores.
     * Higher is better since most time is waiting for network.
     */
    private static final int CHECK_PARALLELISM = Math.max(8, Math.min(CPU_CORES * 4, 32));

    /**
     * Download parallelism: Network bound, scales with cores but caps at 32.
     * Too many causes socket exhaustion on some devices.
     */
    private static final int DOWNLOAD_PARALLELISM = Math.max(12, Math.min(CPU_CORES * 4, 32));

    /**
     * Parser threads per source (internal to SourceLoader).
     * The outer import lane is serialized, so this is the only parser parallelism active while
     * SQLite is receiving rows.
     */
    static final int PARSER_THREADS_PER_SOURCE = 2;
    /**
     * Only one SourceLoader may write to hosts_lists at a time.
     * Downloads remain parallel, and SourceLoader still parses lines with worker threads, but the
     * SQLite insert transaction is serialized to avoid writer contention and WAL/page-cache churn.
     */
    private static final int IMPORT_WRITER_PARALLELISM = 1;
    private static final String LAST_MODIFIED_HEADER = "Last-Modified";
    private static final String IF_NONE_MATCH_HEADER = "If-None-Match";
    private static final String IF_MODIFIED_SINCE_HEADER = "If-Modified-Since";
    private static final String ENTITY_TAG_HEADER = "ETag";
    static final long MAX_SOURCE_BYTES = 512L * 1024 * 1024;
    /**
     * The application context.
     */
    private final Context context;
    /**
     * The application database.
     */
    private final AppDatabase database;
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
     * The model progress (for in-app percentage display).
     */
    private final MutableLiveData<Progress> progress;
    /**
     * Multi-phase progress for detailed UI (Check/Download/Parse bars).
     */
    private final MutableLiveData<MultiPhaseProgress> multiPhaseProgress;
    /**
     * Shared operation state for filter update/import UI surfaces.
     */
    private final MutableLiveData<FilterOperationState> filterOperationState;
    /**
     * Builder for multi-phase progress updates.
     */
    private final MultiPhaseProgressBuilder progressBuilder;
    private final Handler mainHandler;
    private long filterOperationStartedElapsedMs = 0L;
    private long lastFilterOperationEmitElapsedMs = 0L;
    private int lastFilterOperationPercent = -1;
    private boolean lastFilterOperationActive = false;
    private final AtomicInteger terminalIdleToken = new AtomicInteger(0);
    /**
     * The HTTP client to download hosts sources ({@code null} until initialized by {@link #getHttpClient()}).
     */
    private OkHttpClient cachedHttpClient;
    /**
     * Fast HTTP client for HEAD requests during check phase ({@code null} until initialized).
     */
    private OkHttpClient cachedHeadClient;
    /**
     * Current update's thread pools - stored to allow shutdown when new update starts.
     */
    private ExecutorService currentDownloadPool;
    private ExecutorService currentParsePool;
    private final Set<Call> activeDownloads = ConcurrentHashMap.newKeySet();
    /**
     * Prevent multiple overlapping update executions (e.g. auto-update + user refresh).
     * Overlaps were causing counters/UI to become inconsistent and parse tasks to be interrupted.
     */
    private final AtomicBoolean updateInProgress = new AtomicBoolean(false);
    /**
     * Runtime cache rebuilds are compatibility work for UI counters and legacy table readers.
     * Protection decisions read active hosts_lists truth directly, so cache rebuild failures must
     * never roll back a generation publish.
     */
    private final AtomicBoolean runtimeCacheRefreshInProgress = new AtomicBoolean(false);
    /**
     * Target generation for the current import run.
     * - For full updates: set to next generation, and flipped active at the end (atomic swap).
     * - For single-source updates: set to current active generation (in-place update).
     */
    private volatile int currentImportGeneration = 0;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public SourceModel(Context context) {
        this(context, AppDatabase.getInstance(context));
    }

    SourceModel(@NonNull Context context, @NonNull AppDatabase database) {
        this.context = context;
        this.database = database;
        this.hostsSourceDao = database.hostsSourceDao();
        this.hostListItemDao = database.hostsListItemDao();
        this.hostEntryDao = database.hostEntryDao();
        // Avoid setValue() here: SourceModel may be instantiated off the main thread (e.g. from WorkManager).
        this.state = new MutableLiveData<>();
        this.state.postValue("");
        this.progress = new MutableLiveData<>();
        this.progress.postValue(Progress.idle());
        this.multiPhaseProgress = new MutableLiveData<>();
        this.multiPhaseProgress.postValue(MultiPhaseProgress.idle());
        this.filterOperationState = new MutableLiveData<>();
        this.filterOperationState.postValue(FilterOperationState.idle());
        this.progressBuilder = new MultiPhaseProgressBuilder();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.updateAvailable = new MutableLiveData<>();
        this.updateAvailable.postValue(false);
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
     * Get current progress for long-running source operations.
     */
    public LiveData<Progress> getProgress() {
        return this.progress;
    }

    /**
     * Get detailed multi-phase progress (Check/Download/Parse).
     */
    public LiveData<MultiPhaseProgress> getMultiPhaseProgress() {
        return this.multiPhaseProgress;
    }

    public LiveData<FilterOperationState> getFilterOperationState() {
        return this.filterOperationState;
    }

    /**
     * Request pause of the current update operation.
     */
    public void requestPause() {
        if (!canControlCurrentUpdate()) {
            return;
        }
        this.progressBuilder.setPaused(true);
        postMultiPhaseProgress(this.progressBuilder.build(), true);
    }

    /**
     * Resume a paused update operation.
     */
    public void requestResume() {
        if (!canControlCurrentUpdate()) {
            return;
        }
        this.progressBuilder.setPaused(false);
        postMultiPhaseProgress(this.progressBuilder.build(), true);
    }

    /**
     * Request stop of the current update operation.
     */
    public void requestStop() {
        if (!canControlCurrentUpdate()) {
            return;
        }
        this.progressBuilder.setStopped(true);
        postMultiPhaseProgress(this.progressBuilder.build(), true);
    }

    /**
     * Set the scheduler task name for display in the UI.
     * @param name The scheduler task name.
     */
    public void setSchedulerTaskName(String name) {
        this.progressBuilder.setSchedulerTaskName(name);
    }

    private boolean canControlCurrentUpdate() {
        return this.updateInProgress.get()
                && !this.progressBuilder.isStopped()
                && !this.progressBuilder.isFinalizing()
                && !this.progressBuilder.isComplete();
    }

    private void beginUpdateOperation(@NonNull String operationName) throws HostErrorException {
        if (!this.updateInProgress.compareAndSet(false, true)) {
            Timber.w("Update already in progress - ignoring %s", operationName);
            throw new HostErrorException(UPDATE_IN_PROGRESS);
        }
    }

    /**
     * Run a source edit and its runtime synchronization without racing a source update.
     * Call on a worker thread; enqueue any follow-up download only after this method returns.
     */
    public <T> T runSourceMutation(@NonNull Supplier<T> mutation) throws HostErrorException {
        beginUpdateOperation("source mutation");
        try {
            return mutation.get();
        } finally {
            finishUpdateOperation();
        }
    }

    private void finishUpdateOperation() {
        synchronized (this) {
            this.currentDownloadPool = null;
            this.currentParsePool = null;
            this.updateInProgress.set(false);
        }
    }

    private void scheduleRuntimeCacheRefresh() {
        if (!this.runtimeCacheRefreshInProgress.compareAndSet(false, true)) {
            Timber.i("Runtime cache refresh already in progress; skipping duplicate request.");
            return;
        }

        AppExecutors.getInstance().diskIO().execute(() -> {
            long startedMs = SystemClock.elapsedRealtime();
            SupportSQLiteDatabase db = this.database.getOpenHelper().getWritableDatabase();
            try {
                applyBalancedImportPragmas(db);
                rebuildRuntimeCaches(db);
                Timber.i("Runtime cache refresh completed in %dms",
                        SystemClock.elapsedRealtime() - startedMs);
            } catch (RuntimeException exception) {
                Timber.w(exception, "Runtime cache refresh failed; active hosts_lists truth " +
                        "remains authoritative.");
            } finally {
                restoreImportPragmas(db);
                this.runtimeCacheRefreshInProgress.set(false);
            }
        });
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
        // Get enabled hosts sources
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
                // If not, consider update is available if install is older than a week
                ZonedDateTime lastWeek = ZonedDateTime.now().minus(1, WEEKS);
                if (lastModifiedLocal != null && lastModifiedLocal.isBefore(lastWeek)) {
                    updateAvailable = true;
                }
            } else {
                // Check if source was never installed or installed before the last update
                if (lastModifiedLocal == null || lastModifiedOnline.isAfter(lastModifiedLocal)) {
                    updateAvailable = true;
                }
            }
        }
        // Update statuses
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
            return zonedDateTime + " (" + zonedDateTime.format(dateTimeFormatter) + ")";
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
            Timber.w("ConnectivityManager is null, assuming offline");
            return true;
        }
        NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
        boolean offline = netInfo == null || !netInfo.isConnectedOrConnecting();
        if (offline) {
            Timber.w("Device is offline: netInfo=%s", netInfo);
        }
        return offline;
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
                return getUrlLastUpdate(source);
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
     * @param source The source to get last online update.
     * @return The last online date, {@code null} if the date could not be retrieved.
     */
    private ZonedDateTime getUrlLastUpdate(HostsSource source) {
        String url = source.getUrl();
        Timber.v("Checking url last update for source: %s.", url);
        // Check Git hosting
        if (GitHostsSource.isHostedOnGit(url)) {
            try {
                return GitHostsSource.getSource(url).getLastUpdate();
            } catch (MalformedURLException e) {
                Timber.w(e, "Failed to get Git last commit for url %s.", url);
                return null;
            }
        }
        // Default hosting - use fast HEAD client with aggressive timeouts
        Request request = getRequestFor(source).head().build();
        try (Response response = getHeadClient().newCall(request).execute()) {
            String lastModified = response.header(LAST_MODIFIED_HEADER);
            if (lastModified == null) {
                return response.code() == HTTP_NOT_MODIFIED ?
                     source.getOnlineModificationDate() : null;
            }
            return ZonedDateTime.parse(lastModified, RFC_1123_DATE_TIME);
        } catch (IOException | DateTimeParseException e) {
            Timber.e(e, "Exception while fetching last modified date of source %s.", url);
            return null;
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
            if (cursor == null || !cursor.moveToFirst()) {
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
     * Retrieve all enabled hosts sources through the staged full-update pipeline.
     *
     * @throws HostErrorException If the hosts sources could not be downloaded.
     */
    public boolean retrieveHostsSources() throws HostErrorException {
        return checkAndRetrieveHostsSources();
    }

    /**
     * Check and retrieve hosts sources using a true pipeline architecture.
     * Three concurrent phases run independently:
     * 1. Check phase: Runs in parallel, feeds sources to download queue as they're found
     * 2. Download phase: Pool of workers consuming from download queue, feeding to parse queue
     * 3. Parse phase: Semaphore-bounded pool parsing completed downloads
     *
     * This maximizes throughput by overlapping all three phases.
     *
     * @throws HostErrorException If the hosts sources could not be downloaded.
     */
    public boolean checkAndRetrieveHostsSources() throws HostErrorException {
        beginUpdateOperation("checkAndRetrieveHostsSources");
        SupportSQLiteDatabase writableDb = null;
        final SqlUpdateDeduper[] sqlDeduperRef = new SqlUpdateDeduper[1];
        final List<DownloadResult> downloads = Collections.synchronizedList(new ArrayList<>());
        try {
            // Balanced durability speed-up for bulk import:
            // Reduce fsync frequency during the update window. If the device crashes mid-update,
            // the user can re-run the update (correctness is preserved by rebuilding).
            writableDb = this.database.getOpenHelper().getWritableDatabase();
            applyBalancedImportPragmas(writableDb);

            // Reset progress builder for this operation
            progressBuilder.reset();
            terminalIdleToken.incrementAndGet();
            resetFilterOperationPublisher();
            // Atomic update: build next generation, then flip active generation at the end.
            int activeGen = ensureAndGetActiveGeneration(writableDb);
            final int importGeneration = activeGen + 1;
            this.currentImportGeneration = importGeneration;

        // Get all sources and categorize
        List<HostsSource> all = this.hostsSourceDao.getAll();
        List<HostsSource> enabledUrlSources = new ArrayList<>();
        List<HostsSource> enabledFileSources = new ArrayList<>();
        List<Integer> disabledSourceIds = new ArrayList<>();
        final List<SourceCommit> sourceCommits =
                Collections.synchronizedList(new ArrayList<>());
        final List<SourceFailure> sourceFailures =
                Collections.synchronizedList(new ArrayList<>());
        boolean runtimeRebuildRequired = false;

        for (HostsSource source : all) {
            if (!source.isEnabled()) {
                disabledSourceIds.add(source.getId());
                runtimeRebuildRequired = true;
                continue;
            }
            switch (source.getType()) {
                case URL:
                    enabledUrlSources.add(source);
                    break;
                case FILE:
                    enabledFileSources.add(source);
                    break;
                default:
                    Timber.w("Hosts source type is not supported.");
            }
        }

        if (!enabledUrlSources.isEmpty() && isDeviceOffline()) {
            throw new HostErrorException(NO_CONNECTION);
        }

        final int totalSources = enabledUrlSources.size() + enabledFileSources.size();
        if (totalSources == 0) {
            if (runtimeRebuildRequired) {
                finalizeNoChange(
                        importGeneration, disabledSourceIds, sourceCommits, sourceFailures, true);
            }
            this.updateAvailable.postValue(false);
            postProgress(0, 0, null, 10000, 100);
            postMultiPhaseProgress(MultiPhaseProgress.idle());
            return true;
        }

        // Initialize progress tracking
        progressBuilder.setTotalToCheck(totalSources);
        // Parse progress should be monotonic and based on the user's full selection.
        // Sources that are up-to-date (304) or failed downloads still count as "done" for the parse phase.
        progressBuilder.setTotalToParse(totalSources);
        setState(R.string.status_check);
        postMultiPhaseProgress(progressBuilder.build());

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime lastWeek = now.minus(1, WEEKS);
        final AtomicInteger failedCount = new AtomicInteger(0);
        final AtomicInteger successfulSourceCount = new AtomicInteger(0);
        final AtomicInteger changedSourceCount = new AtomicInteger(0);
        final AtomicInteger upToDateCount = new AtomicInteger(0);
        final AtomicBoolean generationUnsafe = new AtomicBoolean(false);
        final List<HostsSource> deferredCarryForwardSources =
                Collections.synchronizedList(new ArrayList<>());
        final List<HostsSource> failedCarryForwardSources =
                Collections.synchronizedList(new ArrayList<>());
        final SqlUpdateDeduper sqlDeduper = new SqlUpdateDeduper(writableDb);
        sqlDeduperRef[0] = sqlDeduper;

        // Handle FILE sources first (quick, no network) - count as check+download+parse in one
        for (HostsSource source : enabledFileSources) {
            if (progressBuilder.isStopped()) {
                Timber.i("Update stopped by user");
                break;
            }
            while (progressBuilder.isPaused() && !progressBuilder.isStopped()) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    progressBuilder.setStopped(true);
                    break;
                }
            }
            if (progressBuilder.isStopped()) {
                Timber.i("Update stopped by user");
                break;
            }

            progressBuilder.setCurrentLabel(source.getLabel());
            progressBuilder.incrementChecked();
            progressBuilder.incrementTotalToDownload();
            postMultiPhaseProgress(progressBuilder.build());

            try {
                readSourceFile(source, importGeneration, sqlDeduper);
                ZonedDateTime onlineModificationDate = getHostsSourceLastUpdate(source);
                if (onlineModificationDate == null) onlineModificationDate = now;
                ZonedDateTime localModificationDate = onlineModificationDate.isAfter(now) ? onlineModificationDate : now;
                sourceCommits.add(SourceCommit.changed(
                        source.getId(), null, localModificationDate, onlineModificationDate,
                        importGeneration));
                successfulSourceCount.incrementAndGet();
                changedSourceCount.incrementAndGet();
                progressBuilder.incrementDownloaded();
                progressBuilder.incrementParsed();
            } catch (IOException | RuntimeException e) {
                Timber.w(e, "Failed to retrieve host source %s.", source.getUrl());
                if (Thread.currentThread().isInterrupted()) {
                    progressBuilder.setStopped(true);
                }
                failedCarryForwardSources.add(source);
                sourceFailures.add(SourceFailure.of(source.getId(), e));
                failedCount.incrementAndGet();
                progressBuilder.incrementDownloaded();
                progressBuilder.incrementParsed(); // Still count as parsed (failed)
            }
            postMultiPhaseProgress(progressBuilder.build());
        }

        // SIMPLIFIED 2-PHASE PIPELINE - no check phase, direct download with conditional GET
        // Download uses If-Modified-Since headers - server returns 304 if unchanged (fast, no body)
        if (!progressBuilder.isStopped() && !enabledUrlSources.isEmpty()) {
            long heapMB = MAX_MEMORY / (1024 * 1024);

            // Create DEDICATED thread pools for THIS update (not shared)
            // Store them so they can be shut down if a new update starts
            int downloadParallelism = Math.min(12, enabledUrlSources.size());
            int parseParallelism = IMPORT_WRITER_PARALLELISM;
            ExecutorService downloadPool;
            ExecutorService parsePool;
            synchronized (this) {
                downloadPool = Executors.newFixedThreadPool(downloadParallelism, r -> {
                    Thread t = new Thread(r, "AdAway-Download");
                    t.setPriority(Thread.NORM_PRIORITY);
                    return t;
                });
                parsePool = Executors.newFixedThreadPool(parseParallelism, r -> {
                    Thread t = new Thread(r, "AdAway-Parse");
                    t.setPriority(Thread.NORM_PRIORITY);
                    return t;
                });
                currentDownloadPool = downloadPool;
                currentParsePool = parsePool;
            }

            final int totalSourcesToProcess = enabledUrlSources.size();
            Timber.d("Pipeline start: cores=%d, heap=%dMB, sources=%d, importWriters=%d",
                    CPU_CORES, heapMB, totalSourcesToProcess, parseParallelism);
            long pipelineStartTime = System.currentTimeMillis();

            Semaphore parseSemaphore = new Semaphore(parseParallelism);
            final ZonedDateTime nowFinal = now;

            // Atomic counters for tracking
            final AtomicInteger downloadsCompleted = new AtomicInteger(0);
            final AtomicInteger parsesSubmitted = new AtomicInteger(0);
            final AtomicInteger parsesCompleted = new AtomicInteger(0);
            // CompletionServices for both phases
            CompletionService<DownloadResult> downloadCompletion = new ExecutorCompletionService<>(downloadPool);
            CompletionService<Void> parseCompletion = new ExecutorCompletionService<>(parsePool);

            // Submit ALL downloads upfront - uses If-Modified-Since for conditional GET
            for (HostsSource source : enabledUrlSources) {
                // Track download stage as "sources processed", regardless of 200/304.
                progressBuilder.incrementTotalToDownload();
                downloadCompletion.submit(() -> {
                    try {
                        if (progressBuilder.isStopped()) return DownloadResult.failed(source);
                        while (progressBuilder.isPaused() && !progressBuilder.isStopped()) {
                            Thread.sleep(100);
                        }
                        progressBuilder.setCurrentLabel(source.getLabel());
                        setState(R.string.status_download_source, source.getUrl());
                        if (progressBuilder.isStopped()) return DownloadResult.failed(source);
                        DownloadResult result = downloadToTempFile(source, null);
                        downloads.add(result);
                        return result;
                    } catch (Exception e) {
                        Timber.w(e, "Download worker failed for %s", source.getUrl());
                        String message = e.getMessage() != null ? e.getMessage()
                                : e.getClass().getSimpleName();
                        return DownloadResult.failed(source, message);
                    }
                });
            }

            // CONCURRENT PROCESSING LOOP - downloads and parses simultaneously
            while (!progressBuilder.isStopped()) {
                boolean madeProgress = false;

                // Process completed downloads
                if (downloadsCompleted.get() < totalSourcesToProcess) {
                    try {
                        Future<DownloadResult> dlFuture = downloadCompletion.poll(50, TimeUnit.MILLISECONDS);
                        if (dlFuture != null) {
                            downloadsCompleted.incrementAndGet();
                            madeProgress = true;
                            DownloadResult result = dlFuture.get();

                            if (result.notModified) {
                                sourceCommits.add(SourceCommit.unchanged(result.source.getId()));
                                deferredCarryForwardSources.add(result.source);
                                upToDateCount.incrementAndGet();
                                successfulSourceCount.incrementAndGet();
                                progressBuilder.incrementChecked(); // Count as checked
                                progressBuilder.incrementDownloaded(); // Count as processed in "download" stage
                                progressBuilder.incrementParsed(); // No parsing needed -> count as done for parse phase
                                Timber.d("Source %s is up-to-date (304)", result.source.getLabel());
                            } else if (result.success) {
                                // 200 OK - new content to parse
                                Timber.d("Source %s needs parsing (200 OK, file=%s)", result.source.getLabel(), result.tmpFile);
                                parsesSubmitted.incrementAndGet();
                                progressBuilder.incrementChecked(); // Count as checked
                                progressBuilder.incrementDownloaded();

                                parseCompletion.submit(() -> {
                                    try {
                                        parseSemaphore.acquire();
                                        if (progressBuilder.isStopped()) return null;
                                        while (progressBuilder.isPaused() && !progressBuilder.isStopped()) {
                                            Thread.sleep(100);
                                        }
                                        if (progressBuilder.isStopped()) return null;
                                        setState(R.string.status_parse_source, result.source.getLabel());
                                        try (BufferedReader reader = result.openReader()) {
                                            parseSourceInputStream(result.source, reader,
                                                    importGeneration, sqlDeduper);
                                        }
                                        ZonedDateTime onlineMod = result.onlineModificationDate != null ? result.onlineModificationDate : nowFinal;
                                        ZonedDateTime localMod = onlineMod.isAfter(nowFinal) ? onlineMod : nowFinal;
                                        sourceCommits.add(SourceCommit.changed(
                                                result.source.getId(), result.entityTag,
                                                localMod, onlineMod, importGeneration));
                                        successfulSourceCount.incrementAndGet();
                                        changedSourceCount.incrementAndGet();
                                    } catch (Exception e) {
                                        Timber.w(e, "Failed to parse %s", result.source.getUrl());
                                        failedCarryForwardSources.add(result.source);
                                        sourceFailures.add(SourceFailure.of(result.source.getId(), e));
                                        failedCount.incrementAndGet();
                                    } finally {
                                        parseSemaphore.release();
                                        result.cleanup();
                                        int parsed = progressBuilder.incrementParsed();
                                        MultiPhaseProgress progress = progressBuilder.build();
                                        Timber.d("Parse complete: %d/%d (%.1f%%)", parsed, progress.totalToParse, progress.getParsePercentDouble());
                                        postMultiPhaseProgress(progress);
                                    }
                                    return null;
                                });
                            } else {
                                // Download failed
                                failedCount.incrementAndGet();
                                progressBuilder.incrementChecked(); // Count as checked (failed)
                                progressBuilder.incrementDownloaded(); // Count as processed in "download" stage
                                progressBuilder.incrementParsed(); // Count as done for parse phase (failed)
                                Timber.d("Source %s download failed (success=%b, notModified=%b)",
                                        result.source.getLabel(), result.success, result.notModified);
                                String errMsg = result.errorMessage != null ? result.errorMessage : "Download failed";
                                sourceFailures.add(SourceFailure.of(result.source.getId(), errMsg));
                                failedCarryForwardSources.add(result.source);
                            }
                            postMultiPhaseProgress(progressBuilder.build());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        progressBuilder.setStopped(true);
                    } catch (Exception e) {
                        Timber.w(e, "Download task failed");
                        generationUnsafe.set(true);
                        downloadsCompleted.incrementAndGet();
                        failedCount.incrementAndGet();
                        progressBuilder.incrementChecked();
                        progressBuilder.incrementDownloaded();
                        postMultiPhaseProgress(progressBuilder.build());
                    }
                }

                // Process completed parses
                if (parsesCompleted.get() < parsesSubmitted.get()) {
                    try {
                        Future<Void> parseFuture = parseCompletion.poll(50, TimeUnit.MILLISECONDS);
                        if (parseFuture != null) {
                            parsesCompleted.incrementAndGet();
                            madeProgress = true;
                            parseFuture.get(); // Propagate exceptions
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        progressBuilder.setStopped(true);
                    } catch (Exception e) {
                        Timber.w(e, "Parse task failed");
                        generationUnsafe.set(true);
                        parsesCompleted.incrementAndGet();
                    }
                }

                // Check if all work is done
                boolean allDownloadsDone = downloadsCompleted.get() >= totalSourcesToProcess;
                boolean allParsesDone = parsesCompleted.get() >= parsesSubmitted.get();

                if (allDownloadsDone && allParsesDone) {
                    break; // Pipeline complete
                }

                // If no progress was made this iteration, sleep briefly to avoid busy-spin
                if (!madeProgress) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        progressBuilder.setStopped(true);
                    }
                }
            }

            long totalTime = System.currentTimeMillis() - pipelineStartTime;
            Timber.i("Pipeline complete in %ds: %d sources (%d up-to-date, %d updated, %d failed), %d accepted host rows",
                    totalTime / 1000, totalSourcesToProcess, upToDateCount.get(),
                    parsesCompleted.get(), failedCount.get(),
                    progressBuilder.getParsedHostCount());
            
            // Show user-friendly message if everything was already up-to-date
            if (upToDateCount.get() == totalSourcesToProcess && failedCount.get() == 0) {
                setState(R.string.status_no_update_found);
            }
            
            // Shutdown dedicated thread pools for this update
            stopPipelineWorkers();

        }

        int totalFailed = failedCount.get();
        MultiPhaseProgress finalProgress = progressBuilder.build();
        int totalDownloaded = finalProgress.downloadedCount;

        if (progressBuilder.isStopped()) {
            cleanupGeneration(writableDb, importGeneration);
            progressBuilder.setFinalizing(false);
            progressBuilder.setComplete(false);
            postMultiPhaseProgress(progressBuilder.build(), true);
            postIdleAfterTerminal();
            return false;
        }

        if (totalFailed == 0
                && changedSourceCount.get() == 0
                && upToDateCount.get() == totalSources) {
            progressBuilder.setFinalizing(true);
            progressBuilder.setCurrentLabel(null);
            postMultiPhaseProgress(progressBuilder.build(), true);
            long noChangeStartedMs = SystemClock.elapsedRealtime();
            FinalizeTimings noChangeTimings = finalizeNoChange(
                    importGeneration, disabledSourceIds, sourceCommits, sourceFailures,
                    runtimeRebuildRequired);
            this.updateAvailable.postValue(false);
            postProgress(totalSources, totalSources, null, 10000, 100);
            Timber.i("Pipeline no-change fast path: %d sources up-to-date, skipped carry-forward, "
                            + "generation activation, and cleanup in %dms (runtimeRebuild=%b, syncMs=%d).",
                    totalSources,
                    SystemClock.elapsedRealtime() - noChangeStartedMs,
                    runtimeRebuildRequired,
                    noChangeTimings.syncMs);
            progressBuilder.setFinalizing(false);
            progressBuilder.setComplete(true);
            postMultiPhaseProgress(progressBuilder.build(), true);
            postIdleAfterTerminal();
            return true;
        }

        progressBuilder.setFinalizing(true);
        progressBuilder.setCurrentLabel(null);
        postMultiPhaseProgress(progressBuilder.build(), true);
        long carryForwardStartedMs = SystemClock.elapsedRealtime();
        for (HostsSource source : failedCarryForwardSources) {
            try {
                if (!carryForwardPreviousGeneration(source, importGeneration)) {
                    generationUnsafe.set(true);
                }
            } catch (SQLiteException exception) {
                Timber.w(exception, "Failed to carry forward source %s.", source.getUrl());
                sourceFailures.add(SourceFailure.of(source.getId(), exception));
                generationUnsafe.set(true);
            }
        }
        for (HostsSource source : deferredCarryForwardSources) {
            try {
                if (!carryForwardPreviousGeneration(source, importGeneration, sqlDeduper)) {
                    generationUnsafe.set(true);
                }
            } catch (SQLiteException exception) {
                Timber.w(exception, "Failed to carry forward source %s.", source.getUrl());
                sourceFailures.add(SourceFailure.of(source.getId(), exception));
                generationUnsafe.set(true);
            }
        }
        long carryForwardMs = SystemClock.elapsedRealtime() - carryForwardStartedMs;

        if ((successfulSourceCount.get() == 0 && totalFailed > 0) || generationUnsafe.get()) {
            abortUnsafeGeneration(writableDb, importGeneration, sourceFailures);
            postMultiPhaseProgress(MultiPhaseProgress.idle());
            throw new HostErrorException(DOWNLOAD_FAILED);
        }

        FinalizeTimings finalizeTimings = finalizeActivatedGeneration(
                importGeneration, disabledSourceIds, sourceCommits, sourceFailures);
        this.updateAvailable.postValue(false);
        postProgress(totalSources, totalSources, null, 10000, 100);
        Timber.i("Pipeline update complete: %d sources checked, %d downloaded, %d failed",
                finalProgress.checkedCount, totalDownloaded, totalFailed);
        Timber.i("Pipeline finalize perf: deferredCarryForwardSources=%d carryForwardMs=%d "
                        + "cleanupMs=%d syncMs=%d",
                deferredCarryForwardSources.size() + failedCarryForwardSources.size(),
                carryForwardMs,
                finalizeTimings.cleanupMs, finalizeTimings.syncMs);
        progressBuilder.setFinalizing(false);
        progressBuilder.setComplete(true);
        postMultiPhaseProgress(progressBuilder.build(), true);
        postIdleAfterTerminal();
        return true;
        } finally {
            stopPipelineWorkers();
            for (DownloadResult download : downloads) {
                download.cleanup();
            }
            if (sqlDeduperRef[0] != null) {
                sqlDeduperRef[0].drop();
            }
            if (writableDb != null) {
                restoreImportPragmas(writableDb);
            }
            finishUpdateOperation();
        }
    }

    private void stopPipelineWorkers() {
        ExecutorService downloadPool;
        ExecutorService parsePool;
        synchronized (this) {
            downloadPool = this.currentDownloadPool;
            parsePool = this.currentParsePool;
        }
        if (downloadPool != null) downloadPool.shutdownNow();
        if (parsePool != null) parsePool.shutdownNow();
        for (Call call : this.activeDownloads) call.cancel();
        boolean interrupted = Thread.interrupted();
        for (ExecutorService pool : new ExecutorService[]{downloadPool, parsePool}) {
            if (pool == null) continue;
            while (!pool.isTerminated()) {
                try {
                    pool.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static int ensureAndGetActiveGeneration(@NonNull SupportSQLiteDatabase db) {
        // Ensure hosts_meta exists and has a single row (id=0).
        db.execSQL("CREATE TABLE IF NOT EXISTS `hosts_meta` (`id` INTEGER NOT NULL, `active_generation` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("INSERT OR IGNORE INTO `hosts_meta` (`id`, `active_generation`) VALUES (0, 0)");
        android.database.Cursor c = db.query("SELECT `active_generation` FROM `hosts_meta` WHERE `id` = 0 LIMIT 1");
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
        } finally {
            c.close();
        }
        return 0;
    }

    private static void setActiveGeneration(@NonNull SupportSQLiteDatabase db, int generation) {
        db.execSQL("UPDATE `hosts_meta` SET `active_generation` = " + generation + " WHERE `id` = 0");
    }

    private static void cleanupNonActiveGenerations(@NonNull SupportSQLiteDatabase db, int activeGeneration) {
        db.execSQL("DELETE FROM `hosts_lists` WHERE `source_id` != 1 AND `generation` != " + activeGeneration);
        db.execSQL("DELETE FROM `root_host_entries_stage` WHERE `source_id` != 1 " +
                "AND `generation` != " + activeGeneration);
    }

    private static void cleanupGeneration(@NonNull SupportSQLiteDatabase db, int generation) {
        db.execSQL("DELETE FROM `hosts_lists` WHERE `source_id` != 1 AND `generation` = " + generation);
        db.execSQL("DELETE FROM `root_host_entries_stage` WHERE `source_id` != 1 " +
                "AND `generation` = " + generation);
    }

    private FinalizeTimings finalizeActivatedGeneration(
            int importGeneration,
            @NonNull List<Integer> disabledSourceIds,
            @NonNull List<SourceCommit> sourceCommits,
            @NonNull List<SourceFailure> sourceFailures) {
        setState(R.string.status_sync_database);
        final long[] cleanupMs = {0L};
        final long[] syncMs = {0L};
        this.database.runInTransaction(() -> {
            SupportSQLiteDatabase db = this.database.getOpenHelper().getWritableDatabase();
            applyDisabledSourceFinalization(disabledSourceIds);
            applySourceFailures(sourceFailures);
            applySourceCommits(sourceCommits);
            setActiveGeneration(db, importGeneration);
            long cleanupStartedMs = SystemClock.elapsedRealtime();
            cleanupNonActiveGenerations(db, importGeneration);
            cleanupMs[0] = SystemClock.elapsedRealtime() - cleanupStartedMs;
            this.hostEntryDao.invalidateRootExportMaterializedCache();
        });
        invalidateVpnRulesCache();
        scheduleRuntimeCacheRefresh();
        return new FinalizeTimings(cleanupMs[0], syncMs[0]);
    }

    private FinalizeTimings finalizeNoChange(
            int importGeneration,
            @NonNull List<Integer> disabledSourceIds,
            @NonNull List<SourceCommit> sourceCommits,
            @NonNull List<SourceFailure> sourceFailures,
            boolean runtimeRebuildRequired) {
        setState(R.string.status_sync_database);
        final long[] syncMs = {0L};
        this.database.runInTransaction(() -> {
            SupportSQLiteDatabase db = this.database.getOpenHelper().getWritableDatabase();
            cleanupGeneration(db, importGeneration);
            applyDisabledSourceFinalization(disabledSourceIds);
            applySourceFailures(sourceFailures);
            applySourceCommits(sourceCommits);
            if (runtimeRebuildRequired) {
                this.hostEntryDao.invalidateRootExportMaterializedCache();
            }
        });
        if (runtimeRebuildRequired) {
            invalidateVpnRulesCache();
            scheduleRuntimeCacheRefresh();
        }
        return new FinalizeTimings(0L, syncMs[0]);
    }

    private void abortUnsafeGeneration(
            @NonNull SupportSQLiteDatabase db,
            int importGeneration,
            @NonNull List<SourceFailure> sourceFailures) {
        this.database.runInTransaction(() -> {
            cleanupGeneration(db, importGeneration);
            applySourceFailures(sourceFailures);
        });
    }

    private void applyDisabledSourceFinalization(@NonNull List<Integer> disabledSourceIds) {
        SupportSQLiteDatabase db = this.database.getOpenHelper().getWritableDatabase();
        for (int sourceId : disabledSourceIds) {
            this.hostListItemDao.clearSourceHosts(sourceId);
            clearRootExportStageForSource(db, sourceId);
            this.hostsSourceDao.clearProperties(sourceId);
        }
    }

    private void applySourceCommits(@NonNull List<SourceCommit> commits) {
        for (SourceCommit commit : commits) {
            if (commit.replaceEntityTag || commit.entityTag != null) {
                this.hostsSourceDao.updateEntityTag(commit.sourceId, commit.entityTag);
            }
            if (commit.localModificationDate != null && commit.onlineModificationDate != null) {
                this.hostsSourceDao.updateModificationDates(
                        commit.sourceId, commit.localModificationDate,
                        commit.onlineModificationDate);
            }
            if (commit.sizeGeneration >= 0) {
                this.hostsSourceDao.updateSizeForGeneration(
                        commit.sourceId, commit.sizeGeneration);
            }
            if (commit.clearDownloadError) {
                this.hostsSourceDao.clearDownloadError(commit.sourceId);
            }
        }
    }

    private void applySourceFailures(@NonNull List<SourceFailure> failures) {
        for (SourceFailure failure : failures) {
            this.hostsSourceDao.updateDownloadError(failure.sourceId, failure.errorMessage);
        }
    }

    private void finalizeStagedSourceGeneration(
            int sourceId, int activeGeneration, int stagingGeneration) {
        finalizeStagedSourceGeneration(sourceId, activeGeneration, stagingGeneration, null);
    }

    private void finalizeStagedSourceGeneration(
            int sourceId,
            int activeGeneration,
            int stagingGeneration,
            @Nullable SourceCommit sourceCommit) {
        setState(R.string.status_sync_database);
        this.database.runInTransaction(() -> {
            SupportSQLiteDatabase db = this.database.getOpenHelper().getWritableDatabase();
            promoteStagedSourceGeneration(sourceId, activeGeneration, stagingGeneration);
            if (sourceCommit != null) {
                applySourceCommits(Collections.singletonList(sourceCommit));
            }
            this.hostEntryDao.invalidateRootExportMaterializedCache();
        });
        invalidateVpnRulesCache();
        scheduleRuntimeCacheRefresh();
    }

    private void finalizeStagedSourceGenerations(@NonNull List<TargetedSourceUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }
        setState(R.string.status_sync_database);
        final boolean[] runtimeRebuildRequired = {false};
        this.database.runInTransaction(() -> {
            List<Integer> disabledSourceIds = new ArrayList<>();
            List<SourceCommit> commits = new ArrayList<>();
            for (TargetedSourceUpdate update : updates) {
                if (update.clearSource) {
                    disabledSourceIds.add(update.sourceId);
                }
                if (update.hasStagedGeneration()) {
                    promoteStagedSourceGeneration(
                            update.sourceId, update.activeGeneration, update.stagingGeneration);
                }
                if (update.sourceCommit != null) {
                    commits.add(update.sourceCommit);
                }
                runtimeRebuildRequired[0] |= update.requiresRuntimeRebuild;
            }
            applyDisabledSourceFinalization(disabledSourceIds);
            applySourceCommits(commits);
            if (runtimeRebuildRequired[0]) {
                this.hostEntryDao.invalidateRootExportMaterializedCache();
            }
        });
        if (runtimeRebuildRequired[0]) {
            invalidateVpnRulesCache();
            scheduleRuntimeCacheRefresh();
        }
    }

    private void cleanupStagedSourceGenerations(@NonNull List<TargetedSourceUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }
        this.database.runInTransaction(() -> {
            SupportSQLiteDatabase db = this.database.getOpenHelper().getWritableDatabase();
            for (TargetedSourceUpdate update : updates) {
                if (update.hasStagedGeneration()) {
                    this.hostListItemDao.clearSourceHostsForGeneration(
                            update.sourceId, update.stagingGeneration);
                    clearRootExportStageForSourceGeneration(
                            db, update.sourceId, update.stagingGeneration);
                }
            }
        });
    }

    private static final class FinalizeTimings {
        final long cleanupMs;
        final long syncMs;

        FinalizeTimings(long cleanupMs, long syncMs) {
            this.cleanupMs = cleanupMs;
            this.syncMs = syncMs;
        }
    }

    private static final class SourceCommit {
        final int sourceId;
        @Nullable
        final String entityTag;
        @Nullable
        final ZonedDateTime localModificationDate;
        @Nullable
        final ZonedDateTime onlineModificationDate;
        final int sizeGeneration;
        final boolean clearDownloadError;
        final boolean replaceEntityTag;

        private SourceCommit(
                int sourceId,
                @Nullable String entityTag,
                @Nullable ZonedDateTime localModificationDate,
                @Nullable ZonedDateTime onlineModificationDate,
                int sizeGeneration,
                boolean clearDownloadError,
                boolean replaceEntityTag) {
            this.sourceId = sourceId;
            this.entityTag = entityTag;
            this.localModificationDate = localModificationDate;
            this.onlineModificationDate = onlineModificationDate;
            this.sizeGeneration = sizeGeneration;
            this.clearDownloadError = clearDownloadError;
            this.replaceEntityTag = replaceEntityTag;
        }

        static SourceCommit changed(
                int sourceId,
                @Nullable String entityTag,
                @NonNull ZonedDateTime localModificationDate,
                @NonNull ZonedDateTime onlineModificationDate,
                int sizeGeneration) {
            return new SourceCommit(
                    sourceId, entityTag, localModificationDate, onlineModificationDate,
                    sizeGeneration, true, true);
        }

        static SourceCommit unchanged(int sourceId) {
            return new SourceCommit(sourceId, null, null, null, -1, true, false);
        }

        static SourceCommit unchanged(
                int sourceId,
                @Nullable String entityTag,
                @NonNull ZonedDateTime localModificationDate,
                @NonNull ZonedDateTime onlineModificationDate,
                int sizeGeneration) {
            return new SourceCommit(
                    sourceId, entityTag, localModificationDate, onlineModificationDate,
                    sizeGeneration, true, false);
        }
    }

    private static final class SourceFailure {
        final int sourceId;
        @NonNull
        final String errorMessage;

        private SourceFailure(int sourceId, @NonNull String errorMessage) {
            this.sourceId = sourceId;
            this.errorMessage = errorMessage;
        }

        static SourceFailure of(int sourceId, @NonNull Throwable throwable) {
            String message = throwable.getMessage() != null
                    ? throwable.getMessage() : throwable.getClass().getSimpleName();
            return of(sourceId, message);
        }

        static SourceFailure of(int sourceId, @NonNull String errorMessage) {
            return new SourceFailure(sourceId, errorMessage);
        }
    }

    private static final class TargetedSourceUpdate {
        final int sourceId;
        final int activeGeneration;
        final int stagingGeneration;
        @Nullable
        final SourceCommit sourceCommit;
        final boolean requiresRuntimeRebuild;
        final boolean clearSource;

        private TargetedSourceUpdate(
                int sourceId,
                int activeGeneration,
                int stagingGeneration,
                @Nullable SourceCommit sourceCommit,
                boolean requiresRuntimeRebuild,
                boolean clearSource) {
            this.sourceId = sourceId;
            this.activeGeneration = activeGeneration;
            this.stagingGeneration = stagingGeneration;
            this.sourceCommit = sourceCommit;
            this.requiresRuntimeRebuild = requiresRuntimeRebuild;
            this.clearSource = clearSource;
        }

        static TargetedSourceUpdate changed(
                int sourceId,
                int activeGeneration,
                int stagingGeneration,
                @NonNull SourceCommit sourceCommit) {
            return new TargetedSourceUpdate(
                    sourceId, activeGeneration, stagingGeneration, sourceCommit, true, false);
        }

        static TargetedSourceUpdate runtimeOnly() {
            return new TargetedSourceUpdate(-1, -1, -1, null, true, false);
        }

        static TargetedSourceUpdate disabled(int sourceId) {
            return new TargetedSourceUpdate(sourceId, -1, -1, null, true, true);
        }

        static TargetedSourceUpdate metadataOnly(@NonNull SourceCommit sourceCommit) {
            return new TargetedSourceUpdate(
                    sourceCommit.sourceId, -1, -1, sourceCommit, false, false);
        }

        boolean hasStagedGeneration() {
            return this.stagingGeneration >= 0;
        }

        boolean requiresFinalization() {
            return hasStagedGeneration() || this.sourceCommit != null || this.clearSource;
        }
    }

    private boolean carryForwardPreviousGeneration(@NonNull HostsSource source) {
        return carryForwardPreviousGeneration(source, this.currentImportGeneration);
    }

    private boolean carryForwardPreviousGeneration(@NonNull HostsSource source, int importGeneration) {
        return carryForwardPreviousGeneration(source, importGeneration, null);
    }

    private boolean carryForwardPreviousGeneration(
            @NonNull HostsSource source,
            int importGeneration,
            @Nullable SqlUpdateDeduper sqlDeduper) {
        int oldGeneration = importGeneration - 1;
        if (oldGeneration < 0) {
            return false;
        }
        int activeRows = this.hostListItemDao.countSourceHostsForGeneration(source.getId(), oldGeneration);
        if (activeRows <= 0 && source.getLocalModificationDate() == null) {
            Timber.w("Cannot carry forward source %s: no previous active rows.", source.getUrl());
            return false;
        }
        if (sqlDeduper != null) {
            sqlDeduper.copyUnseenSourceGeneration(source.getId(), oldGeneration, importGeneration);
            int copiedRows = this.hostListItemDao.countSourceHostsForGeneration(
                    source.getId(), importGeneration);
            if (activeRows > 0 && copiedRows == 0) {
                Timber.w("Falling back to direct carry-forward for source %s.", source.getUrl());
                this.hostListItemDao.copySourceGenerationReplacingTarget(
                        source.getId(), oldGeneration, importGeneration);
                copyRootExportStageFromHostsListsReplacingTarget(source.getId(), importGeneration);
            }
        } else {
            this.hostListItemDao.copySourceGenerationReplacingTarget(
                    source.getId(), oldGeneration, importGeneration);
            copyRootExportStageFromHostsListsReplacingTarget(
                    source.getId(), importGeneration);
        }
        return true;
    }

    private void promoteStagedSourceGeneration(
            int sourceId, int activeGeneration, int stagingGeneration) {
        this.hostListItemDao.replaceSourceGeneration(sourceId, stagingGeneration, activeGeneration);
        SupportSQLiteDatabase db = this.database.getOpenHelper().getWritableDatabase();
        clearRootExportStageForSourceGeneration(db, sourceId, activeGeneration);
        db.execSQL("UPDATE `root_host_entries_stage` SET `generation` = " + activeGeneration +
                " WHERE `source_id` = " + sourceId + " AND `generation` = " +
                stagingGeneration);
    }

    private static void clearRootExportStageForSource(
            @NonNull SupportSQLiteDatabase db, int sourceId) {
        db.execSQL("DELETE FROM `root_host_entries_stage` WHERE `source_id` = " + sourceId);
    }

    private static void clearRootExportStageForSourceGeneration(
            @NonNull SupportSQLiteDatabase db, int sourceId, int generation) {
        db.execSQL("DELETE FROM `root_host_entries_stage` WHERE `source_id` = " + sourceId +
                " AND `generation` = " + generation);
    }

    private void copyRootExportStageFromHostsListsReplacingTarget(
            int sourceId, int generation) {
        SupportSQLiteDatabase db = this.database.getOpenHelper().getWritableDatabase();
        clearRootExportStageForSourceGeneration(db, sourceId, generation);
        db.execSQL("INSERT INTO `root_host_entries_stage` " +
                "(`host`, `reverse_host`, `type`, `redirection`, `source_id`, `generation`) " +
                "SELECT `host`, `reverse_host`, `type`, `redirection`, `source_id`, " +
                "`generation` FROM `hosts_lists` WHERE `source_id` = " + sourceId +
                " AND `generation` = " + generation +
                " AND `enabled` = 1 AND `type` IN (0, 2)");
    }

    private static void applyBalancedImportPragmas(@NonNull SupportSQLiteDatabase db) {
        try {
            db.execSQL("PRAGMA synchronous=NORMAL");
        } catch (Throwable t) {
            Timber.w(t, "Failed to apply import pragmas");
        }
    }

    private static void restoreImportPragmas(@NonNull SupportSQLiteDatabase db) {
        try {
            db.execSQL("PRAGMA synchronous=FULL");
        } catch (Throwable t) {
            Timber.w(t, "Failed to restore import pragmas");
        }
    }

    /**
     * Retrieve a single hosts source (download/read + parse) and synchronize entries.
     * This is used for per-list "Update" actions in the UI.
     *
     * @param sourceId The source id to update.
     * @throws HostErrorException If the hosts source could not be downloaded.
     */
    public void retrieveHostsSource(int sourceId) throws HostErrorException {
        beginUpdateOperation("retrieveHostsSource");
        try {
            retrieveHostsSource(sourceId, true);
        } finally {
            finishUpdateOperation();
        }
    }

    /**
     * Retrieve several hosts sources and rebuild runtime entries once at the end.
     *
     * @param sourceIds The source ids to update.
     * @throws HostErrorException If a hosts source could not be downloaded.
     */
    public void retrieveHostsSources(@NonNull List<Integer> sourceIds) throws HostErrorException {
        if (sourceIds.isEmpty()) {
            return;
        }
        List<Integer> uniqueSourceIds = new ArrayList<>(new LinkedHashSet<>(sourceIds));
        beginUpdateOperation("retrieveHostsSources(list)");
        List<TargetedSourceUpdate> finalizationUpdates = new ArrayList<>();
        boolean runtimeRebuildRequired = false;
        try {
            for (int sourceId : uniqueSourceIds) {
                TargetedSourceUpdate update = retrieveHostsSource(sourceId, false);
                if (update == null) {
                    continue;
                }
                runtimeRebuildRequired |= update.requiresRuntimeRebuild;
                if (update.requiresFinalization()) {
                    finalizationUpdates.add(update);
                }
            }
            if (!finalizationUpdates.isEmpty()) {
                finalizeStagedSourceGenerations(finalizationUpdates);
            } else if (runtimeRebuildRequired) {
                syncHostEntries();
            }
            this.updateAvailable.postValue(false);
            postProgress(uniqueSourceIds.size(), uniqueSourceIds.size(), null, 10000, 100);
        } catch (HostErrorException | RuntimeException e) {
            cleanupStagedSourceGenerations(finalizationUpdates);
            if (runtimeRebuildRequired) {
                syncHostEntries();
            }
            throw e;
        } finally {
            finishUpdateOperation();
        }
    }

    private TargetedSourceUpdate retrieveHostsSource(int sourceId, boolean syncAfter)
            throws HostErrorException {
        // Check connection status
        if (isDeviceOffline()) {
            throw new HostErrorException(NO_CONNECTION);
        }
        HostsSource source = this.hostsSourceDao.getById(sourceId).orElse(null);
        if (source == null || source.getId() == HostsSource.USER_SOURCE_ID) {
            return null;
        }
        postProgress(0, 1, source.getLabel(), 0, 0);
        if (!source.isEnabled()) {
            if (syncAfter) {
                // Keep DB clean for disabled sources
                this.hostListItemDao.clearSourceHosts(sourceId);
                clearRootExportStageForSource(
                        this.database.getOpenHelper().getWritableDatabase(), sourceId);
                this.hostsSourceDao.clearProperties(sourceId);
                syncHostEntries();
                this.updateAvailable.postValue(false);
                postProgress(1, 1, source.getLabel(), 10000, 100);
            }
            return syncAfter ? null : TargetedSourceUpdate.disabled(sourceId);
        }

        // Compute current date in UTC timezone
        ZonedDateTime now = ZonedDateTime.now();

        // Download/read + parse (URL uses conditional GET; no HEAD pre-check)
        final SupportSQLiteDatabase writableDb = this.database.getOpenHelper().getWritableDatabase();
        applyBalancedImportPragmas(writableDb);
        int activeGeneration = ensureAndGetActiveGeneration(writableDb);
        int stagingGeneration = activeGeneration + 1;
        // Single-source update: parse into a staging generation, then promote on success.
        this.currentImportGeneration = stagingGeneration;
        SqlUpdateDeduper sqlDeduper = new SqlUpdateDeduper(writableDb);
        boolean runtimeRebuiltWithPromotion = false;
        TargetedSourceUpdate stagedUpdate = null;
        try {
            switch (source.getType()) {
                case URL:
                    DownloadResult result = downloadToTempFile(source, (label, withinFraction) -> {
                        int withinPercent = (int) Math.floor(withinFraction * 100.0);
                        int basisPoints = (int) Math.floor(Math.min(0.999d, Math.max(0d, withinFraction)) * 10000.0);
                        postProgress(0, 1, label, basisPoints, withinPercent);
                    });
                    try {
                        if (!result.success) {
                            throw new IOException(result.errorMessage != null
                                    ? result.errorMessage : "Download failed");
                        }
                        if (!result.notModified) {
                            try (BufferedReader reader = result.openReader()) {
                                parseSourceInputStream(source, reader, stagingGeneration, sqlDeduper);
                            }
                            ZonedDateTime onlineModificationDate = result.onlineModificationDate != null ? result.onlineModificationDate : now;
                            ZonedDateTime localModificationDate = onlineModificationDate.isAfter(now) ? onlineModificationDate : now;
                            SourceCommit commit = SourceCommit.changed(
                                    sourceId, result.entityTag, localModificationDate,
                                    onlineModificationDate, activeGeneration);
                            if (syncAfter) {
                                finalizeStagedSourceGeneration(
                                        sourceId, activeGeneration, stagingGeneration, commit);
                                runtimeRebuiltWithPromotion = true;
                            } else {
                                stagedUpdate = TargetedSourceUpdate.changed(
                                        sourceId, activeGeneration, stagingGeneration, commit);
                            }
                        } else {
                            // Preserve the last body-fetch date so repeated 304s cannot bypass
                            // the weekly unconditional refresh guard.
                            SourceCommit unchangedCommit = SourceCommit.unchanged(sourceId);
                            if (syncAfter) {
                                applySourceCommits(Collections.singletonList(unchangedCommit));
                            } else {
                                stagedUpdate = TargetedSourceUpdate.metadataOnly(unchangedCommit);
                            }
                        }
                    } finally {
                        result.cleanup();
                    }
                    break;
                case FILE:
                    readSourceFile(source, stagingGeneration, sqlDeduper);
                    ZonedDateTime onlineModificationDateFile = getHostsSourceLastUpdate(source);
                    if (onlineModificationDateFile == null) {
                        onlineModificationDateFile = now;
                    }
                    ZonedDateTime localModificationDateFile = onlineModificationDateFile.isAfter(now) ? onlineModificationDateFile : now;
                    SourceCommit fileCommit = SourceCommit.changed(
                            sourceId, null, localModificationDateFile,
                            onlineModificationDateFile, activeGeneration);
                    if (syncAfter) {
                        finalizeStagedSourceGeneration(
                                sourceId, activeGeneration, stagingGeneration, fileCommit);
                        runtimeRebuiltWithPromotion = true;
                    } else {
                        stagedUpdate = TargetedSourceUpdate.changed(
                                sourceId, activeGeneration, stagingGeneration, fileCommit);
                    }
                    break;
                default:
                    Timber.w("Hosts source type is not supported.");
                    return null;
            }
        } catch (IOException | RuntimeException e) {
            this.hostListItemDao.clearSourceHostsForGeneration(sourceId, stagingGeneration);
            clearRootExportStageForSourceGeneration(writableDb, sourceId, stagingGeneration);
            this.hostsSourceDao.updateDownloadError(sourceId,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            Timber.w(e, "Failed to retrieve host source %s.", source.getUrl());
            throw new HostErrorException(DOWNLOAD_FAILED);
        } finally {
            sqlDeduper.drop();
            restoreImportPragmas(writableDb);
        }

        if (syncAfter) {
            // Synchronize hosts entries when the source was unchanged.
            // Changed sources rebuild runtime entries atomically with promotion above.
            if (!runtimeRebuiltWithPromotion) {
                syncHostEntries();
            }
            // Mark no update available
            this.updateAvailable.postValue(false);
        }
        postProgress(1, 1, source.getLabel(), 10000, 100);
        return stagedUpdate;
    }

    /**
     * Synchronize hosts entries from current source states.
     */
    public void syncHostEntries() {
        setState(R.string.status_sync_database);
        SupportSQLiteDatabase db = this.database.getOpenHelper().getWritableDatabase();
        applyBalancedImportPragmas(db);
        try {
            rebuildRuntimeCaches(db);
        } finally {
            restoreImportPragmas(db);
        }
        invalidateVpnRulesCache();
    }

    private void rebuildRuntimeCaches(@NonNull SupportSQLiteDatabase db) {
        this.hostEntryDao.refreshStatsFromActiveGeneration();
        if (this.hostEntryDao.getActiveRuntimeRuleCountNow()
                > HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS) {
            this.hostEntryDao.rebuildFromActiveGeneration(db);
            return;
        }
        this.database.runInTransaction(() ->
                this.hostEntryDao.rebuildFromActiveGeneration(db));
    }

    private void invalidateVpnRulesCache() {
        Context appContext = this.context.getApplicationContext();
        if (appContext instanceof AdAwayApplication) {
            ((AdAwayApplication) appContext).invalidateVpnRulesCache();
        }
    }

    /**
     * Get the HTTP client to download hosts sources.
     * Configured for high parallelism with optimized dispatcher and connection pool.
     *
     * @return The HTTP client to download hosts sources.
     */
    @NonNull
    private synchronized OkHttpClient getHttpClient() {
        if (this.cachedHttpClient == null) {
            File cacheDir = new File(this.context.getCacheDir(), "okhttp");

            // Configure dispatcher for high parallelism
            okhttp3.Dispatcher dispatcher = new okhttp3.Dispatcher();
            dispatcher.setMaxRequests(DOWNLOAD_PARALLELISM * 2);  // e.g. 64
            dispatcher.setMaxRequestsPerHost(DOWNLOAD_PARALLELISM);  // e.g. 32

            // Configure connection pool to support parallel connections
            okhttp3.ConnectionPool connectionPool = new okhttp3.ConnectionPool(
                    DOWNLOAD_PARALLELISM * 2,  // Max idle connections
                    5, TimeUnit.MINUTES        // Keep-alive duration
            );

            this.cachedHttpClient = new OkHttpClient.Builder()
                    .dispatcher(dispatcher)
                    .connectionPool(connectionPool)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .cache(new Cache(cacheDir, CACHE_SIZE))
                    // Security hardening: Block HTTPS -> HTTP redirect downgrades.
                    .addNetworkInterceptor(chain -> {
                        Response response = chain.proceed(chain.request());
                        if (response.isRedirect()) {
                            String location = response.header("Location");
                            if (location != null && chain.request().url().scheme().equals("https")
                                    && location.startsWith("http:")) {
                                throw new IOException("Security: Blocked HTTPS to HTTP redirect to " + location);
                            }
                        }
                        return response;
                    })
                    .build();
        }
        return this.cachedHttpClient;
    }

    /**
     * Get HTTP client optimized for fast HEAD requests (check phase).
     * Uses aggressive timeouts since HEAD requests should be instant.
     */
    @NonNull
    private synchronized OkHttpClient getHeadClient() {
        if (this.cachedHeadClient == null) {
            // Configure for high parallelism HEAD requests
            okhttp3.Dispatcher dispatcher = new okhttp3.Dispatcher();
            dispatcher.setMaxRequests(64);  // High parallelism for HEAD
            dispatcher.setMaxRequestsPerHost(8);  // Multiple per host OK for HEAD

            okhttp3.ConnectionPool connectionPool = new okhttp3.ConnectionPool(
                    64,  // Max idle connections
                    1, TimeUnit.MINUTES  // Short keep-alive
            );

            this.cachedHeadClient = new OkHttpClient.Builder()
                    .dispatcher(dispatcher)
                    .connectionPool(connectionPool)
                    .connectTimeout(5, TimeUnit.SECONDS)  // Fast connect timeout
                    .readTimeout(10, TimeUnit.SECONDS)    // Fast read timeout
                    .writeTimeout(5, TimeUnit.SECONDS)    // Fast write timeout
                    .build();
        }
        return this.cachedHeadClient;
    }

    /**
     * Expose the cached HTTP client for UI helpers.
     * (Used by FilterLists.com directory importer.)
     */
    @NonNull
    public OkHttpClient getHttpClientForUi() {
        return getHttpClient();
    }

    /**
     * Get request builder for an hosts source.
     * All cache data available are filled into the headers.
     *
     * @param source The hosts source to get request builder.
     * @return The hosts source request builder.
     */
    private Request.Builder getRequestFor(HostsSource source) {
        Request.Builder request = new Request.Builder().url(source.getUrl());
        // ATK-10: Redirect-enabled sources must always be fetched fresh from the network.
        // An on-disk OkHttp cache file could be replaced by an attacker on a rooted device,
        // poisoning the redirect table. Force-network for these sources; ETag caching is
        // irrelevant since the content changes (redirect IPs can change any time).
        if (source.isRedirectEnabled()) {
            request = request.cacheControl(okhttp3.CacheControl.FORCE_NETWORK);
            return request;
        }
        // Max-age guard: if the source has not been successfully fetched in over 7 days,
        // skip cached ETag/Last-Modified headers and force a full re-fetch. This prevents
        // stale data persisting indefinitely when a server sends bad ETags or a source URL
        // is changed and the old cache metadata no longer matches the new content.
        ZonedDateTime localModificationDate = source.getLocalModificationDate();
        boolean cacheExpired = localModificationDate == null ||
                localModificationDate.isBefore(ZonedDateTime.now().minusDays(7)) ||
                source.getLastDownloadError() != null;
        if (cacheExpired) {
            request.cacheControl(okhttp3.CacheControl.FORCE_NETWORK);
        }
        if (!cacheExpired && source.getEntityTag() != null) {
            request = request.header(IF_NONE_MATCH_HEADER, source.getEntityTag());
        }
        if (!cacheExpired && source.getOnlineModificationDate() != null) {
            String lastModified = source.getOnlineModificationDate().format(RFC_1123_DATE_TIME);
            request = request.header(IF_MODIFIED_SINCE_HEADER, lastModified);
        }
        return request;
    }

    /**
     * Progress callback used while copying a downloaded URL source to a staging file.
     */
    private interface DownloadProgress {
        void onProgress(@NonNull String label, double withinFraction);
    }

    /**
     * Download a URL source to a temp file using a single conditional GET.
     * Returns 304/notModified when applicable (no temp file needed).
     */
    @NonNull
    private DownloadResult downloadToTempFile(@NonNull HostsSource source, @Nullable DownloadProgress progress) {
        String url = source.getUrl();
        setState(R.string.status_download_source, url);

        Request request = getRequestFor(source).build();
        File tmp = null;
        Call call = getHttpClient().newCall(request);
        this.activeDownloads.add(call);
        try (Response response = executeDownload(call)) {
            if (response.code() == HTTP_NOT_MODIFIED) {
                if (request.header(IF_NONE_MATCH_HEADER) == null
                        && request.header(IF_MODIFIED_SINCE_HEADER) == null) {
                    return DownloadResult.failed(source, "Unexpected HTTP 304 without a validator");
                }
                return DownloadResult.notModified(source);
            }
            if (!response.isSuccessful()) {
                return DownloadResult.failed(source, "HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                return DownloadResult.failed(source, "Empty response body");
            }

            String entityTag = response.header(ENTITY_TAG_HEADER);

            ZonedDateTime lastModified = null;
            String lastModifiedHeader = response.header(LAST_MODIFIED_HEADER);
            if (lastModifiedHeader != null) {
                try {
                    lastModified = ZonedDateTime.parse(lastModifiedHeader, RFC_1123_DATE_TIME);
                } catch (DateTimeParseException ignored) {
                    lastModified = null;
                }
            }

            // Write response to temp file
            tmp = File.createTempFile("adaway-src-", ".txt", this.context.getCacheDir());
            long contentLength = body.contentLength();
            if (contentLength > MAX_SOURCE_BYTES) {
                throw new IOException("Hosts source exceeds " + MAX_SOURCE_BYTES + " bytes");
            }
            long totalRead = 0L;
            try (InputStream in = body.byteStream();
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[32 * 1024];
                int read;
                while ((read = in.read(buf)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Hosts source download interrupted");
                    }
                    if (totalRead > MAX_SOURCE_BYTES - read) {
                        throw new IOException("Hosts source exceeds " + MAX_SOURCE_BYTES + " bytes");
                    }
                    out.write(buf, 0, read);
                    totalRead += read;
                    if (progress != null) {
                        double within;
                        if (contentLength > 0L) {
                            within = totalRead / (double) contentLength;
                        } else {
                            final double k = 750_000d;
                            within = 1d - Math.exp(-(totalRead / k));
                        }
                        progress.onProgress(source.getLabel(),
                                Math.min(0.999d, Math.max(0d, within)));
                    }
                }
                out.flush();
            }

            return DownloadResult.success(source, tmp, entityTag, lastModified);
        } catch (IOException | RuntimeException e) {
            Timber.w(e, "Download failed for %s", url);
            if (tmp != null) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return DownloadResult.failed(source, errMsg);
        } finally {
            this.activeDownloads.remove(call);
        }
    }

    private Response executeDownload(Call call) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            call.cancel();
            throw new IOException("Hosts source download interrupted");
        }
        return call.execute();
    }

    /**
     * Download result (URL source only).
     */
    private static final class DownloadResult {
        final HostsSource source;
        final boolean success;
        final boolean notModified;
        @Nullable
        final File tmpFile;
        @Nullable
        final String entityTag;
        @Nullable
        final ZonedDateTime onlineModificationDate;
        @Nullable
        final String errorMessage;

        private DownloadResult(@Nullable HostsSource source,
                               boolean success,
                               boolean notModified,
                               @Nullable File tmpFile,
                               @Nullable String entityTag,
                               @Nullable ZonedDateTime onlineModificationDate,
                               @Nullable String errorMessage) {
            this.source = source;
            this.success = success;
            this.notModified = notModified;
            this.tmpFile = tmpFile;
            this.entityTag = entityTag;
            this.onlineModificationDate = onlineModificationDate;
            this.errorMessage = errorMessage;
        }

        static DownloadResult notModified(@NonNull HostsSource source) {
            return new DownloadResult(source, true, true, null, null, source.getOnlineModificationDate(), null);
        }

        static DownloadResult failed(@NonNull HostsSource source) {
            return new DownloadResult(source, false, false, null, null, null, null);
        }

        static DownloadResult failed(@NonNull HostsSource source, @NonNull String errorMsg) {
            return new DownloadResult(source, false, false, null, null, null, errorMsg);
        }

        static DownloadResult success(@NonNull HostsSource source,
                                      @NonNull File tmpFile,
                                      @Nullable String entityTag,
                                      @Nullable ZonedDateTime onlineModificationDate) {
            return new DownloadResult(source, true, false, tmpFile, entityTag, onlineModificationDate, null);
        }

        BufferedReader openReader() throws IOException {
            if (tmpFile == null) throw new IOException("No temp file");
            return new BufferedReader(new InputStreamReader(new java.io.FileInputStream(tmpFile)));
        }

        void cleanup() {
            if (tmpFile != null) {
                //noinspection ResultOfMethodCallIgnored
                tmpFile.delete();
            }
        }
    }

    /**
     * Read a hosts source file and append it to the database.
     *
     * @param hostsSource The hosts source to copy.
     * @throws IOException If the hosts source could not be copied.
     */
    private void readSourceFile(HostsSource hostsSource, int generation) throws IOException {
        readSourceFile(hostsSource, generation, null);
    }

    private void readSourceFile(
            HostsSource hostsSource,
            int generation,
            @Nullable SqlUpdateDeduper sqlDeduper) throws IOException {
        // Get hosts file URI
        String hostsFileUrl = hostsSource.getUrl();
        Uri fileUri = Uri.parse(hostsFileUrl);
        Timber.v("Reading hosts source file: %s.", hostsFileUrl);
        // Set state to copying hosts source
        setState(R.string.status_read_source, hostsFileUrl);
        try (InputStream inputStream = this.context.getContentResolver().openInputStream(fileUri);
             InputStreamReader reader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            parseSourceInputStream(hostsSource, bufferedReader, generation, sqlDeduper);
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
    private void parseSourceInputStream(HostsSource hostsSource, BufferedReader reader, int generation) {
        parseSourceInputStream(hostsSource, reader, generation, null);
    }

    private void parseSourceInputStream(HostsSource hostsSource, BufferedReader reader,
                                        int generation,
                                        @Nullable SqlUpdateDeduper sqlDeduper) {
        setState(R.string.status_parse_source, hostsSource.getLabel());
        long startTime = System.currentTimeMillis();
        SupportSQLiteDatabase db = this.database.getOpenHelper().getWritableDatabase();
        int skippedCount = new SourceLoader(hostsSource, generation).parse(
                reader,
                this.hostListItemDao,
                db,
                count -> {
                    progressBuilder.addParsedHosts(count);
                    MultiPhaseProgress mp = progressBuilder.build();
                    postMultiPhaseProgress(mp);
                },
                sqlDeduper);
        this.hostsSourceDao.updateSkippedCount(hostsSource.getId(), skippedCount);
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
        Timber.d("Source model state: %s.", state);
        this.state.postValue(state);
    }

    private void postProgress(int done, int total, @Nullable String currentLabel, int basisPoints, int currentSourcePercent) {
        // Legacy single-bar progress - still used for backward compatibility
        this.progress.postValue(new Progress(done, total, currentLabel, basisPoints, currentSourcePercent));
    }

    private void postMultiPhaseProgress(MultiPhaseProgress mp) {
        postMultiPhaseProgress(mp, false);
    }

    private void postMultiPhaseProgress(MultiPhaseProgress mp, boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!shouldPublishFilterOperation(mp, now, force)) {
            return;
        }

        this.progress.postValue(mp.toLegacyProgress());
        FilterOperationState operationState = FilterOperationState.fromMultiPhaseProgress(
                mp, this.filterOperationStartedElapsedMs, now);
        this.mainHandler.post(() -> {
            this.multiPhaseProgress.setValue(mp);
            this.filterOperationState.setValue(operationState);
        });
    }

    private void postIdleAfterTerminal() {
        int token = this.terminalIdleToken.incrementAndGet();
        this.mainHandler.postDelayed(() -> {
            if (this.terminalIdleToken.get() == token && !this.updateInProgress.get()) {
                postMultiPhaseProgress(MultiPhaseProgress.idle(), true);
            }
        }, 750L);
    }

    private synchronized void resetFilterOperationPublisher() {
        this.filterOperationStartedElapsedMs = 0L;
        this.lastFilterOperationEmitElapsedMs = 0L;
        this.lastFilterOperationPercent = -1;
        this.lastFilterOperationActive = false;
    }

    private synchronized boolean shouldPublishFilterOperation(
            @NonNull MultiPhaseProgress progress, long nowElapsedMs, boolean force) {
        boolean active = progress.isActive();
        if (active && !this.lastFilterOperationActive) {
            this.filterOperationStartedElapsedMs = nowElapsedMs;
        }

        int percent = active ? progress.getOverallPercent() : 0;
        boolean shouldPublish = force
                || active != this.lastFilterOperationActive
                || percent != this.lastFilterOperationPercent
                || !active
                || progress.isPaused
                || progress.isStopped
                || progress.isFinalizing
                || progress.isComplete
                || nowElapsedMs - this.lastFilterOperationEmitElapsedMs
                >= FILTER_OPERATION_PROGRESS_THROTTLE_MS;

        if (!shouldPublish) {
            return false;
        }

        this.lastFilterOperationActive = active;
        this.lastFilterOperationPercent = percent;
        this.lastFilterOperationEmitElapsedMs = nowElapsedMs;
        if (!active) {
            this.filterOperationStartedElapsedMs = 0L;
        }
        return true;
    }

    /**
     * Simple progress value object for UI (legacy single-bar).
     */
    public static final class Progress {
        public final int done;
        public final int total;
        @Nullable
        public final String currentLabel;
        public final int basisPoints;
        public final int currentSourcePercent;

        public Progress(int done, int total, @Nullable String currentLabel, int basisPoints, int currentSourcePercent) {
            this.done = Math.max(0, done);
            this.total = Math.max(0, total);
            this.currentLabel = currentLabel;
            this.basisPoints = Math.max(0, Math.min(10000, basisPoints));
            this.currentSourcePercent = Math.max(0, Math.min(100, currentSourcePercent));
        }

        public static Progress idle() {
            return new Progress(0, 0, null, 0, 0);
        }

        public boolean isActive() {
            return total > 0 && basisPoints < 10000;
        }
    }

    /**
     * Multi-phase progress for detailed UI with separate bars for Check/Download/Parse.
     */
    public static final class MultiPhaseProgress {
        // Check phase
        public final int checkedCount;
        public final int totalToCheck;

        // Download phase
        public final int downloadedCount;
        public final int totalToDownload;  // Only sources needing update
        public final int downloadQueued;   // Waiting to download

        // Parse phase
        public final int parsedCount;
        public final int totalToParse;
        public final int parseQueued;      // Waiting to parse
        public final long parsedHostCount; // Live count of accepted host rows parsed.

        // Scheduler info
        @Nullable
        public final String schedulerTaskName;
        public final boolean isPaused;
        public final boolean isStopped;
        public final boolean isFinalizing;
        public final boolean isComplete;

        // Current activity
        @Nullable
        public final String currentLabel;

        // Monotonic percentages (never decrease)
        public final int monotonicCheckPercent;
        public final int monotonicDownloadPercent;
        public final int monotonicParsePercent;
        public final int monotonicOverallPercent;

        public MultiPhaseProgress(
                int checkedCount, int totalToCheck,
                int downloadedCount, int totalToDownload, int downloadQueued,
                int parsedCount, int totalToParse, int parseQueued,
                long parsedHostCount,
                @Nullable String schedulerTaskName, boolean isPaused, boolean isStopped,
                boolean isFinalizing, boolean isComplete,
                @Nullable String currentLabel,
                int monotonicCheckPercent, int monotonicDownloadPercent,
                int monotonicParsePercent, int monotonicOverallPercent) {
            this.checkedCount = checkedCount;
            this.totalToCheck = totalToCheck;
            this.downloadedCount = downloadedCount;
            this.totalToDownload = totalToDownload;
            this.downloadQueued = downloadQueued;
            this.parsedCount = parsedCount;
            this.totalToParse = totalToParse;
            this.parseQueued = parseQueued;
            this.parsedHostCount = parsedHostCount;
            this.schedulerTaskName = schedulerTaskName;
            this.isPaused = isPaused;
            this.isStopped = isStopped;
            this.isFinalizing = isFinalizing;
            this.isComplete = isComplete;
            this.currentLabel = currentLabel;
            this.monotonicCheckPercent = monotonicCheckPercent;
            this.monotonicDownloadPercent = monotonicDownloadPercent;
            this.monotonicParsePercent = monotonicParsePercent;
            this.monotonicOverallPercent = monotonicOverallPercent;
        }

        public static MultiPhaseProgress idle() {
            return new MultiPhaseProgress(0, 0, 0, 0, 0, 0, 0, 0, 0, null, false,
                    false, false, false, null, 0, 0, 0, 0);
        }

        public boolean isActive() {
            // Active if any phase has work remaining (using >= for safety since counts can exceed totals)
            return totalToCheck > 0 && (isStopped || isFinalizing || isComplete
                    || getCheckPercent() < 100 || getDownloadPercent() < 100
                    || getParsePercent() < 100);
        }

        public int getCheckPercent() {
            return monotonicCheckPercent;
        }

        public double getCheckPercentDouble() {
            return monotonicCheckPercent;
        }

        public int getDownloadPercent() {
            return monotonicDownloadPercent;
        }

        public double getDownloadPercentDouble() {
            // Return actual double percentage from raw counts for precision
            return totalToDownload > 0 ? Math.min(100.0, (downloadedCount * 100.0) / totalToDownload) : 0.0;
        }

        public int getParsePercent() {
            return monotonicParsePercent;
        }

        public double getParsePercentDouble() {
            // Return actual double percentage from raw counts for precision.
            // If nothing needs parsing (totalToParse==0), treat parse as complete once the download stage is complete.
            if (totalToParse > 0) {
                return Math.min(100.0, (parsedCount * 100.0) / totalToParse);
            }
            return (totalToDownload > 0 && downloadedCount >= totalToDownload) ? 100.0 : 0.0;
        }

        public int getOverallPercent() {
            return monotonicOverallPercent;
        }

        public double getOverallPercentDouble() {
            // Calculate weighted average from actual double percentages for precision
            double checkPct = totalToCheck > 0 ? Math.min(100.0, (checkedCount * 100.0) / totalToCheck) : 0.0;
            double downloadPct = totalToDownload > 0 ? Math.min(100.0, (downloadedCount * 100.0) / totalToDownload) : 0.0;
            double parsePct = totalToParse > 0 ? Math.min(100.0, (parsedCount * 100.0) / totalToParse)
                    : (totalToDownload > 0 && downloadedCount >= totalToDownload) ? 100.0 : 0.0;
            return Math.min(100.0, checkPct * 0.30 + downloadPct * 0.40 + parsePct * 0.30);
        }

        /** Convert to legacy Progress for backward compatibility. */
        public Progress toLegacyProgress() {
            // If multi-phase progress is idle/inactive, ensure legacy progress is also idle.
            // Otherwise HomeActivity's legacy observer will show "Updating sources: 0/1" forever.
            if (totalToCheck <= 0) {
                return Progress.idle();
            }
            int total = Math.max(1, totalToCheck);
            int done = checkedCount;
            int basisPoints = total > 0 ? (done * 10000 / total) : 0;
            return new Progress(done, total, currentLabel, basisPoints, getDownloadPercent());
        }
    }

    /**
     * Builder for updating MultiPhaseProgress atomically.
     */
    public static final class MultiPhaseProgressBuilder {
        private final AtomicInteger checkedCount = new AtomicInteger(0);
        private final AtomicInteger totalToCheck = new AtomicInteger(0);
        private final AtomicInteger downloadedCount = new AtomicInteger(0);
        private final AtomicInteger totalToDownload = new AtomicInteger(0);
        private final AtomicInteger downloadQueued = new AtomicInteger(0);
        private final AtomicInteger parsedCount = new AtomicInteger(0);
        private final AtomicInteger totalToParse = new AtomicInteger(0);
        private final AtomicInteger parseQueued = new AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicLong parsedHostCount = new java.util.concurrent.atomic.AtomicLong(0);
        // High water marks - percentages NEVER decrease
        private final AtomicInteger maxCheckPercent = new AtomicInteger(0);
        private final AtomicInteger maxDownloadPercent = new AtomicInteger(0);
        private final AtomicInteger maxParsePercent = new AtomicInteger(0);
        private final AtomicInteger maxOverallPercent = new AtomicInteger(0);
        private volatile String schedulerTaskName;
        private volatile boolean isPaused;
        private volatile boolean isStopped;
        private volatile boolean isFinalizing;
        private volatile boolean isComplete;
        private volatile String currentLabel;

        public void setTotalToCheck(int total) { totalToCheck.set(total); }
        public int incrementChecked() { return checkedCount.incrementAndGet(); }
        public void incrementTotalToDownload() { totalToDownload.incrementAndGet(); downloadQueued.incrementAndGet(); }
        public int incrementDownloaded() { downloadQueued.decrementAndGet(); return downloadedCount.incrementAndGet(); }
        public void setTotalToParse(int total) { 
            // Only update if new total is larger (monotonically increasing)
            totalToParse.updateAndGet(current -> Math.max(current, total)); 
            parseQueued.set(totalToParse.get() - parsedCount.get()); 
        }
        public void incrementTotalToParse() { totalToParse.incrementAndGet(); parseQueued.incrementAndGet(); }
        public int incrementParsed() { parseQueued.decrementAndGet(); return parsedCount.incrementAndGet(); }
        public void addParsedHosts(long count) { parsedHostCount.addAndGet(count); }
        public long getParsedHostCount() { return parsedHostCount.get(); }
        public void setSchedulerTaskName(String name) { schedulerTaskName = name; }
        public void setPaused(boolean paused) { isPaused = paused; }
        public void setStopped(boolean stopped) { isStopped = stopped; }
        public void setFinalizing(boolean finalizing) { isFinalizing = finalizing; }
        public void setComplete(boolean complete) { isComplete = complete; }
        public void setCurrentLabel(String label) { currentLabel = label; }
        public boolean isPaused() { return isPaused; }
        public boolean isStopped() { return isStopped; }
        public boolean isFinalizing() { return isFinalizing; }
        public boolean isComplete() { return isComplete; }

        public MultiPhaseProgress build() {
            // Calculate current percentages
            int ttc = totalToCheck.get();
            int ttd = totalToDownload.get();
            int ttp = totalToParse.get();
            int curCheck = ttc > 0 ? Math.min(100, checkedCount.get() * 100 / ttc) : 0;
            int curDownload = ttd > 0 ? Math.min(100, downloadedCount.get() * 100 / ttd) : 0;
            int curParse;
            if (ttp > 0) {
                curParse = Math.min(100, parsedCount.get() * 100 / ttp);
            } else {
                // No parsing required: mark parse as complete once download stage is complete (all 200/304/fail processed)
                curParse = (ttd > 0 && downloadedCount.get() >= ttd) ? 100 : 0;
            }
            int curOverall = (int) Math.min(100, curCheck * 0.30 + curDownload * 0.40 + curParse * 0.30);

            // Update high water marks (monotonic - only increase)
            maxCheckPercent.updateAndGet(prev -> Math.max(prev, curCheck));
            maxDownloadPercent.updateAndGet(prev -> Math.max(prev, curDownload));
            maxParsePercent.updateAndGet(prev -> Math.max(prev, curParse));
            maxOverallPercent.updateAndGet(prev -> Math.max(prev, curOverall));

            return new MultiPhaseProgress(
                    checkedCount.get(), totalToCheck.get(),
                    downloadedCount.get(), totalToDownload.get(), downloadQueued.get(),
                    parsedCount.get(), totalToParse.get(), parseQueued.get(),
                    parsedHostCount.get(),
                    schedulerTaskName, isPaused, isStopped, isFinalizing, isComplete,
                    currentLabel,
                    maxCheckPercent.get(), maxDownloadPercent.get(), maxParsePercent.get(), maxOverallPercent.get()
            );
        }

        public void reset() {
            checkedCount.set(0);
            totalToCheck.set(0);
            downloadedCount.set(0);
            totalToDownload.set(0);
            downloadQueued.set(0);
            parsedCount.set(0);
            totalToParse.set(0);
            parseQueued.set(0);
            parsedHostCount.set(0);
            maxCheckPercent.set(0);
            maxDownloadPercent.set(0);
            maxParsePercent.set(0);
            maxOverallPercent.set(0);
            schedulerTaskName = null;
            isPaused = false;
            isStopped = false;
            isFinalizing = false;
            isComplete = false;
            currentLabel = null;
        }
    }
}
