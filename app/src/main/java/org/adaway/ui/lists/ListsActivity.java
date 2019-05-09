package org.adaway.ui.lists;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.adaway.R;
import org.adaway.helper.ThemeHelper;

import static org.adaway.ui.lists.ListsFragment.BLACKLIST_TAB;
import static org.adaway.ui.lists.ListsFragment.TAB;

/**
 * This activity is the preferences activity.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ListsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        /*
         * Create fragment
         */
        Intent intent = getIntent();
        int tab = intent.getIntExtra(TAB, BLACKLIST_TAB);
        ListsFragment fragment = new ListsFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(TAB, tab);
        fragment.setArguments(bundle);
        /*
         * Set view content.
         */
        setContentView(R.layout.lists_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.lists_containers, fragment)
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
