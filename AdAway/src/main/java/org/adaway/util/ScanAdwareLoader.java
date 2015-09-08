/*
 * Copyright (C) 2012 Daniel Bjorge
 *                    Dominik Schürmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.adaway.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.support.v4.content.AsyncTaskLoader;

/**
 * A custom Loader to search for bad adware apps, based on
 * https://github.com/brosmike/AirPush-Detector. Daniel Bjorge licensed it under Apachev2 after
 * asking him by mail.
 */
public class ScanAdwareLoader extends AsyncTaskLoader<List<Map<String, String>>> {
    public static final String[] AD_PACKAGE_PREFIXES = {"com.airpush.", "com.Leadbolt.",
            "com.appenda.", "com.iac.notification.", "com.appbucks.sdk.", "com.tapjoy.",
            "com.sellaring.", "com.inmobi.", "com.senddroid.", "cn.kuguo.", "com.applovin.",
            "com.adnotify."};

    Context context;
    List<String> mItems;
    private volatile boolean canceled = false;

    public ScanAdwareLoader(Context context) {
        super(context);

        this.context = context;
    }

    @Override
    public List<Map<String, String>> loadInBackground() {
        List<PackageInfo> adPackages = getAdPackages();
        PackageManager pm = context.getPackageManager();

        List<Map<String, String>> data = new ArrayList<Map<String, String>>(adPackages.size());
        for (PackageInfo pkg : adPackages) {
            Map<String, String> attrs = new HashMap<String, String>();
            attrs.put("app_name", pm.getApplicationLabel(pkg.applicationInfo).toString());
            attrs.put("package_name", pkg.packageName);
            data.add(attrs);
        }

        return data;
    }

    @Override
    protected void onReset() {
        super.onReset();

        canceled = true;

        // Ensure the loader is stopped
        onStopLoading();
    }

    @Override
    protected void onStartLoading() {
        canceled = false;
        forceLoad();
    }

    @Override
    protected void onStopLoading() {
        canceled = true;
        cancelLoad();
    }

    @Override
    public void deliverResult(List<Map<String, String>> data) {
        super.deliverResult(data);
    }

    /**
     * Finds all installed packages that look like they include a known ad framework
     */
    private List<PackageInfo> getAdPackages() {
        Set<PackageInfo> adPackages = new HashSet<PackageInfo>();

        PackageManager pm = context.getPackageManager();
        // It'd be simpler to just use pm.getInstalledPackages here, but apparently it's broken
        List<ApplicationInfo> appInfos = pm.getInstalledApplications(0);

        for (ApplicationInfo appInfo : appInfos) {
            if(canceled) {
                adPackages.clear();
                break;
            }
            try {
                PackageInfo pkgInfo = pm.getPackageInfo(appInfo.packageName,
                        PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                                | PackageManager.GET_SERVICES
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

        return new ArrayList<PackageInfo>(adPackages);
    }

}
