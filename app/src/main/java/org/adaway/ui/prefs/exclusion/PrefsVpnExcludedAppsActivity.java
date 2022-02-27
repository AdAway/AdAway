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

import org.adaway.R;
import org.adaway.databinding.VpnExcludedAppActivityBinding;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;

import java.util.Arrays;
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
        VpnExcludedAppActivityBinding binding = VpnExcludedAppActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        /*
         * Configure recycler view.
         */
        // Get recycler view
        binding.vpnExcludedAppList.setHasFixedSize(true);
        // Defile recycler layout
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        binding.vpnExcludedAppList.setLayoutManager(linearLayoutManager);
        // Create recycler adapter
        this.userApplications = getUserApplications();
        this.adapter = new UserAppRecycleViewAdapter(this);
        binding.vpnExcludedAppList.setAdapter(this.adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.vpn_excluded_app_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.select_all) {
            excludeApplications(this.userApplications);
            this.adapter.notifyItemRangeChanged(0, this.adapter.getItemCount());
            return true;
        } else if (item.getItemId() == R.id.deselect_all) {
            includeApplications(this.userApplications);
            this.adapter.notifyItemRangeChanged(0, this.adapter.getItemCount());
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
                    .map(applicationInfo -> new UserApp(
                            packageManager.getApplicationLabel(applicationInfo),
                            applicationInfo.packageName,
                            packageManager.getApplicationIcon(applicationInfo),
                            excludedApps.contains(applicationInfo.packageName)
                    ))
                    .sorted()
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
