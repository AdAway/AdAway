package org.adaway.ui.source;

import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.databinding.SourceEditActivityBinding;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.FilterListsSourceMetadata;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.ui.hosts.FilterSetUpdateService;
import org.adaway.util.AppExecutors;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static java.util.Objects.requireNonNull;
import static org.adaway.ui.Animations.hideView;
import static org.adaway.ui.Animations.setHidden;
import static org.adaway.ui.Animations.setShown;
import static org.adaway.ui.Animations.showView;

/**
 * This activity create, edit and delete a hosts source.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class SourceEditActivity extends AppCompatActivity {
    /**
     * The source identifier extra.
     */
    public static final String SOURCE_ID = "sourceId";
    /**
     * Optional extras to prefill fields when adding a new source.
     */
    public static final String EXTRA_INITIAL_LABEL = "initialLabel";
    public static final String EXTRA_INITIAL_URL = "initialUrl";
    public static final String EXTRA_INITIAL_ALLOW = "initialAllow";
    public static final String EXTRA_INITIAL_REDIRECT = "initialRedirect";
    public static final String EXTRA_FILTER_LIST_ID = "filterListId";
    public static final String EXTRA_FILTER_LIST_NAME = "filterListName";
    public static final String EXTRA_FILTER_LIST_SYNTAX_IDS = "filterListSyntaxIds";
    public static final String EXTRA_FILTER_LIST_TAG_IDS = "filterListTagIds";
    public static final String EXTRA_FILTER_LIST_LANGUAGE_IDS = "filterListLanguageIds";
    public static final String EXTRA_FILTER_LIST_SELECTED_URL = "filterListSelectedUrl";
    /**
     * The any type mime type.
     */
    private static final String ANY_MIME_TYPE = "*/*";
    private static final String PREFS_SOURCE_SCHEDULES = "source_schedules";
    private static final String KEY_SCHEDULE_PREFIX = "schedule_url_";
    private static final String KEY_LAST_RUN_PREFIX = "last_run_url_";
    private static final String KEY_HOUR_PREFIX = "hour_url_";
    private static final String KEY_MINUTE_PREFIX = "minute_url_";
    private static final String KEY_WEEKDAY_PREFIX = "weekday_url_";

    private static final int SCHEDULE_OFF = 0;
    private static final int SCHEDULE_DAILY = 1;
    private static final int SCHEDULE_WEEKLY = 2;

    private SourceEditActivityBinding binding;
    private AppDatabase database;
    private HostsSourceDao hostsSourceDao;
    private ActivityResultLauncher<Intent> startActivityLauncher;
    private boolean editing;
    private boolean saving;
    private HostsSource edited;
    private int initialFilterListId;
    @Nullable
    private int[] initialFilterListSyntaxIds;
    @Nullable
    private int[] initialFilterListTagIds;
    @Nullable
    private int[] initialFilterListLanguageIds;
    @Nullable
    private String initialFilterListName;
    @Nullable
    private String initialFilterListSelectedUrl;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        /*
         * Set view content.
         */
        this.binding = SourceEditActivityBinding.inflate(getLayoutInflater());
        setContentView(this.binding.getRoot());
        /*
         * Configure actionbar.
         */
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        /*
         * Get database access.
         */
        this.database = AppDatabase.getInstance(this);
        this.hostsSourceDao = this.database.hostsSourceDao();
        // Register for activity
        registerForStartActivity();
        // Set up values
        checkInitialValueFromIntent();
    }

    private void registerForStartActivity() {
        this.startActivityLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
            Uri uri;
            Intent data = result.getData();
            if (result.getResultCode() == RESULT_OK
                    && data != null
                    && (uri = data.getData()) != null) {
                // Persist read permission
                getContentResolver().takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION);
                // Update file location
                this.binding.fileLocationTextView.setText(uri.toString());
                this.binding.fileLocationTextView.setError(null);
            }
        });
    }

    private void checkInitialValueFromIntent() {
        Intent intent = getIntent();
        int sourceId = intent.getIntExtra(SOURCE_ID, -1);
        this.editing = sourceId != -1;
        if (this.editing) {
            AppExecutors.getInstance().diskIO().execute(() -> {
                Optional<HostsSource> hostsSource = this.hostsSourceDao.getById(sourceId);
                hostsSource.ifPresent(source -> {
                    this.edited = source;
                    AppExecutors.getInstance().mainThread().execute(() -> {
                        applyInitialValues(source);
                        bindLocation();
                        bindFormats();
                        invalidateOptionsMenu();
                    });
                });
            });
        } else {
            setTitle(R.string.source_edit_add_title);
            // Prefill from optional extras
            String initialLabel = intent.getStringExtra(EXTRA_INITIAL_LABEL);
            if (initialLabel != null) {
                this.binding.labelEditText.setText(initialLabel);
            }
            String initialUrl = intent.getStringExtra(EXTRA_INITIAL_URL);
            if (initialUrl != null) {
                this.binding.typeButtonGroup.check(R.id.url_button);
                this.binding.locationEditText.setText(initialUrl);
            }
            boolean initialAllow = intent.getBooleanExtra(EXTRA_INITIAL_ALLOW, false);
            boolean initialRedirect = intent.getBooleanExtra(EXTRA_INITIAL_REDIRECT, false);
            this.initialFilterListId = intent.getIntExtra(EXTRA_FILTER_LIST_ID, 0);
            this.initialFilterListName = intent.getStringExtra(EXTRA_FILTER_LIST_NAME);
            this.initialFilterListSyntaxIds = intent.getIntArrayExtra(EXTRA_FILTER_LIST_SYNTAX_IDS);
            this.initialFilterListTagIds = intent.getIntArrayExtra(EXTRA_FILTER_LIST_TAG_IDS);
            this.initialFilterListLanguageIds =
                    intent.getIntArrayExtra(EXTRA_FILTER_LIST_LANGUAGE_IDS);
            this.initialFilterListSelectedUrl =
                    intent.getStringExtra(EXTRA_FILTER_LIST_SELECTED_URL);
            this.binding.blockFormatButton.setChecked(!initialAllow);
            this.binding.allowFormatButton.setChecked(initialAllow);
            this.binding.redirectedHostsCheckbox.setChecked(!initialAllow && initialRedirect);
            bindLocation();
            bindFormats();
            hideAddSourceAdvancedControls();
        }
    }

    private void hideAddSourceAdvancedControls() {
        this.binding.formatTextView.setVisibility(View.GONE);
        this.binding.formatButtonGroup.setVisibility(View.GONE);
        this.binding.redirectedHostsCheckbox.setVisibility(View.GONE);
        this.binding.redirectedHostsWarningTextView.setVisibility(View.GONE);
    }

    private void applyInitialValues(HostsSource source) {
        this.binding.labelEditText.setText(source.getLabel());
        this.binding.blockFormatButton.setChecked(!source.isAllowEnabled());
        this.binding.allowFormatButton.setChecked(source.isAllowEnabled());
        switch (source.getType()) {
            case URL:
                this.binding.typeButtonGroup.check(R.id.url_button);
                this.binding.locationEditText.setText(source.getUrl());
                break;
            case FILE:
                this.binding.typeButtonGroup.check(R.id.file_button);
                this.binding.fileLocationTextView.setText(source.getUrl());
                this.binding.fileLocationTextView.setVisibility(VISIBLE);
                this.binding.urlTextInputLayout.setVisibility(INVISIBLE);
                break;
        }
        this.binding.redirectedHostsCheckbox.setChecked(source.isRedirectEnabled());
        if (source.isAllowEnabled()) {
            setHidden(this.binding.redirectedHostsCheckbox);
            setHidden(this.binding.redirectedHostsWarningTextView);
        } else {
            setShown(this.binding.redirectedHostsCheckbox);
            setShown(this.binding.redirectedHostsWarningTextView);
        }
    }

    private void bindLocation() {
        this.binding.typeButtonGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            // Keep always one button checked
            if (group.getCheckedButtonId() == View.NO_ID) {
                group.check(checkedId);
                return;
            }
            if (isChecked) {
                boolean isFile = checkedId == R.id.file_button;
                this.binding.locationEditText.setText("");
                this.binding.locationEditText.setEnabled(!isFile);
                if (isFile) {
                    openDocument();
                }
                this.binding.urlTextInputLayout.setVisibility(isFile ? INVISIBLE : VISIBLE);
                this.binding.fileLocationTextView.setVisibility(isFile ? VISIBLE : INVISIBLE);
            }
        });
        this.binding.fileLocationTextView.setOnClickListener(view -> openDocument());
    }

    private void bindFormats() {
        this.binding.blockFormatButton.addOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) {
                showView(this.binding.redirectedHostsCheckbox);
                showView(this.binding.redirectedHostsWarningTextView);
            } else {
                hideView(this.binding.redirectedHostsCheckbox);
                hideView(this.binding.redirectedHostsWarningTextView);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.source_edit_menu, menu);
        menu.findItem(R.id.delete_action).setVisible(this.editing);
        menu.findItem(R.id.auto_update_action).setVisible(this.editing);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean ready = !this.saving && (!this.editing || this.edited != null);
        menu.findItem(R.id.apply_action).setEnabled(ready);
        menu.findItem(R.id.delete_action).setEnabled(ready);
        menu.findItem(R.id.auto_update_action).setEnabled(ready);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Check item identifier
        if (item.getItemId() == R.id.delete_action) {
            confirmDeleteSource();
            return true;
        } else if (item.getItemId() == R.id.apply_action) {
            if (this.saving || (this.editing && this.edited == null)) {
                return true;
            }
            HostsSource source = validate();
            if (source == null) {
                return true;
            }
            saveSource(source);
            return true;
        } else if (item.getItemId() == R.id.auto_update_action) {
            if (!this.editing) {
                return false;
            }
            HostsSource source = validate();
            if (source == null) {
                return false;
            }
            promptAutoUpdateSchedule(source.getUrl());
            return true;
        }
        return false;
    }

    private void saveSource(HostsSource values) {
        this.saving = true;
        invalidateOptionsMenu();
        AppExecutors.getInstance().diskIO().execute(() -> {
            boolean[] needsDownload = {false};
            boolean[] runtimeChanged = {false};
            String[] previousUrl = {null};
            int error;
            SourceModel sourceModel = ((AdAwayApplication) getApplication()).getSourceModel();
            try {
                error = sourceModel.runSourceMutation(() -> {
                    int result = this.database.runInTransaction(() -> {
                        int editedId = this.editing ? this.edited.getId() : -1;
                        HostsSource duplicate = this.hostsSourceDao.getByUrl(values.getUrl())
                                .orElse(null);
                        if (duplicate != null && duplicate.getId() != editedId) {
                            return R.string.source_edit_duplicate_url;
                        }
                        if (!this.editing) {
                            this.hostsSourceDao.insert(values);
                            needsDownload[0] = true;
                            return 0;
                        }
                        HostsSource current = this.hostsSourceDao.getById(editedId).orElse(null);
                        if (current == null || editedId == HostsSource.USER_SOURCE_ID) {
                            return R.string.source_edit_save_failed;
                        }
                        boolean contentChanged = !current.getUrl().equals(values.getUrl())
                                || current.isAllowEnabled() != values.isAllowEnabled()
                                || current.isRedirectEnabled() != values.isRedirectEnabled();
                        if (!current.getUrl().equals(values.getUrl())) {
                            previousUrl[0] = current.getUrl();
                        }
                        // Updating in place preserves selection, downloaded rules and HTTP validators
                        // for label-only edits. Recreating the row cascaded away all imported rules.
                        current.setLabel(values.getLabel());
                        current.setUrl(values.getUrl());
                        current.setAllowEnabled(values.isAllowEnabled());
                        current.setRedirectEnabled(values.isRedirectEnabled());
                        FilterListsSourceMetadata.copy(values, current);
                        this.hostsSourceDao.update(current);
                        if (contentChanged) {
                            this.database.hostsListItemDao().clearSourceHosts(editedId);
                            this.database.getOpenHelper().getWritableDatabase().execSQL(
                                    "DELETE FROM root_host_entries_stage WHERE source_id = ?",
                                    new Object[]{editedId});
                            this.hostsSourceDao.clearProperties(editedId);
                            this.hostsSourceDao.updateSkippedCount(editedId, 0);
                            this.hostsSourceDao.clearDownloadError(editedId);
                            this.database.hostEntryDao().invalidateRootExportMaterializedCache();
                            runtimeChanged[0] = true;
                            needsDownload[0] = current.isEnabled();
                        }
                        return 0;
                    });
                    if (result == 0 && runtimeChanged[0]) {
                        sourceModel.syncHostEntries();
                    }
                    return result;
                });
            } catch (HostErrorException exception) {
                error = R.string.error_update_in_progress_message;
            } catch (SQLiteException exception) {
                error = R.string.source_edit_save_failed;
            }
            if (error == 0) {
                if (previousUrl[0] != null) {
                    clearSchedule(previousUrl[0]);
                }
                FilterSetStore.markCustomProfile(getApplicationContext());
                if (needsDownload[0]) {
                    SourceUpdateService.enqueueUpdateNow(getApplicationContext());
                }
            }
            postMutationResult(error);
        });
    }

    private void confirmDeleteSource() {
        HostsSource source = this.edited;
        if (source == null) {
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.source_edit_delete_confirm_title)
                .setMessage(getString(
                        R.string.source_edit_delete_confirm_message,
                        source.getLabel()))
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.source_edit_delete_confirm_action,
                        (dialog, which) -> deleteSource(source))
                .show();
    }

    private void deleteSource(HostsSource source) {
        if (this.saving) return;
        this.saving = true;
        invalidateOptionsMenu();
        AppExecutors.getInstance().diskIO().execute(() -> {
            SourceModel sourceModel = ((AdAwayApplication) getApplication()).getSourceModel();
            String[] deletedUrl = {null};
            int error;
            try {
                error = sourceModel.runSourceMutation(() -> {
                    int result = this.database.runInTransaction(() -> {
                        HostsSource current = this.hostsSourceDao.getById(source.getId())
                                .orElse(null);
                        if (current == null || current.getId() == HostsSource.USER_SOURCE_ID) {
                            return R.string.source_edit_save_failed;
                        }
                        deletedUrl[0] = current.getUrl();
                        this.hostsSourceDao.delete(current);
                        this.database.getOpenHelper().getWritableDatabase().execSQL(
                                "DELETE FROM root_host_entries_stage WHERE source_id = ?",
                                new Object[]{current.getId()});
                        this.database.hostEntryDao().invalidateRootExportMaterializedCache();
                        return 0;
                    });
                    if (result == 0) sourceModel.syncHostEntries();
                    return result;
                });
            } catch (HostErrorException exception) {
                error = R.string.error_update_in_progress_message;
            } catch (SQLiteException exception) {
                error = R.string.source_edit_save_failed;
            }
            if (error == 0) {
                clearSchedule(deletedUrl[0]);
                FilterSetStore.markCustomProfile(getApplicationContext());
            }
            postMutationResult(error);
        });
    }

    private void postMutationResult(int error) {
        AppExecutors.getInstance().mainThread().execute(() -> {
            this.saving = false;
            if (isFinishing() || isDestroyed()) return;
            invalidateOptionsMenu();
            if (error == 0) {
                finish();
            } else if (this.binding.typeButtonGroup.getCheckedButtonId() == R.id.url_button) {
                this.binding.locationEditText.setError(getString(error));
            } else {
                this.binding.fileLocationTextView.setError(getString(error));
            }
        });
    }

    private void promptAutoUpdateSchedule(String url) {
        CharSequence[] scheduleOptions = new CharSequence[]{
                getString(R.string.filter_set_schedule_off),
                getString(R.string.filter_set_schedule_daily),
                getString(R.string.filter_set_schedule_weekly)
        };
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.source_edit_auto_update)
                .setItems(scheduleOptions, (d, which) -> {
                    if (which == 0) {
                        saveSchedule(url, SCHEDULE_OFF, 1, 3, 0);
                        FilterSetUpdateService.enable(this);
                        return;
                    }
                    if (which == 1) {
                        pickTime((hour, minute) -> {
                            saveSchedule(url, SCHEDULE_DAILY, 1, hour, minute);
                            FilterSetUpdateService.enable(this);
                        });
                        return;
                    }
                    pickDayOfWeek(dowIso -> pickTime((hour, minute) -> {
                        saveSchedule(url, SCHEDULE_WEEKLY, dowIso, hour, minute);
                        FilterSetUpdateService.enable(this);
                    }));
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void saveSchedule(String url, int schedule, int weekdayIso, int hour24, int minute) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_SOURCE_SCHEDULES, MODE_PRIVATE);
        android.content.SharedPreferences.Editor e = prefs.edit();
        e.putInt(KEY_SCHEDULE_PREFIX + url, schedule);
        e.putInt(KEY_WEEKDAY_PREFIX + url, Math.max(1, Math.min(7, weekdayIso)));
        e.putInt(KEY_HOUR_PREFIX + url, Math.max(0, Math.min(23, hour24)));
        e.putInt(KEY_MINUTE_PREFIX + url, Math.max(0, Math.min(59, minute)));
        // Reset last-run when schedule changes so it runs next occurrence.
        e.remove(KEY_LAST_RUN_PREFIX + url);
        e.apply();
    }

    private void clearSchedule(String url) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_SOURCE_SCHEDULES, MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_SCHEDULE_PREFIX + url)
                .remove(KEY_WEEKDAY_PREFIX + url)
                .remove(KEY_HOUR_PREFIX + url)
                .remove(KEY_MINUTE_PREFIX + url)
                .remove(KEY_LAST_RUN_PREFIX + url)
                .apply();
    }

    private interface TimePicked {
        void onPicked(int hour24, int minute);
    }

    private void pickTime(@NonNull TimePicked picked) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int hour = c.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = c.get(java.util.Calendar.MINUTE);
        new android.app.TimePickerDialog(
                this,
                (view, hourOfDay, minuteOfHour) -> picked.onPicked(hourOfDay, minuteOfHour),
                hour,
                minute,
                DateFormat.is24HourFormat(this)
        ).show();
    }

    private interface DayPicked {
        void onPicked(int isoDay);
    }

    private void pickDayOfWeek(@NonNull DayPicked picked) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.filter_set_schedule_pick_day)
                .setItems(getWeekdayLabels(), (d, which) -> picked.onPicked(which + 1))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    @NonNull
    private String[] getWeekdayLabels() {
        String[] labels = new String[DayOfWeek.values().length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = DayOfWeek.of(i + 1)
                    .getDisplayName(TextStyle.FULL, Locale.getDefault());
        }
        return labels;
    }

    private HostsSource validate() {
        String label = requireNonNull(this.binding.labelEditText.getText()).toString().trim();
        String url;
        if (this.binding.typeButtonGroup.getCheckedButtonId() == R.id.url_button) {
            url = requireNonNull(this.binding.locationEditText.getText()).toString().trim();
            if (url.isEmpty()) {
                this.binding.locationEditText.setError(getString(R.string.source_edit_url_location_required));
                return null;
            }
            if (url.contains(" ") || !HostsSource.isValidUrl(url)) {
                this.binding.locationEditText.setError(getString(R.string.source_edit_location_invalid));
                return null;
            }
        } else {
            url = this.binding.fileLocationTextView.getText().toString();
            if (!HostsSource.isValidUrl(url)) {
                this.binding.fileLocationTextView.setError(getString(R.string.source_edit_location_invalid));
                return null;
            }
        }
        if (label.isEmpty()) {
            String host = Uri.parse(url).getHost();
            label = host != null && !host.isEmpty() ? host : url;
        }
        HostsSource source = new HostsSource();
        source.setLabel(label);
        source.setUrl(url);
        boolean allowFormat = this.binding.allowFormatButton.isChecked();
        source.setAllowEnabled(allowFormat);
        source.setRedirectEnabled(!allowFormat && this.binding.redirectedHostsCheckbox.isChecked());
        if (!this.editing && this.initialFilterListId > 0) {
            FilterListsSourceMetadata.apply(source, this.initialFilterListId,
                    this.initialFilterListName, this.initialFilterListSyntaxIds,
                    this.initialFilterListTagIds, this.initialFilterListLanguageIds,
                    this.initialFilterListSelectedUrl != null ? this.initialFilterListSelectedUrl
                            : url);
        } else if (this.editing && this.edited != null
                && url.equals(this.edited.getUrl())) {
            FilterListsSourceMetadata.copy(this.edited, source);
        }
        return source;
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(ANY_MIME_TYPE);
        this.startActivityLauncher.launch(intent);
    }
}
