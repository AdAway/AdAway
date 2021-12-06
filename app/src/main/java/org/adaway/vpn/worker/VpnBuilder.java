package org.adaway.vpn.worker;

import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.Q;
import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;
import static java.util.Collections.emptySet;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import org.adaway.helper.PreferenceHelper;
import org.adaway.ui.home.HomeActivity;
import org.adaway.vpn.dns.DnsServerMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

/**
 * This utility class is in charge of establishing a new VPN interface.
 *
 * @author Bruce BUJON
 */
public final class VpnBuilder {
    /**
     * Private constructor.
     */
    private VpnBuilder() {

    }

    /**
     * Establish the VPN interface.
     *
     * @param service         The VPN service to create interface.
     * @param dnsServerMapper The DNS server mapper used to configure VPN address and routes.
     * @return The VPN interface.
     */
    public static ParcelFileDescriptor establish(VpnService service, DnsServerMapper dnsServerMapper) {
        Timber.d("Establishing VPN…");
        VpnService.Builder builder = service.new Builder();
        // Configure VPN address and DNS servers
        dnsServerMapper.configureVpn(service, builder);
        // Exclude applications from VPN according user preferences (all applications goes through VPN by default)
        excludeApplicationsFromVpn(service, builder);
        // Allow applications to bypass the VPN by programmatically binding to a network for compatibility
        builder.allowBypass();
        // Set file descriptor in blocking mode as worker has a dedicated thread
        builder.setBlocking(true);
        // Set the VPN to unmetered
        if (SDK_INT >= Q) {
            builder.setMetered(false);
        }
        // Explicitly allow both families to prevent a family being blocked if no DNS server is found with it
        builder.allowFamily(AF_INET);
        builder.allowFamily(AF_INET6);
        // Create a new interface.
        ParcelFileDescriptor pfd = builder
                .setSession("AdAway")
                .setConfigureIntent(PendingIntent.getActivity(
                        service,
                        1,
                        new Intent(service, HomeActivity.class),
                        FLAG_CANCEL_CURRENT | FLAG_IMMUTABLE
                ))
                .establish();
        Timber.i("VPN established.");
        return pfd;
    }

    private static void excludeApplicationsFromVpn(Context context, VpnService.Builder builder) {
        PackageManager packageManager = context.getPackageManager();

        ApplicationInfo self = context.getApplicationInfo();
        Set<String> excludedApps = PreferenceHelper.getVpnExcludedApps(context);
        String vpnExcludedSystemApps = PreferenceHelper.getVpnExcludedSystemApps(context);
        Set<String> webBrowserPackageName = vpnExcludedSystemApps.equals("allExceptBrowsers") ?
                getWebBrowserPackageName(packageManager) :
                emptySet();

        List<ApplicationInfo> installedApplications = packageManager.getInstalledApplications(0);
        for (ApplicationInfo applicationInfo : installedApplications) {
            boolean excluded = false;
            // Skip itself
            if (applicationInfo.packageName.equals(self.packageName)) {
                continue;
            }
            // Check system app
            if ((applicationInfo.flags & FLAG_SYSTEM) != 0) {
                excluded = vpnExcludedSystemApps.equals("all") ||
                        (vpnExcludedSystemApps.equals("allExceptBrowsers") && !webBrowserPackageName.contains(applicationInfo.packageName));
            }
            // Check user excluded applications
            else if (excludedApps.contains(applicationInfo.packageName)) {
                excluded = true;
            }
            if (excluded) {
                try {
                    builder.addDisallowedApplication(applicationInfo.packageName);
                } catch (PackageManager.NameNotFoundException e) {
                    Timber.w(e, "Failed to exclude application %s from VPN.", applicationInfo.packageName);
                }
            }
        }
    }

    private static Set<String> getWebBrowserPackageName(PackageManager packageManager) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://isabrowser.adaway.org/"));
        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, 0);
        Set<String> packageNames = new HashSet<>();
        for (ResolveInfo resolveInfo : resolveInfoList) {
            packageNames.add(resolveInfo.activityInfo.packageName);
        }

        packageNames.add("com.google.android.webview");
        packageNames.add("com.android.htmlviewer");
        packageNames.add("com.google.android.backuptransport");
        packageNames.add("com.google.android.gms");
        packageNames.add("com.google.android.gsf");

        return packageNames;
    }
}
