package org.adaway.ui;

import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.arch.core.util.Function;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;

import org.adaway.R;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.ui.help.HelpActivity;
import org.adaway.ui.home.ListsViewModel;
import org.adaway.ui.prefs.PrefsActivity;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;

/**
 * This class is the application main activity.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class NextActivity extends AppCompatActivity {
    /**
     * The project link.
     */
    private static final String PROJECT_LINK = "https://github.com/AdAway/AdAway";
    /**
     * The support link.
     */
    private static final String SUPPORT_LINK = "https://paypal.me/BruceBUJON";

//    protected CoordinatorLayout coordinatorLayout;

    private BottomAppBar appBar;
    private BottomSheetBehavior<View> drawerBehavior;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        NotificationHelper.clearUpdateHostsNotification(this);
        Log.i(Constants.TAG, "Starting main activity");
        setContentView(R.layout.next_activity);

        this.appBar = findViewById(R.id.bar);
        hideActionBar();
        bindHostCounter();
        bindClickListeners();
        notifyUpdating(false);
        setUpBottomDrawer();

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

    private void hideActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
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
                // TODO
                break;
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
     * Start preferences activity.
     */
    private void startPrefsActivity() {
        this.startActivity(new Intent(this, PrefsActivity.class));
    }

    /**
     * Start help activity.
     *
     * @param view The source event view.
     */
    private void startHelpActivity(@SuppressWarnings("unused") View view) {
        this.startActivity(new Intent(this, HelpActivity.class));
    }

    /**
     * Show development page.
     *
     * @param view The source event view.
     */
    private void showProjectPage(@SuppressWarnings("unused") View view) {
        // Show development page
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_LINK));
        this.startActivity(browserIntent);
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
                    this.startActivity(browserIntent);
                })
                .create()
                .show();
    }
}
