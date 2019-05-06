package org.adaway.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.navigation.NavigationView;

import org.adaway.R;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.ThemeHelper;
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

//    protected CoordinatorLayout coordinatorLayout;

    protected BottomAppBar appBar;
    private BottomSheetBehavior<View> drawerBehavior;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        NotificationHelper.clearUpdateHostsNotification(this);
        Log.i(Constants.TAG, "Starting main activity");
        setContentView(R.layout.next_activity);

        hideActionBar();
        this.appBar = findViewById(R.id.bar);
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
            case R.id.drawer_hosts_sources:
                // TODO
                break;
            case R.id.drawer_your_lists:
                // TODO
                break;
            case R.id.drawer_open_hosts_file:
                // TODO
                break;
            case R.id.drawer_preferences:
                // TODO
                break;
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
}
