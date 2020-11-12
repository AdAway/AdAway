package org.adaway.ui.lists;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.adaway.R;
import org.adaway.helper.ThemeHelper;
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar;

import static android.content.Intent.ACTION_SEARCH;

/**
 * This activity display hosts list items.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ListsActivity extends AppCompatActivity {
    /**
     * The tab to display argument.
     */
    public static final String TAB = "org.adaway.lists.tab";
    /**
     * The blocked hosts tab index.
     */
    public static final int BLOCKED_HOSTS_TAB = 0;
    /**
     * The allowed hosts tab index.
     */
    public static final int ALLOWED_HOSTS_TAB = 1;
    /**
     * The redirected hosts tab index.
     */
    public static final int REDIRECTED_HOSTS_TAB = 2;
    /**
     * The view model.
     */
    private ListsViewModel listsViewModel;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        /*
         * Set view content.
         */
        setContentView(R.layout.lists_fragment);
        /*
         * Configure actionbar.
         */
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        /*
         * Configure tabs.
         */
        // Get view pager
        ViewPager2 viewPager = findViewById(R.id.lists_view_pager);
        // Create pager adapter
        ListsFragmentPagerAdapter pagerAdapter = new ListsFragmentPagerAdapter(this);
        // Set view pager adapter
        viewPager.setAdapter(pagerAdapter);
        // Get navigation view
        BottomNavigationView navigationView = findViewById(R.id.navigation);
        // Add view pager on page listener to set selected tab according the selected page
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                navigationView.getMenu().getItem(position).setChecked(true);
                pagerAdapter.ensureActionModeCanceled();
            }
        });
        // Add navigation view item selected listener to change view pager current item
        navigationView.setOnNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.lists_navigation_blocked) {
                viewPager.setCurrentItem(0);
                return true;
            } else if (item.getItemId() == R.id.lists_navigation_allowed) {
                viewPager.setCurrentItem(1);
                return true;
            } else if (item.getItemId() == R.id.lists_navigation_redirected) {
                viewPager.setCurrentItem(2);
                return true;
            } else {
                return false;
            }
        });
        // Display requested tab
        Intent intent = getIntent();
        int tab = intent.getIntExtra(TAB, BLOCKED_HOSTS_TAB);
        viewPager.setCurrentItem(tab);
        /*
         * Configure add action button.
         */
        // Get the add action button
        FloatingActionButton addActionButton = findViewById(R.id.lists_add);
        // Set add action button listener
        addActionButton.setOnClickListener(clickedView -> {
            // Get current fragment position
            int currentItemPosition = viewPager.getCurrentItem();
            // Add item to the current fragment
            pagerAdapter.addItem(currentItemPosition);
        });
        /*
         * Configure snackbar.
         */
        // Get lists layout to attached snackbar to
        CoordinatorLayout coordinatorLayout = findViewById(R.id.coordinator);
        // Create apply snackbar
        ApplyConfigurationSnackbar applySnackbar = new ApplyConfigurationSnackbar(coordinatorLayout, false, false);
        // Bind snackbar to view models
        this.listsViewModel = new ViewModelProvider(this).get(ListsViewModel.class);
        this.listsViewModel.getModelChanged().observe(this, applySnackbar.createObserver());
        // Get the intent, verify the action and get the query
        handleQuery(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleQuery(intent);
    }

    private void handleQuery(Intent intent) {
        if (ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            this.listsViewModel.search(query);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_menu, menu);
        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
        if (searchManager != null) {
            SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            searchView.setIconifiedByDefault(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_toggle_source) {
            this.listsViewModel.toggleSources();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (this.listsViewModel.isSearching()) {
            this.listsViewModel.clearSearch();
        } else {
            super.onBackPressed();
        }
    }
}
