package org.adaway.ui.next;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.arch.core.util.Function;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.update.Manifest;
import org.adaway.ui.help.HelpActivity;
import org.adaway.ui.home.ListsViewModel;
import org.adaway.ui.hosts.HostsSourcesActivity;
import org.adaway.ui.lists.ListsActivity;
import org.adaway.ui.prefs.PrefsActivity;
import org.adaway.ui.welcome.WelcomeActivity;
import org.adaway.util.Log;

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;
import static org.adaway.model.adblocking.AdBlockMethod.UNDEFINED;
import static org.adaway.ui.lists.ListsFragment.BLACKLIST_TAB;
import static org.adaway.ui.lists.ListsFragment.REDIRECTION_TAB;
import static org.adaway.ui.lists.ListsFragment.TAB;
import static org.adaway.ui.lists.ListsFragment.WHITELIST_TAB;
import static org.adaway.util.Constants.TAG;

/**
 * This class is the application main activity.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class NextActivity extends AppCompatActivity {
    /**
     * The support link.
     */
    public static final String SUPPORT_LINK = "https://paypal.me/BruceBUJON";
    /**
     * The project link.
     */
    private static final String PROJECT_LINK = "https://github.com/AdAway/AdAway";

    //    protected CoordinatorLayout coordinatorLayout;
    private BottomAppBar appBar;
    private FloatingActionButton fab;
    private BottomSheetBehavior<View> drawerBehavior;
    private NextViewModel nextViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkFirstStep();
        ThemeHelper.applyTheme(this);
        NotificationHelper.clearUpdateHostsNotification(this);
        Log.i(TAG, "Starting main activity");
        setContentView(R.layout.next_activity);

        this.nextViewModel = ViewModelProviders.of(this).get(NextViewModel.class);
        this.nextViewModel.isAdBlocked().observe(this, this::notifyAdBlocked);
        this.nextViewModel.getError().observe(this, hostError -> {
            if (hostError != null) { // TODO Save last error
                TextView textView = new TextView(this);
                textView.setText(hostError.getMessageKey());
                new MaterialAlertDialogBuilder(this) // TODO Create proper dialog
                        .setTitle("An error occurred")
                        .setView(textView)
                        .create()
                        .show();
            }
        });

        this.appBar = findViewById(R.id.bar);
        applyActionBar();
        bindAppVersion();
        bindHostCounter();
        bindClickListeners();
        setUpBottomDrawer();
        bindFab();
        notifyUpdating(false);

        NavigationView navigationView = findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(item -> {
            if (this.showFragment(item.getItemId())) {
                this.drawerBehavior.setState(STATE_HIDDEN);
            }
            return false; // TODO Handle selection
        });
    }

    @Override
    public void onBackPressed() {
        // Hide drawer if expanded
        if (this.drawerBehavior != null && this.drawerBehavior.getState() != STATE_HIDDEN) {
            this.drawerBehavior.setState(STATE_HIDDEN);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return this.showFragment(item.getItemId());
    }

    private void checkFirstStep() {
        AdBlockMethod adBlockMethod = PreferenceHelper.getAdBlockMethod(this);
        if (adBlockMethod == UNDEFINED) {
            this.startActivity(new Intent(this, WelcomeActivity.class));
            this.finish();
        }
    }

    private void applyActionBar() {
//        ActionBar actionBar = getSupportActionBar();
//        if (actionBar != null) {
//            actionBar.hide();
//        }
        setSupportActionBar(this.appBar);
    }

    private void bindAppVersion() {
        TextView versionTextView = findViewById(R.id.versionTextView);
        versionTextView.setText(this.nextViewModel.getVersionName());
        versionTextView.setOnClickListener(this::showChangelog);

        this.nextViewModel.getAppManifest().observe(
                this,
                manifest -> versionTextView.setText(R.string.update_available)
        );
    }

    private void bindHostCounter() {
        ListsViewModel listsViewModel = ViewModelProviders.of(this).get(ListsViewModel.class);
        Resources resources = getResources();
        Function<Integer, CharSequence> stringMapper = count -> Integer.toString(count);

        TextView blockedHostCountTextView = findViewById(R.id.blockedHostCounterTextView);
        TextView blockedHostTextView = findViewById(R.id.blockedHostTextView);
        LiveData<Integer> blockedHostCounter = listsViewModel.getBlockedHostCount();
        Transformations.map(blockedHostCounter, stringMapper).observe(this, blockedHostCountTextView::setText);
        blockedHostCounter.observe(this, count ->
                blockedHostTextView.setText(resources.getQuantityText(R.plurals.blocked_hosts_label, count))
        );

        TextView allowedHostCountTextView = findViewById(R.id.allowedHostCounterTextView);
        TextView allowedHostTextView = findViewById(R.id.allowedHostTextView);
        LiveData<Integer> allowedHostCounter = listsViewModel.getAllowedHostCount();
        Transformations.map(allowedHostCounter, stringMapper).observe(this, allowedHostCountTextView::setText);
        allowedHostCounter.observe(this, count ->
                allowedHostTextView.setText(resources.getQuantityText(R.plurals.allowed_hosts_label, count))
        );

        TextView redirectHostCountTextView = findViewById(R.id.redirectHostCounterTextView);
        TextView redirectHostTextView = findViewById(R.id.redirectHostTextView);
        LiveData<Integer> redirectHostCounter = listsViewModel.getRedirectHostCount();
        Transformations.map(redirectHostCounter, stringMapper).observe(this, redirectHostCountTextView::setText);
        redirectHostCounter.observe(this, count ->
                redirectHostTextView.setText(resources.getQuantityText(R.plurals.redirect_hosts_label, count))
        );
    }

    private void bindClickListeners() {
        CardView blockedHostCardView = findViewById(R.id.blockedHostCardView);
        blockedHostCardView.setOnClickListener(v -> startHostListActivity(BLACKLIST_TAB));
        CardView allowedHostCardView = findViewById(R.id.allowedHostCardView);
        allowedHostCardView.setOnClickListener(v -> startHostListActivity(WHITELIST_TAB));
        CardView redirectHostHostCardView = findViewById(R.id.redirectHostCardView);
        redirectHostHostCardView.setOnClickListener(v -> startHostListActivity(REDIRECTION_TAB));
        CardView sourcesCardView = findViewById(R.id.sourcesCardView);
        sourcesCardView.setOnClickListener(this::startHostsSourcesActivity);
        CardView helpCardView = findViewById(R.id.helpCardView);
        helpCardView.setOnClickListener(this::startHelpActivity);
        CardView projectCardView = findViewById(R.id.projectCardView);
        projectCardView.setOnClickListener(this::showProjectPage);
        CardView supportCardView = findViewById(R.id.supportCardView);
        supportCardView.setOnClickListener(this::showSupportPage);
    }

    private void setUpBottomDrawer() {
        View bottomDrawer = findViewById(R.id.bottom_drawer);
        this.drawerBehavior = BottomSheetBehavior.from(bottomDrawer);
        this.drawerBehavior.setState(STATE_HIDDEN);

        this.appBar.setNavigationOnClickListener(v -> this.drawerBehavior.setState(STATE_HALF_EXPANDED));
//        bar.setNavigationIcon(R.drawable.ic_menu_24dp);
//        bar.replaceMenu(R.menu.next_actions);
    }

    private void bindFab() {
        this.fab = findViewById(R.id.fab);
        this.fab.setOnClickListener(v -> this.nextViewModel.toggleAdBlocking());
    }

    private boolean showFragment(@IdRes int actionId) {
        switch (actionId) {
            case R.id.drawer_open_hosts_file:
                // TODO
                break;
            case R.id.drawer_preferences:
                this.startPrefsActivity();
                this.drawerBehavior.setState(STATE_HIDDEN);
                return true;
//                break;
            case R.id.action_update:
                this.syncHostsList();
                return true;
            case R.id.action_show_log:
                // TODO
                break;
        }
        return false;
    }

    private void notifyUpdating(boolean updating) {
        Menu menu = this.appBar.getMenu();
        MenuItem updateItemMenu = menu.findItem(R.id.action_update);
        if (updateItemMenu != null) {
            updateItemMenu.setIcon(updating ? R.drawable.ic_language_red : R.drawable.ic_sync_24dp);
        }
    }

    /**
     * Start hosts lists activity.
     *
     * @param tab The tab to show.
     */
    private void startHostListActivity(int tab) {
        Intent intent = new Intent(this, ListsActivity.class);
        intent.putExtra(TAB, tab);
        startActivity(intent);
    }

    /**
     * Start hosts source activity.
     *
     * @param view The source view event.
     */
    private void startHostsSourcesActivity(@SuppressWarnings("unused") View view) {
        startActivity(new Intent(this, HostsSourcesActivity.class));
    }

    /**
     * Start help activity.
     *
     * @param view The source event view.
     */
    private void startHelpActivity(@SuppressWarnings("unused") View view) {
        startActivity(new Intent(this, HelpActivity.class));
    }

    /**
     * Show development page.
     *
     * @param view The source event view.
     */
    private void showProjectPage(@SuppressWarnings("unused") View view) {
        // Show development page
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_LINK));
        startActivity(browserIntent);
    }

    /**
     * Show support page.
     *
     * @param view The source event view.
     */
    private void showSupportPage(@SuppressWarnings("unused") View view) {
        // Show support dialog
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.baseline_favorite_24)
                .setTitle(R.string.drawer_support_dialog_title)
                .setMessage(R.string.drawer_support_dialog_text)
                .setPositiveButton(R.string.drawer_support_dialog_button, (d, which) -> {
                    // Show support page
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_LINK));
                    startActivity(browserIntent);
                })
                .create()
                .show();
    }

    /**
     * Start preferences activity.
     */
    private void startPrefsActivity() {
        startActivity(new Intent(this, PrefsActivity.class));
    }

    /**
     * Synchronize the hosts list.
     */
    private void syncHostsList() {
        this.notifyUpdating(true);
        this.nextViewModel.sync();
        this.notifyUpdating(false);
    }

    private void notifyAdBlocked(boolean adBlocked) {
        FrameLayout layout = findViewById(R.id.headerFrameLayout);
        int color = adBlocked ? getResources().getColor(R.color.primary, null) : Color.GRAY;
        layout.setBackgroundColor(color);
        this.fab.setImageResource(adBlocked ? R.drawable.ic_pause_24dp : R.drawable.ic_playlist_add_24dp);
    }

    private void showChangelog(@SuppressWarnings("unused") View view) {
        Manifest manifest = this.nextViewModel.getAppManifest().getValue();
        if (manifest == null) {
            return;
        }
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_changelog)
                .setMessage(manifest.changelog)
                .setNeutralButton(R.string.button_close, (dialog, which) -> dialog.dismiss());
        if (manifest.updateAvailable) {
            dialogBuilder.setPositiveButton(R.string.update_update_button, (dialog, which) -> {
                ((AdAwayApplication) this.getApplication()).getUpdateModel().update();
                dialog.dismiss();
            });
        }
        dialogBuilder.create().show();
    }
}
