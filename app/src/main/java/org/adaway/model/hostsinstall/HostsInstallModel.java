package org.adaway.model.hostsinstall;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.git.GitHostsSource;
import org.adaway.util.ApplyUtils;
import org.adaway.util.CommandException;
import org.adaway.util.Constants;
import org.adaway.util.DateUtils;
import org.adaway.util.HostsParser;
import org.adaway.util.Log;
import org.adaway.util.NotEnoughSpaceException;
import org.adaway.util.RemountException;
import org.adaway.util.Utils;
import org.sufficientlysecure.rootcommands.Shell;
import org.sufficientlysecure.rootcommands.util.RootAccessDeniedException;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Observable;
import java.util.Set;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static org.adaway.model.hostsinstall.HostsInstallError.APPLY_FAIL;
import static org.adaway.model.hostsinstall.HostsInstallError.COPY_FAIL;
import static org.adaway.model.hostsinstall.HostsInstallError.DOWNLOAD_FAIL;
import static org.adaway.model.hostsinstall.HostsInstallError.NOT_ENOUGH_SPACE;
import static org.adaway.model.hostsinstall.HostsInstallError.NO_CONNECTION;
import static org.adaway.model.hostsinstall.HostsInstallError.PRIVATE_FILE_FAIL;
import static org.adaway.model.hostsinstall.HostsInstallError.REMOUNT_FAIL;
import static org.adaway.model.hostsinstall.HostsInstallError.REVERT_FAIL;
import static org.adaway.model.hostsinstall.HostsInstallError.ROOT_ACCESS_DENIED;
import static org.adaway.model.hostsinstall.HostsInstallError.SYMLINK_MISSING;

/**
 * This class is the model to represent hosts file installation.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsInstallModel extends Observable {
    /*
     * Apply modes (see pref pref_apply_method_entries_values).
     */
    private static final String APPLY_TO_SYSTEM = "writeToSystem";
    private static final String APPLY_TO_DATA_DATA = "writeToDataData";
    private static final String APPLY_TO_DATA = "writeToData";
    private static final String APPLY_TO_CUSTOM_TARGET = "customTarget";
    /**
     * The application context.
     */
    private final Context context;
    /**
     * The model state.
     */
    private String state;
    /**
     * The model detailed state.
     */
    private String detailedState;
    /**
     * The HTTP client to download hosts sources ({@code null} until initialized by {@link #getHttpClient()}).
     */
    private OkHttpClient httpClient;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public HostsInstallModel(Context context) {
        this.context = context;
        this.state = "";
        this.detailedState = "";
    }

    /**
     * Create symlink from system hosts file to target hosts file.
     *
     * @throws HostsInstallException If the symlink could not be created.
     */
    public void createSymlink() throws HostsInstallException {
        try {
            // Check installation according apply method
            String applyMethod = PreferenceHelper.getApplyMethod(this.context);
            switch (applyMethod) {
                case APPLY_TO_DATA_DATA:
                    ApplyUtils.createSymlink(Constants.ANDROID_DATA_DATA_HOSTS);
                    break;
                case APPLY_TO_DATA:
                    ApplyUtils.createSymlink(Constants.ANDROID_DATA_HOSTS);
                    break;
                case APPLY_TO_CUSTOM_TARGET:
                    ApplyUtils.createSymlink(PreferenceHelper.getCustomTarget(this.context));
                    break;
                default:
                    throw new IllegalStateException("The apply method " + applyMethod + " is not supported.");
            }
        } catch (CommandException | RemountException exception) {
            throw new HostsInstallException(SYMLINK_MISSING, "Failed to create symlink.", exception);
        }
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
     * Get the model detailed state.
     *
     * @return The model detailed state.
     */
    public String getDetailedState() {
        return this.detailedState;
    }

    /**
     * Check if there is update available in hosts sources.
     *
     * @throws HostsInstallException If the hosts sources could not be checked.
     */
    public boolean checkForUpdate() throws HostsInstallException {
        HostsSourceDao hostsSourceDao = AppDatabase.getInstance(this.context).hostsSourceDao();
        // Check current connection
        if (!Utils.isAndroidOnline(this.context)) {
            throw new HostsInstallException(NO_CONNECTION, "Failed to download hosts sources files: not connected.");
        }
        // Initialize update status
        boolean updateAvailable = false;
        boolean anyHostsSourceVerified = false;
        // Get hosts sources
        List<HostsSource> sources = hostsSourceDao.getEnabled();
        if (sources.isEmpty()) {
            // Return no update as no source
            return false;
        }
        // Update state
        this.setStateAndDetails(R.string.status_checking, R.string.status_checking);
        // Check each source
        for (HostsSource source : sources) {
            // Get URL and lastModified from db
            String sourceUrl = source.getUrl();
            Date lastModifiedLocal = source.getLastLocalModification();
            // Update state
            this.setStateAndDetails(R.string.status_checking, sourceUrl);
            // Get hosts source last update
            Date lastModifiedOnline = this.getHostsSourceLastUpdate(sourceUrl);
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
            throw new HostsInstallException(DOWNLOAD_FAIL, "No hosts sources files was checked: all checks failed.");
        }
        // Check if update is available
        if (updateAvailable) {
            this.setStateAndDetails(R.string.status_update_available, R.string.status_update_available_subtitle);
        } else {
            this.setStateAndDetails(R.string.status_enabled, R.string.status_enabled_subtitle);
        }
        Log.d(Constants.TAG, "Update check result: " + updateAvailable);
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
     * @throws HostsInstallException If the hosts sources could not be downloaded.
     */
    public void retrieveHostsSources() throws HostsInstallException {
        HostsSourceDao hostsSourceDao = AppDatabase.getInstance(this.context).hostsSourceDao();
        // Check connection status
        if (!Utils.isAndroidOnline(this.context)) {
            throw new HostsInstallException(NO_CONNECTION, "Failed to download hosts sources files: not connected.");
        }
        // Update state to downloading
        this.setStateAndDetails(R.string.download_dialog, "");
        // Initialize copy counters
        int numberOfCopies = 0;
        int numberOfFailedCopies = 0;
        // Open local private file and get cursor to enabled hosts sources
        try (FileOutputStream out = this.context.openFileOutput(Constants.DOWNLOADED_HOSTS_FILENAME, Context.MODE_PRIVATE)) {
            // Get each hosts source
            for (HostsSource hostsSource : hostsSourceDao.getEnabled()) {
                // Increment number of copy
                numberOfCopies++;
                boolean copySuccess = false;
                // Check hosts source protocol
                String url = hostsSource.getUrl();
                String protocol = new URL(url).getProtocol();
                switch (protocol) {
                    case "https":
                        copySuccess = this.downloadHostSource(hostsSource, out);
                        break;
                    case "file":
                        copySuccess = this.copyHostSourceFile(hostsSource, out);
                        break;
                    default:
                        Log.w(Constants.TAG, "Hosts source protocol " + protocol + " is not supported.");
                }
                if (!copySuccess) {
                    // Increment number of failed copy
                    numberOfFailedCopies++;
                }
            }
            // Check if all copies failed
            if (numberOfCopies == numberOfFailedCopies && numberOfCopies != 0) {
                throw new HostsInstallException(DOWNLOAD_FAIL, "No hosts sources files was copied: all copies failed.");
            }
        } catch (IOException exception) {
            throw new HostsInstallException(PRIVATE_FILE_FAIL, "An error happened with private file while copying hosts sources files.", exception);
        }
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
                    .cache(new Cache(context.getCacheDir(), 100 * 1024 * 1024))
                    .build();
        }
        return this.httpClient;
    }

    /**
     * Download an hosts source file and append it to a private file.
     *
     * @param hostsSource The hosts source to download.
     * @param out         The output stream to append the hosts file content to.
     * @return {@code true} if the hosts was successfully downloaded, {@code false} otherwise.
     */
    private boolean downloadHostSource(HostsSource hostsSource, FileOutputStream out) {
        HostsSourceDao hostsSourceDao = AppDatabase.getInstance(this.context).hostsSourceDao();
        // Get hosts file URL
        String hostsFileUrl = hostsSource.getUrl();
        Log.v(Constants.TAG, "Downloading hosts file: " + hostsFileUrl);
        // Set state to downloading hosts source
        this.setStateAndDetails(R.string.download_dialog, hostsFileUrl);
        // Get HTTP client
        OkHttpClient httpClient = this.getHttpClient();
        // Create request
        Request request = new Request.Builder()
                .url(hostsFileUrl)
                .build();
        // Request hosts file and open byte stream
        try (Response response = httpClient.newCall(request).execute();
             InputStream inputStream = response.body().byteStream()) {
            // Copy hosts content to private file
            this.copyHostsContent(inputStream, out);
            // Save last modified online for later use
            String lastModifiedHeader = response.header("Last-Modified");
            if (lastModifiedHeader != null) {
                try {
                    // Parse last modified date
                    SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                    Date lastModified = format.parse(lastModifiedHeader);
                    // Update last_modified_online (null if not available or error happened)
                    hostsSourceDao.updateOnlineModificationDate(hostsFileUrl, lastModified);
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
     * @param out         The output stream to append the hosts file content to.
     * @return {@code true} if the hosts was successfully downloaded, {@code false} otherwise.
     */
    private boolean copyHostSourceFile(HostsSource hostsSource, FileOutputStream out) {
        HostsSourceDao hostsSourceDao = AppDatabase.getInstance(this.context).hostsSourceDao();
        // Get hosts file URL
        String hostsFileUrl = hostsSource.getUrl();
        Log.v(Constants.TAG, "Copying hosts source file: " + hostsFileUrl);
        // Set state to downloading hosts source
        this.setStateAndDetails(R.string.download_dialog, hostsFileUrl);
        // Declare last modification date
        Date lastModified = null;
        try {
            // Get file from URL
            File hostsSourceFile = new File(new URL(hostsFileUrl).toURI());
            // Copy hosts file source to private file
            try (InputStream inputStream = new FileInputStream(hostsSourceFile)) {
                copyHostsContent(inputStream, out);
            }
            // Get last modified date
            lastModified = new Date(hostsSourceFile.lastModified());
        } catch (IOException | URISyntaxException exception) {
            Log.e(Constants.TAG, "Error while copying hosts file from " + hostsFileUrl + ".", exception);
            // Return copy failed
            return false;
        } finally {
            // Update last_modified_online (null if not available or error happened)
            hostsSourceDao.updateOnlineModificationDate(hostsFileUrl, lastModified);
        }
        // Return copy successful
        return true;
    }

    /**
     * Copy an {@link InputStream} hosts content to an {@link java.io.OutputStream}.
     *
     * @param inputStream  The input stream to copy from.
     * @param outputStream The output stream to copy to.
     * @throws IOException If the input stream could not be read or output stream could not be written.
     */
    private void copyHostsContent(InputStream inputStream, FileOutputStream outputStream) throws IOException {
        // Read all content into output stream
        byte[] data = new byte[1024];
        int count;
        // run while only when thread is not cancelled
        while ((count = inputStream.read(data)) != -1) {
            outputStream.write(data, 0, count);
        }
        // add line separator to add files together in one file
        outputStream.write(Constants.LINE_SEPARATOR.getBytes());
    }

    /**
     * Apply hosts file.
     *
     * @throws HostsInstallException If the hosts file could not be applied.
     */
    public void applyHostsFile() throws HostsInstallException {
        // Create root shell
        Shell shell = null;
        try {
            shell = Shell.startRootShell();
            this.setStateAndDetails(R.string.apply_dialog, R.string.apply_dialog_hosts);
            if (!this.checkHostsFileSymlink(shell)) {
                throw new HostsInstallException(SYMLINK_MISSING, "The symlink to the hosts file target is missing.");
            }
            this.createNewHostsFile();
            this.deleteHostsSources();
            this.copyNewHostsFile(shell);
            this.deleteNewHostsFile();
            this.setStateAndDetails(R.string.apply_dialog, R.string.apply_dialog_apply);
            if (!checkInstalledHostsFile()) {
                throw new HostsInstallException(APPLY_FAIL, "Failed to apply new hosts file.");
            }
            this.markHostsSourcesAsInstalled();
            this.setStateAndDetails(R.string.status_enabled, R.string.status_enabled_subtitle);
        } catch (RootAccessDeniedException exception) {
            throw new HostsInstallException(ROOT_ACCESS_DENIED, "Root access denied", exception);
        } catch (IOException exception) {
            throw new HostsInstallException(APPLY_FAIL, "Failed to start a root shell.", exception);
        } finally {
            if (shell != null) {
                try {
                    shell.close();
                } catch (Exception exception) {
                    Log.e(Constants.TAG, "Problem closing the root shell!", exception);
                }
            }
        }
    }

    private void deleteNewHostsFile() {
        // delete generated hosts file from private storage
        this.context.deleteFile(Constants.HOSTS_FILENAME);
    }

    private void deleteHostsSources() {
        // delete downloaded hosts file from private storage
        this.context.deleteFile(Constants.DOWNLOADED_HOSTS_FILENAME);
    }

    /**
     * Check if the hosts file was well installed.
     *
     * @return {@code true} if the hosts file was well installed, {@code false} otherwise.
     */
    private boolean checkInstalledHostsFile() {
        // Check installation according apply method
        String applyMethod = PreferenceHelper.getApplyMethod(this.context);
        switch (applyMethod) {
            case APPLY_TO_SYSTEM:
                /* /system/etc/hosts */
                return ApplyUtils.isHostsFileCorrect(Constants.ANDROID_SYSTEM_ETC_HOSTS);
            case APPLY_TO_DATA_DATA:
                /* /data/data/hosts */
                return ApplyUtils.isHostsFileCorrect(Constants.ANDROID_DATA_DATA_HOSTS);
            case APPLY_TO_DATA:
                /* /data/data/hosts */
                return ApplyUtils.isHostsFileCorrect(Constants.ANDROID_DATA_HOSTS);
            case APPLY_TO_CUSTOM_TARGET:
                /* custom target */
                String customTarget = PreferenceHelper.getCustomTarget(this.context);
                return ApplyUtils.isHostsFileCorrect(customTarget);
            default:
                throw new IllegalStateException("The apply method " + applyMethod + " is not supported.");
        }
    }

    /**
     * Check if the hosts file target symlink is needed and installed.
     *
     * @param shell The root shell to use.
     * @return {@code true} if the hosts file target is the system one or symlink to target is installed, {@code false} otherwise.
     */
    private boolean checkHostsFileSymlink(Shell shell) {
        // Check installation according apply method
        String applyMethod = PreferenceHelper.getApplyMethod(this.context);
        switch (applyMethod) {
            case APPLY_TO_SYSTEM:
                // System hosts file used, no need of symlink
                return true;
            case APPLY_TO_DATA_DATA:
                // /data/data/hosts
                return ApplyUtils.isSymlinkCorrect(Constants.ANDROID_DATA_DATA_HOSTS, shell);
            case APPLY_TO_DATA:
                // /data/data/hosts
                return ApplyUtils.isSymlinkCorrect(Constants.ANDROID_DATA_HOSTS, shell);
            case APPLY_TO_CUSTOM_TARGET:
                // custom target
                String customTarget = PreferenceHelper.getCustomTarget(this.context);
                return ApplyUtils.isSymlinkCorrect(customTarget, shell);
            default:
                throw new IllegalStateException("The apply method " + applyMethod + " is not supported.");
        }
    }

    private void copyNewHostsFile(Shell rootShell) throws HostsInstallException {
        // copy build hosts file with RootTools, based on target from preferences
        try {
            String applyMethod = PreferenceHelper.getApplyMethod(this.context);
            switch (applyMethod) {
                case APPLY_TO_SYSTEM:
                    ApplyUtils.copyHostsFile(this.context, Constants.ANDROID_SYSTEM_ETC_HOSTS, rootShell);
                    break;
                case APPLY_TO_DATA_DATA:
                    ApplyUtils.copyHostsFile(this.context, Constants.ANDROID_DATA_DATA_HOSTS, rootShell);
                    break;
                case APPLY_TO_DATA:
                    ApplyUtils.copyHostsFile(this.context, Constants.ANDROID_DATA_HOSTS, rootShell);
                    break;
                case APPLY_TO_CUSTOM_TARGET:
                    String customTarget = PreferenceHelper.getCustomTarget(this.context);
                    ApplyUtils.copyHostsFile(this.context, customTarget, rootShell);
                    break;
                default:
                    throw new IllegalStateException("The apply method " + applyMethod + " is not supported.");
            }
        } catch (NotEnoughSpaceException exception) {
            throw new HostsInstallException(NOT_ENOUGH_SPACE, "Missing free space to copy new private hosts file to target hosts file.", exception);
        } catch (RemountException exception) {
            throw new HostsInstallException(REMOUNT_FAIL, "Failed to remount target to copy new private hosts to.", exception);
        } catch (CommandException exception) {
            throw new HostsInstallException(COPY_FAIL, "Unable to copy new private hosts file to target hosts file.", exception);
        }
    }

    /**
     * Create a new hosts files in a private file from downloaded hosts sources.
     *
     * @throws HostsInstallException If the new hosts file could not be created.
     */
    private void createNewHostsFile() throws HostsInstallException {
        try (BufferedOutputStream outputStream = new BufferedOutputStream(this.context.openFileOutput(Constants.HOSTS_FILENAME,
                Context.MODE_PRIVATE))) {
            HostsParser parser = this.parseDownloadedHosts();
            this.writeHostsHeader(outputStream);
            this.writeLoopbackToHosts(outputStream);
            this.writeHosts(outputStream, parser);
        } catch (FileNotFoundException exception) {
            throw new HostsInstallException(PRIVATE_FILE_FAIL, "Private hosts file was not found.", exception);
        } catch (IOException exception) {
            throw new HostsInstallException(PRIVATE_FILE_FAIL, "Failed to write new private hosts file.", exception);
        }
    }

    private HostsParser parseDownloadedHosts() throws IOException {
        // Get application context
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.context.openFileInput(Constants.DOWNLOADED_HOSTS_FILENAME)))) {
            /* PARSE: parse hosts files to sets of host names and comments */
            // Use whitelist and/or redirection rules from hosts sources only if enabled in preferences
            HostsParser hostsParser = new HostsParser(reader, PreferenceHelper.getWhitelistRules(this.context), PreferenceHelper.getRedirectionRules(this.context));
            this.applyUserList(hostsParser);
            return hostsParser;
        }
    }

    /**
     * Apply user-defined lists.
     *
     * @param parser The parser to which apply user-defined lists.
     */
    private void applyUserList(HostsParser parser) {
        HostListItemDao hostListItemDao = AppDatabase.getInstance(context).hostsListItemDao();
        // Get list collections
        Set<String> blackListHosts = new HashSet<>(hostListItemDao.getEnabledBlackListHosts());
        Set<String> whiteListHosts = new HashSet<>(hostListItemDao.getEnabledWhiteListHosts());
        Map<String, String> redirectListHosts = Stream.of(hostListItemDao.getEnabledRedirectList())
                .collect(Collectors.toMap(HostListItem::getHost, HostListItem::getRedirection));
        // add whitelist from db
        parser.addBlacklist(blackListHosts);
        // add blacklist from db
        parser.addWhitelist(whiteListHosts);
        // add redirect list from db
        parser.addRedirectList(redirectListHosts);
        // compile lists (removing whitelist entries, etc.)
        parser.compileList();
    }

    private void writeHosts(BufferedOutputStream outputStream, HostsParser parser) throws IOException {
        String redirectionIpv4 = PreferenceHelper.getRedirectionIpv4(this.context);
        String redirectionIpv6 = PreferenceHelper.getRedirectionIpv6(this.context);
        // write hostnames
        String line;
        boolean enableIpv6 = PreferenceHelper.getEnableIpv6(this.context);
        for (String hostname : parser.getBlacklist()) {
            line = Constants.LINE_SEPARATOR + redirectionIpv4 + " " + hostname;
            outputStream.write(line.getBytes());
            if (enableIpv6) {
                line = Constants.LINE_SEPARATOR + redirectionIpv6 + " " + hostname;
                outputStream.write(line.getBytes());
            }
        }

        /* REDIRECT LIST: write redirect items */
        String redirectItemHostname;
        String redirectItemIP;
        for (HashMap.Entry<String, String> item : parser.getRedirectList().entrySet()) {
            redirectItemHostname = item.getKey();
            redirectItemIP = item.getValue();

            line = Constants.LINE_SEPARATOR + redirectItemIP + " " + redirectItemHostname;
            outputStream.write(line.getBytes());
        }

        // hosts file has to end with new line, when not done last entry won't be recognized
        outputStream.write(Constants.LINE_SEPARATOR.getBytes());
    }

    private void writeLoopbackToHosts(BufferedOutputStream outputStream) throws IOException {
        // add "127.0.0.1 localhost" entry
        String localhost = Constants.LINE_SEPARATOR + Constants.LOCALHOST_IPv4 + " "
                + Constants.LOCALHOST_HOSTNAME + Constants.LINE_SEPARATOR
                + Constants.LOCALHOST_IPv6 + " " + Constants.LOCALHOST_HOSTNAME;
        outputStream.write(localhost.getBytes());

        outputStream.write(Constants.LINE_SEPARATOR.getBytes());
    }

    private void writeHostsHeader(BufferedOutputStream outputStream) throws IOException {
        HostsSourceDao hostsSourceDao = AppDatabase.getInstance(this.context).hostsSourceDao();
        // build current timestamp for header
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date now = new Date();

        // add adaway header
        String header = Constants.HEADER1 + Constants.LINE_SEPARATOR + "# " +
                formatter.format(now) + Constants.LINE_SEPARATOR + Constants.HEADER2 +
                Constants.LINE_SEPARATOR + Constants.HEADER_SOURCES;
        outputStream.write(header.getBytes());

        // write sources into header
        String source;
        for (HostsSource hostsSource : hostsSourceDao.getEnabled()) {
            source = Constants.LINE_SEPARATOR + "# " + hostsSource.getUrl();
            outputStream.write(source.getBytes());
        }

        outputStream.write(Constants.LINE_SEPARATOR.getBytes());
    }

    /**
     * Revert to the default hosts file.
     *
     * @throws HostsInstallException If the hosts file could not be reverted.
     */
    public void revert() throws HostsInstallException {
        // Update status
        this.setStateAndDetails(R.string.status_reverting, R.string.status_reverting_subtitle);
        // Create root shell
        Shell shell = null;
        try {
            shell = Shell.startRootShell();
            // Revert hosts file
            this.revertHostFile(shell);
            this.markHostsSourcesAsUninstalled();
            this.setStateAndDetails(R.string.status_disabled, R.string.status_disabled_subtitle);
        } catch (IOException exception) {
            this.setStateAndDetails(R.string.status_enabled, R.string.revert_problem);
            throw new HostsInstallException(REVERT_FAIL, "Unable to revert hosts file.", exception);
        } finally {
            // Close shell
            if (shell != null) {
                try {
                    shell.close();
                } catch (IOException exception) {
                    Log.d(Constants.TAG, "Error while closing shell.", exception);
                }
            }
        }
    }

    /**
     * Revert to default hosts file.
     *
     * @param shell The root shell to use.
     * @throws IOException If the hosts file could not be reverted.
     */
    private void revertHostFile(Shell shell) throws IOException {
        // Create private file
        try (FileOutputStream fos = context.openFileOutput(Constants.HOSTS_FILENAME, Context.MODE_PRIVATE)) {
            // Write default localhost as hosts file
            String localhost = Constants.LOCALHOST_IPv4 + " " + Constants.LOCALHOST_HOSTNAME
                    + Constants.LINE_SEPARATOR + Constants.LOCALHOST_IPv6 + " "
                    + Constants.LOCALHOST_HOSTNAME;
            fos.write(localhost.getBytes());
            // Get hosts file target based on preferences
            String applyMethod = PreferenceHelper.getApplyMethod(this.context);
            String target;
            switch (applyMethod) {
                case APPLY_TO_SYSTEM:
                    target = Constants.ANDROID_SYSTEM_ETC_HOSTS;
                    break;
                case APPLY_TO_DATA_DATA:
                    target = Constants.ANDROID_DATA_DATA_HOSTS;
                    break;
                case APPLY_TO_DATA:
                    target = Constants.ANDROID_DATA_HOSTS;
                    break;
                case APPLY_TO_CUSTOM_TARGET:
                    target = PreferenceHelper.getCustomTarget(this.context);
                    break;
                default:
                    throw new IllegalStateException("The apply method does not match any settings: " + applyMethod + ".");
            }
            // Copy generated hosts file to target location
            ApplyUtils.copyHostsFile(this.context, target, shell);
            // Delete generated hosts file after applying it
            this.context.deleteFile(Constants.HOSTS_FILENAME);
        } catch (Exception exception) {
            throw new IOException("Unable to revert hosts file.", exception);
        }
    }

    /**
     * Set local modifications date to now for all enabled hosts sources.
     */
    private void markHostsSourcesAsInstalled() {
        // Get application context and database
        HostsSourceDao hostsSourceDao = AppDatabase.getInstance(this.context).hostsSourceDao();
        Date now = new Date();
        hostsSourceDao.updateEnabledLocalModificationDates(now);
    }

    /**
     * Clear local modification dates for all hosts sources.
     */
    private void markHostsSourcesAsUninstalled() {
        // Get application context and database
        HostsSourceDao hostsSourceDao = AppDatabase.getInstance(this.context).hostsSourceDao();
        hostsSourceDao.clearLocalModificationDates();
    }

    private void setStateAndDetails(@StringRes int stateResId, @StringRes int detailsResId) {
        this.setStateAndDetails(stateResId, this.context.getString(detailsResId));
    }

    private void setStateAndDetails(@StringRes int stateResId, String details) {
        this.state = this.context.getString(stateResId);
        this.detailedState = details;
        this.setChanged();
        this.notifyObservers();
    }
}
