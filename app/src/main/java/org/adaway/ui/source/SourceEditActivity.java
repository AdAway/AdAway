package org.adaway.ui.source;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

import org.adaway.R;
import org.adaway.databinding.SourceEditActivityBinding;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.ThemeHelper;
import org.adaway.util.AppExecutors;

import java.util.Optional;
import java.util.concurrent.Executor;

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
     * The any type mime type.
     */
    private static final String ANY_MIME_TYPE = "*/*";
    private static final Executor DISK_IO_EXECUTOR = AppExecutors.getInstance().diskIO();
    private static final Executor MAIN_THREAD_EXECUTOR = AppExecutors.getInstance().mainThread();

    private SourceEditActivityBinding binding;
    private HostsSourceDao hostsSourceDao;
    private ActivityResultLauncher<Intent> startActivityLauncher;
    private boolean editing;
    private HostsSource edited;

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
        AppDatabase database = AppDatabase.getInstance(this);
        this.hostsSourceDao = database.hostsSourceDao();
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
            DISK_IO_EXECUTOR.execute(() -> {
                Optional<HostsSource> hostsSource = this.hostsSourceDao.getById(sourceId);
                hostsSource.ifPresent(source -> {
                    this.edited = source;
                    MAIN_THREAD_EXECUTOR.execute(() -> {
                        applyInitialValues(source);
                        bindLocation();
                        bindFormats();
                    });
                });
            });
        } else {
            setTitle(R.string.source_edit_add_title);
            bindLocation();
            bindFormats();
        }
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
                this.binding.locationEditText.setText(isFile ? "" : "https://");
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
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Check item identifier
        if (item.getItemId() == R.id.delete_action) {
            DISK_IO_EXECUTOR.execute(() -> this.hostsSourceDao.delete(this.edited));
            finish();
            return true;
        } else if (item.getItemId() == R.id.apply_action) {
            HostsSource source = validate();
            if (source == null) {
                return false;
            }
            DISK_IO_EXECUTOR.execute(() -> {
                if (this.editing) {
                    this.hostsSourceDao.delete(this.edited);
                }
                this.hostsSourceDao.insert(source);
                finish();
            });
            return true;
        }
        return false;
    }

    private HostsSource validate() {
        String label = requireNonNull(this.binding.labelEditText.getText()).toString();
        if (label.isEmpty()) {
            this.binding.labelEditText.setError(getString(R.string.source_edit_label_required));
            return null;
        }
        String url;
        if (this.binding.typeButtonGroup.getCheckedButtonId() == R.id.url_button) {
            url = requireNonNull(this.binding.locationEditText.getText()).toString();
            if (url.isEmpty()) {
                this.binding.locationEditText.setError(getString(R.string.source_edit_url_location_required));
                return null;
            }
            if (!HostsSource.isValidUrl(url)) {
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
        HostsSource source = new HostsSource();
        source.setLabel(label);
        source.setUrl(url);
        boolean allowFormat = this.binding.allowFormatButton.isChecked();
        source.setAllowEnabled(allowFormat);
        source.setRedirectEnabled(!allowFormat && this.binding.redirectedHostsCheckbox.isChecked());
        return source;
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(ANY_MIME_TYPE);
        this.startActivityLauncher.launch(intent);
    }
}
