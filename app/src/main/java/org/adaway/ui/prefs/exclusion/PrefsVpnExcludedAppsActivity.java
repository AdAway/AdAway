package org.adaway.ui.prefs.exclusion;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This activity is the activity to select excluded applications from VPN.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class PrefsVpnExcludedAppsActivity extends AppCompatActivity implements ExcludedAppController {
    private UserApp[] userApplications;
    private UserAppRecycleViewAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /*
         * Create activity.
         */
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.vpn_excluded_app_activity);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        /*
         * Configure recycler view.
         */
        // Get recycler view
        RecyclerView recyclerView = findViewById(R.id.vpn_excluded_app_list);
        recyclerView.setHasFixedSize(true);
        // Defile recycler layout
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(linearLayoutManager);
        // Create recycler adapter
        this.userApplications = getUserApplications();
        this.adapter = new UserAppRecycleViewAdapter(this);
        recyclerView.setAdapter(this.adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.vpn_excluded_app_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.select_all:
                excludeApplications(this.userApplications);
                this.adapter.notifyDataSetChanged();
                return true;
            case R.id.deselect_all:
                includeApplications(this.userApplications);
                this.adapter.notifyDataSetChanged();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public UserApp[] getUserApplications() {
        if (this.userApplications == null) {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo self = getApplicationInfo();
            Set<String> excludedApps = PreferenceHelper.getVpnExcludedApps(this);

            this.userApplications = packageManager.getInstalledApplications(0).stream()
                    .filter(applicationInfo -> (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0)
                    .filter(applicationInfo -> !Objects.equals(applicationInfo.name, self.name))
                    .sorted(Comparator.comparing(applicationInfo -> applicationInfo.packageName))
                    .map(applicationInfo -> new UserApp(
                            packageManager.getApplicationLabel(applicationInfo),
                            applicationInfo.packageName,
                            packageManager.getApplicationIcon(applicationInfo),
                            excludedApps.contains(applicationInfo.packageName)
                    ))
                    .toArray(UserApp[]::new);
        }
        return this.userApplications;
    }

    @Override
    public void excludeApplications(UserApp... applications) {
        for (UserApp application : applications) {
            application.excluded = true;
        }
        updatePreferences();
    }

    @Override
    public void includeApplications(UserApp... applications) {
        for (UserApp application : applications) {
            application.excluded = false;
        }
        updatePreferences();
    }

    private void updatePreferences() {
        Set<String> excludedApplicationPackageNames = Arrays.stream(this.userApplications)
                .filter(userApp -> userApp.excluded)
                .map(userApp -> userApp.packageName.toString())
                .collect(Collectors.toSet());
        PreferenceHelper.setVpnExcludedApps(this, excludedApplicationPackageNames);
    }
}
