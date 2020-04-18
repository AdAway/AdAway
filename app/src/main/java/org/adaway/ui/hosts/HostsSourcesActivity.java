package org.adaway.ui.hosts;

import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.adaway.R;
import org.adaway.helper.ThemeHelper;

/**
 * This activity display hosts list items.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsSourcesActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        /*
         * Create fragment
         */
        HostsSourcesFragment fragment = new HostsSourcesFragment();
        /*
         * Set view content.
         */
        setContentView(R.layout.hosts_sources_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.hosts_sources_container, fragment)
                .commit();
        /*
         * Configure actionbar.
         */
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }
}
