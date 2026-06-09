package org.adaway.ui.prefs

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adaway.AdAwayApplication
import org.adaway.R
import org.adaway.helper.PreferenceHelper
import org.adaway.model.adblocking.AdBlockMethod
import org.adaway.model.source.SourceUpdateService
import org.adaway.model.update.ApkUpdateService
import org.adaway.model.update.UpdateStore
import org.adaway.util.Constants.PREFS_NAME
import org.adaway.util.WebServerUtils
import org.adaway.util.log.SentryLog
import org.adaway.vpn.VpnServiceControls
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import timber.log.Timber

class PrefsViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Main prefs states
    var darkThemeMode by mutableStateOf("")
        private set
    var dynamicColorEnabled by mutableStateOf(false)
        private set
    var enableIpv6 by mutableStateOf(false)
        private set
    var enableTelemetry by mutableStateOf(false)
        private set
    var enableDebug by mutableStateOf(false)
        private set
    var telemetrySupported by mutableStateOf(true)
        private set
    var adBlockMethod by mutableStateOf(AdBlockMethod.UNDEFINED)
        private set

    // Update screen states
    var notificationsDisabled by mutableStateOf(false)
        private set
    var checkAppStartup by mutableStateOf(false)
        private set
    var checkAppDaily by mutableStateOf(false)
        private set
    var includeBetaReleases by mutableStateOf(false)
        private set
    var includeBetaEnabled by mutableStateOf(true)
        private set
    var checkHostsStartup by mutableStateOf(false)
        private set
    var checkHostsDaily by mutableStateOf(false)
        private set
    var automaticUpdateDaily by mutableStateOf(false)
        private set
    var updateOnlyOnWifi by mutableStateOf(false)
        private set

    // Root screen states
    var neverReboot by mutableStateOf(false)
        private set
    var redirectionIpv4 by mutableStateOf("")
        private set
    var redirectionIpv6 by mutableStateOf("")
        private set
    var ipv6Enabled by mutableStateOf(false)
        private set
    var webServerEnabled by mutableStateOf(false)
        private set
    var webServerIcon by mutableStateOf(false)
        private set
    var webServerStateSummaryRes by mutableStateOf(R.string.pref_webserver_state_checking)

    // VPN screen states
    var serviceOnBoot by mutableStateOf(false)
        private set
    var watchdogEnabled by mutableStateOf(false)
        private set
    var excludedSystemApps by mutableStateOf("")
        private set

    init {
        reloadState()
    }

    fun reloadState() {
        darkThemeMode = prefs.getString(
            context.getString(R.string.pref_dark_theme_mode_key),
            context.getString(R.string.pref_dark_theme_mode_def)
        ) ?: context.getString(R.string.pref_dark_theme_mode_def)
        dynamicColorEnabled = PreferenceHelper.getDynamicColorEnabled(context)
        enableIpv6 = PreferenceHelper.getEnableIpv6(context)
        enableTelemetry = PreferenceHelper.getTelemetryEnabled(context)
        enableDebug = PreferenceHelper.getDebugEnabled(context)
        adBlockMethod = PreferenceHelper.getAdBlockMethod(context)
        telemetrySupported = !SentryLog.isStub()

        notificationsDisabled = Build.VERSION.SDK_INT >= TIRAMISU &&
                context.checkSelfPermission(POST_NOTIFICATIONS) != PERMISSION_GRANTED

        checkAppStartup = prefs.getBoolean(
            context.getString(R.string.pref_update_check_app_startup_key),
            context.resources.getBoolean(R.bool.pref_update_check_app_startup_def)
        )
        checkAppDaily = prefs.getBoolean(
            context.getString(R.string.pref_update_check_app_daily_key),
            context.resources.getBoolean(R.bool.pref_update_check_app_daily_def)
        )
        includeBetaReleases = prefs.getBoolean(
            context.getString(R.string.pref_update_include_beta_releases_key),
            context.resources.getBoolean(R.bool.pref_update_include_beta_releases_def)
        )
        checkHostsStartup = prefs.getBoolean(
            context.getString(R.string.pref_update_check_key),
            context.resources.getBoolean(R.bool.pref_update_check_def)
        )
        checkHostsDaily = prefs.getBoolean(
            context.getString(R.string.pref_update_check_hosts_daily_key),
            context.resources.getBoolean(R.bool.pref_update_check_hosts_daily_def)
        )
        automaticUpdateDaily = prefs.getBoolean(
            context.getString(R.string.pref_automatic_update_daily_key),
            context.resources.getBoolean(R.bool.pref_automatic_update_daily_def)
        )
        updateOnlyOnWifi = prefs.getBoolean(
            context.getString(R.string.pref_update_only_on_wifi_key),
            context.resources.getBoolean(R.bool.pref_update_only_on_wifi_def)
        )

        val application = context.applicationContext as AdAwayApplication
        includeBetaEnabled = application.updateModel.store == UpdateStore.ADAWAY

        neverReboot = prefs.getBoolean(
            context.getString(R.string.pref_never_reboot_key),
            context.resources.getBoolean(R.bool.pref_never_reboot_def)
        )
        redirectionIpv4 = prefs.getString(
            context.getString(R.string.pref_redirection_ipv4_key),
            context.getString(R.string.pref_redirection_ipv4_def)
        ).orEmpty()
        redirectionIpv6 = prefs.getString(
            context.getString(R.string.pref_redirection_ipv6_key),
            context.getString(R.string.pref_redirection_ipv6_def)
        ).orEmpty()
        ipv6Enabled = PreferenceHelper.getEnableIpv6(context)
        webServerEnabled = PreferenceHelper.getWebServerEnabled(context)
        webServerIcon = PreferenceHelper.getWebServerIcon(context)

        serviceOnBoot = prefs.getBoolean(
            context.getString(R.string.pref_vpn_service_on_boot_key),
            context.resources.getBoolean(R.bool.pref_vpn_service_on_boot_def)
        )
        watchdogEnabled = prefs.getBoolean(
            context.getString(R.string.pref_vpn_watchdog_enabled_key),
            context.resources.getBoolean(R.bool.pref_vpn_watchdog_enabled_def)
        )
        excludedSystemApps = PreferenceHelper.getVpnExcludedSystemApps(context)
    }

    fun updateWebServerState() {
        webServerStateSummaryRes = R.string.pref_webserver_state_checking
        viewModelScope.launch {
            delay(500)
            val summaryResId = withContext(Dispatchers.IO) {
                WebServerUtils.getWebServerState()
            }
            webServerStateSummaryRes = summaryResId
        }
    }

    fun updateDarkThemeMode(mode: String) {
        darkThemeMode = mode
        prefs.edit()
            .putString(context.getString(R.string.pref_dark_theme_mode_key), mode)
            .apply()
    }

    fun updateDynamicColorEnabled(enabled: Boolean) {
        dynamicColorEnabled = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_dynamic_color_key), enabled)
            .apply()
    }

    fun updateEnableIpv6(enabled: Boolean) {
        enableIpv6 = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_enable_ipv6_key), enabled)
            .apply()
    }

    fun updateEnableTelemetry(enabled: Boolean) {
        if (!telemetrySupported) return
        enableTelemetry = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_enable_telemetry_key), enabled)
            .apply()
        SentryLog.setEnabled(context.applicationContext as Application, enabled)
    }

    fun updateEnableDebug(enabled: Boolean) {
        enableDebug = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_enable_debug_key), enabled)
            .apply()
    }

    fun updateCheckAppStartup(enabled: Boolean) {
        checkAppStartup = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_update_check_app_startup_key), enabled)
            .apply()
    }

    fun updateCheckAppDaily(enabled: Boolean) {
        checkAppDaily = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_update_check_app_daily_key), enabled)
            .apply()
        if (enabled) {
            ApkUpdateService.enable(context)
        } else {
            ApkUpdateService.disable(context)
        }
    }

    fun updateIncludeBetaReleases(enabled: Boolean) {
        includeBetaReleases = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_update_include_beta_releases_key), enabled)
            .apply()
    }

    fun updateCheckHostsStartup(enabled: Boolean) {
        checkHostsStartup = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_update_check_key), enabled)
            .apply()
    }

    fun updateCheckHostsDaily(enabled: Boolean) {
        checkHostsDaily = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_update_check_hosts_daily_key), enabled)
            .apply()
        if (enabled) {
            SourceUpdateService.enable(context, updateOnlyOnWifi)
        } else {
            SourceUpdateService.disable(context)
        }
    }

    fun updateAutomaticUpdateDaily(enabled: Boolean) {
        automaticUpdateDaily = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_automatic_update_daily_key), enabled)
            .apply()
    }

    fun updateUpdateOnlyOnWifi(enabled: Boolean) {
        updateOnlyOnWifi = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_update_only_on_wifi_key), enabled)
            .apply()
        SourceUpdateService.enable(context, enabled)
    }

    fun updateNeverReboot(enabled: Boolean) {
        neverReboot = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_never_reboot_key), enabled)
            .apply()
    }

    fun saveRedirectionIpv4(redirection: String) {
        redirectionIpv4 = redirection
        prefs.edit()
            .putString(context.getString(R.string.pref_redirection_ipv4_key), redirection)
            .apply()
    }

    fun saveRedirectionIpv6(redirection: String) {
        redirectionIpv6 = redirection
        prefs.edit()
            .putString(context.getString(R.string.pref_redirection_ipv6_key), redirection)
            .apply()
    }

    fun updateWebServerEnabled(enabled: Boolean) {
        if (enabled) {
            WebServerUtils.startWebServer(context)
            webServerEnabled = WebServerUtils.isWebServerRunning()
        } else {
            WebServerUtils.stopWebServer()
            webServerEnabled = WebServerUtils.isWebServerRunning()
        }
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_webserver_enabled_key), webServerEnabled)
            .apply()
        updateWebServerState()
    }

    fun updateWebServerIcon(enabled: Boolean) {
        webServerIcon = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_webserver_icon_key), enabled)
            .apply()
        if (WebServerUtils.isWebServerRunning()) {
            WebServerUtils.stopWebServer()
            WebServerUtils.startWebServer(context)
            updateWebServerState()
        }
    }

    fun updateServiceOnBoot(enabled: Boolean) {
        serviceOnBoot = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_vpn_service_on_boot_key), enabled)
            .apply()
    }

    fun updateWatchdogEnabled(enabled: Boolean) {
        watchdogEnabled = enabled
        prefs.edit()
            .putBoolean(context.getString(R.string.pref_vpn_watchdog_enabled_key), enabled)
            .apply()
    }

    fun updateExcludedSystemApps(value: String) {
        excludedSystemApps = value
        prefs.edit()
            .putString(context.getString(R.string.pref_vpn_excluded_system_apps_key), value)
            .apply()
        if (VpnServiceControls.isRunning(context)) {
            VpnServiceControls.stop(context)
            VpnServiceControls.start(context)
        }
    }
}
