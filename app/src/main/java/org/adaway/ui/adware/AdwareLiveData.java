package org.adaway.ui.adware;

import android.arch.lifecycle.LiveData;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.support.annotation.WorkerThread;

import org.adaway.util.AppExecutors;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is {@link LiveData} to represents installed adware on device.
 */
class AdwareLiveData extends LiveData<List<AdwareInstall>> {
    /**
     * The adware package prefixes.
     */
    private static final String[] AD_PACKAGE_PREFIXES = {
            "com.airpush.",
            "com.adnotify.",
            "com.appbucks.sdk.",
            "com.appenda.",
            "com.applovin.",
            "com.iac.notification.",
            "com.inmobi.",
            "com.Leadbolt.",
            "com.sellaring.",
            "com.senddroid.",
            "com.tapjoy.",
            "cn.kuguo.",
            "com"
    };

    /**
     * The application context.
     */
    private final Context context;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    AdwareLiveData(Context context) {
        this.context = context;
        AppExecutors.getInstance().diskIO().execute(this::loadData);
    }

    @WorkerThread
    private void loadData() {
        // Get the package manager
        PackageManager pm = context.getPackageManager();
        // Get the adware packages
        List<PackageInfo> adwarePackages = this.getAdwarePackages(pm);
        // Create related adware installs
        List<AdwareInstall> adwareInstalls = new ArrayList<>(adwarePackages.size());
        for (PackageInfo pkg : adwarePackages) {
            // Retrieve application name
            String applicationName = pm.getApplicationLabel(pkg.applicationInfo).toString();
            // Add adware install
            adwareInstalls.add(new AdwareInstall(applicationName, pkg.packageName));
        }
        // Sort adware installs found
        Collections.sort(adwareInstalls);
        // Post loaded adware installs
        this.postValue(adwareInstalls);
    }

    /**
     * Finds all installed packages that look like they include a known ad framework
     *
     * @param pm The package manager.
     * @return The found adware package information.
     */
    private List<PackageInfo> getAdwarePackages(PackageManager pm) {
        // It'd be simpler to just use pm.getInstalledPackages here, but apparently it's broken
        List<ApplicationInfo> applicationInfoList = pm.getInstalledApplications(0);

        Set<PackageInfo> adPackages = new HashSet<>();
        for (ApplicationInfo applicationInfo : applicationInfoList) {
            try {
                PackageInfo pkgInfo = pm.getPackageInfo(
                        applicationInfo.packageName,
                        PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_SERVICES
                );

                Log.v(Constants.TAG, "Scanning package " + pkgInfo.packageName);

                if (pkgInfo.activities != null) {
                    for (ActivityInfo activity : pkgInfo.activities) {
                        Log.v(Constants.TAG, "[ACTIVITY] " + activity.name);
                        for (String adPackagePrefix : AD_PACKAGE_PREFIXES) {
                            if (activity.name.startsWith(adPackagePrefix)) {
                                Log.i(Constants.TAG, "Detected ad framework prefix "
                                        + adPackagePrefix + " in package " + pkgInfo.packageName
                                        + " as activity " + activity.name);
                                adPackages.add(pkgInfo);
                                break;
                            }
                        }
                    }
                }
                if (pkgInfo.receivers != null) {
                    for (ActivityInfo receiver : pkgInfo.receivers) {
                        Log.v(Constants.TAG, "[RECEIVER] " + receiver.name);
                        for (String adPackagePrefix : AD_PACKAGE_PREFIXES) {
                            if (receiver.name.startsWith(adPackagePrefix)) {
                                Log.i(Constants.TAG, "Detected ad framework prefix "
                                        + adPackagePrefix + " in package " + pkgInfo.packageName
                                        + " as receiver " + receiver.name);
                                adPackages.add(pkgInfo);
                                break;
                            }
                        }
                    }
                }
                if (pkgInfo.services != null) {
                    for (ServiceInfo service : pkgInfo.services) {
                        Log.v(Constants.TAG, "[SERVICE] " + service.name);
                        for (String adPackagePrefix : AD_PACKAGE_PREFIXES) {
                            if (service.name.startsWith(adPackagePrefix)) {
                                Log.i(Constants.TAG, "Detected ad framework prefix "
                                        + adPackagePrefix + " in package " + pkgInfo.packageName
                                        + " as service " + service.name);
                                adPackages.add(pkgInfo);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(Constants.TAG, "Scan Adware Exception", e);
            }
        }
        return new ArrayList<>(adPackages);
    }
}
