package org.adaway.ui.home;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.databinding.HomeActivityBinding;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.error.HostError;
import org.adaway.model.update.Manifest;
import org.adaway.ui.help.HelpActivity;
import org.adaway.ui.hosts.HostsSourcesActivity;
import org.adaway.ui.lists.ListsActivity;
import org.adaway.ui.prefs.PrefsActivity;
import org.adaway.ui.support.SupportActivity;
import org.adaway.ui.tcpdump.TcpdumpLogActivity;
import org.adaway.ui.welcome.WelcomeActivity;
import org.adaway.util.Log;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;
import static org.adaway.model.adblocking.AdBlockMethod.UNDEFINED;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.ui.lists.ListsActivity.ALLOWED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.BLOCKED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.REDIRECTED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.TAB;

/**
 * This class is the application main activity.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HomeActivity extends AppCompatActivity {
    private static final String TAG = "Home";
    /**
     * The project link.
     */
    private static final String PROJECT_LINK = "https://github.com/AdAway/AdAway";

    private HomeActivityBinding binding;
    private BottomSheetBehavior<View> drawerBehavior;
    private HomeViewModel homeViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkFirstStep();
        ThemeHelper.applyTheme(this);
        NotificationHelper.clearUpdateNotifications(this);
        Log.i(TAG, "Starting main activity");
        this.binding = HomeActivityBinding.inflate(getLayoutInflater());
        setContentView(this.binding.getRoot());

        this.homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        this.homeViewModel.isAdBlocked().observe(this, this::notifyAdBlocked);
        this.homeViewModel.getError().observe(this, this::notifyError);

        applyActionBar();
        bindAppVersion();
        bindHostCounter();
        bindSourceCounter();
        bindPending();
        bindState();
        bindClickListeners();
        setUpBottomDrawer();
        bindFab();

        this.binding.navigationView.setNavigationItemSelectedListener(item -> {
            if (showFragment(item.getItemId())) {
                this.drawerBehavior.setState(STATE_HIDDEN);
            }
            return false; // TODO Handle selection
        });

        checkUpdateAtStartup();
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
        return showFragment(item.getItemId());
    }

    private void checkFirstStep() {
        AdBlockMethod adBlockMethod = PreferenceHelper.getAdBlockMethod(this);
        Intent prepareIntent;
        if (adBlockMethod == UNDEFINED) {
            // Start welcome activity
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
        } else if (adBlockMethod == VPN && (prepareIntent = VpnService.prepare(this)) != null) {
            // Prepare VNP
            startActivity(prepareIntent);
        }
    }

    private void checkUpdateAtStartup() {
        boolean checkUpdateAtStartup = PreferenceHelper.getUpdateCheck(this);
        if (checkUpdateAtStartup) {
            updateHostsList(null);
        }
    }

    private void applyActionBar() {
        setSupportActionBar(this.binding.bar);
    }

    private void bindAppVersion() {
        TextView versionTextView = this.binding.content.versionTextView;
        versionTextView.setText(this.homeViewModel.getVersionName());
        versionTextView.setOnClickListener(this::showChangelog);

        this.homeViewModel.getAppManifest().observe(
                this,
                manifest -> {
                    if (manifest.updateAvailable) {
                        versionTextView.setTypeface(versionTextView.getTypeface(), Typeface.BOLD);
                        versionTextView.setText(R.string.update_available);
                    }
                }
        );
    }

    private void bindHostCounter() {
        Function<Integer, CharSequence> stringMapper = count -> Integer.toString(count);

        TextView blockedHostCountTextView = this.binding.content.blockedHostCounterTextView;
        LiveData<Integer> blockedHostCount = this.homeViewModel.getBlockedHostCount();
        Transformations.map(blockedHostCount, stringMapper).observe(this, blockedHostCountTextView::setText);

        TextView allowedHostCountTextView = this.binding.content.allowedHostCounterTextView;
        LiveData<Integer> allowedHostCount = this.homeViewModel.getAllowedHostCount();
        Transformations.map(allowedHostCount, stringMapper).observe(this, allowedHostCountTextView::setText);

        TextView redirectHostCountTextView = this.binding.content.redirectHostCounterTextView;
        LiveData<Integer> redirectHostCount = this.homeViewModel.getRedirectHostCount();
        Transformations.map(redirectHostCount, stringMapper).observe(this, redirectHostCountTextView::setText);
    }

    private void bindSourceCounter() {
        Resources resources = getResources();

        TextView upToDateSourcesTextView = this.binding.content.upToDateSourcesTextView;
        LiveData<Integer> upToDateSourceCount = this.homeViewModel.getUpToDateSourceCount();
        upToDateSourceCount.observe(this, count ->
                upToDateSourcesTextView.setText(resources.getQuantityString(R.plurals.up_to_date_source_label, count, count))
        );

        TextView outdatedSourcesTextView = this.binding.content.outdatedSourcesTextView;
        LiveData<Integer> outdatedSourceCount = this.homeViewModel.getOutdatedSourceCount();
        outdatedSourceCount.observe(this, count ->
                outdatedSourcesTextView.setText(resources.getQuantityString(R.plurals.outdated_source_label, count, count))
        );
    }

    private void bindPending() {
        View sourcesImageView = this.binding.content.sourcesImageView;
        View sourcesProgressBar = this.binding.content.sourcesProgressBar;
        this.homeViewModel.getPending().observe(this, pending -> {
            if (pending) {
                hideView(sourcesImageView);
                showView(sourcesProgressBar);
            } else {
                showView(sourcesImageView);
                hideView(sourcesProgressBar);
            }
        });
    }

    private void bindState() {
        this.homeViewModel.getState().observe(this, this.binding.content.stateTextView::setText);
    }

    private void bindClickListeners() {
        this.binding.content.blockedHostCardView.setOnClickListener(v -> startHostListActivity(BLOCKED_HOSTS_TAB));
        this.binding.content.allowedHostCardView.setOnClickListener(v -> startHostListActivity(ALLOWED_HOSTS_TAB));
        this.binding.content.redirectHostCardView.setOnClickListener(v -> startHostListActivity(REDIRECTED_HOSTS_TAB));
        this.binding.content.sourcesCardView.setOnClickListener(this::startHostsSourcesActivity);
        this.binding.content.checkForUpdateImageView.setOnClickListener(this::updateHostsList);
        this.binding.content.updateImageView.setOnClickListener(this::syncHostsList);
        this.binding.content.helpCardView.setOnClickListener(this::startHelpActivity);
        this.binding.content.projectCardView.setOnClickListener(this::showProjectPage);
        this.binding.content.supportCardView.setOnClickListener(this::showSupportActivity);
    }

    private void setUpBottomDrawer() {
        this.drawerBehavior = BottomSheetBehavior.from(this.binding.bottomDrawer);
        this.drawerBehavior.setState(STATE_HIDDEN);

        this.binding.bar.setNavigationOnClickListener(v -> this.drawerBehavior.setState(STATE_HALF_EXPANDED));
//        this.binding.bar.setNavigationIcon(R.drawable.ic_menu_24dp);
//        this.binding.bar.replaceMenu(R.menu.next_actions);
    }

    private void bindFab() {
        this.binding.fab.setOnClickListener(v -> this.homeViewModel.toggleAdBlocking());
    }

    private boolean showFragment(@IdRes int actionId) {
        switch (actionId) {
            case R.id.drawer_preferences:
                startPrefsActivity();
                this.drawerBehavior.setState(STATE_HIDDEN);
                return true;
            case R.id.drawer_dns_logs:
                startDnsLogActivity();
                this.drawerBehavior.setState(STATE_HIDDEN);
                return true;
            case R.id.action_update:
                syncHostsList(null); // TODO
                return true;
            case R.id.action_show_log:
                // TODO
                break;
        }
        return false;
    }

    private void notifyUpdating(boolean updating) {
        TextView stateTextView = this.binding.content.stateTextView;
        if (updating) {
            showView(stateTextView);
        } else {
            hideView(stateTextView);
        }

//        Menu menu = this.appBar.getMenu();
//        MenuItem updateItemMenu = menu.findItem(R.id.action_update);
//        if (updateItemMenu != null) {
//            updateItemMenu.setIcon(updating ? R.drawable.ic_language_red : R.drawable.ic_sync_24dp);
//        }
    }

    private void showView(View view) {
        view.clearAnimation();
        view.setAlpha(0F);
        view.setVisibility(VISIBLE);
        view.animate()
                .alpha(1F)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(VISIBLE);
                    }
                });
    }

    private void hideView(View view) {
        view.clearAnimation();
        view.animate()
                .alpha(0F)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(GONE);
                    }
                });
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
     * @param view The event source view.
     */
    private void startHostsSourcesActivity(@SuppressWarnings("unused") View view) {
        startActivity(new Intent(this, HostsSourcesActivity.class));
    }

    /**
     * Update the hosts list status.
     *
     * @param view The event source view.
     */
    private void updateHostsList(@SuppressWarnings("unused") View view) {
        notifyUpdating(true);
        this.homeViewModel.update();
    }

    /**
     * Synchronize the hosts list.
     *
     * @param view The event source view.
     */
    private void syncHostsList(@SuppressWarnings("unused") View view) {
        notifyUpdating(true);
        this.homeViewModel.sync();
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
     * Show support activity.
     *
     * @param view The source event view.
     */
    private void showSupportActivity(@SuppressWarnings("unused") View view) {
        startActivity(new Intent(this, SupportActivity.class));
    }

    /**
     * Start preferences activity.
     */
    private void startPrefsActivity() {
        startActivity(new Intent(this, PrefsActivity.class));
    }

    /**
     * Start DNS log activity.
     */
    private void startDnsLogActivity() {
        startActivity(new Intent(this, TcpdumpLogActivity.class));
    }

    private void notifyAdBlocked(boolean adBlocked) {
        int color = adBlocked ? getResources().getColor(R.color.primary, null) : Color.GRAY;
        this.binding.content.headerFrameLayout.setBackgroundColor(color);
        this.binding.fab.setImageResource(adBlocked ? R.drawable.ic_pause_24dp : R.drawable.logo);
    }

    private void notifyError(HostError error) {
        if (error == null) {
            return;
        }

        notifyUpdating(false);

        String message = getString(error.getDetailsKey()) + "\n\n" + getString(R.string.error_dialog_help);
        new MaterialAlertDialogBuilder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(error.getMessageKey())
                .setMessage(message)
                .setPositiveButton(R.string.button_close, (dialog, id) -> dialog.dismiss())
                .setNegativeButton(R.string.button_help, (dialog, id) -> {
                    dialog.dismiss();
                    startActivity(new Intent(this, HelpActivity.class));
                })
                .create()
                .show();
    }

    private void showChangelog(@SuppressWarnings("unused") View view) {
        // Check manifest
        Manifest manifest = this.homeViewModel.getAppManifest().getValue();
        if (manifest == null) {
            return;
        }
        // Format changelog
        String message = manifest.changelog;
        // Create dialog
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this);
        if (manifest.updateAvailable) {
            message = getString(R.string.update_update_message) + message;
            dialogBuilder
                    .setMessage(message)
                    .setPositiveButton(R.string.update_update_button, (dialog, which) -> {
                        ((AdAwayApplication) this.getApplication()).getUpdateModel().update();
                        dialog.dismiss();
                    })
                    .setNeutralButton(R.string.button_close, (dialog, which) -> dialog.dismiss());
        } else {
            message = getString(R.string.update_up_to_date_message) + message;
            dialogBuilder.setTitle(R.string.update_up_to_date_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.button_close, (dialog, which) -> dialog.dismiss());
        }
        dialogBuilder.create().show();
    }
}
