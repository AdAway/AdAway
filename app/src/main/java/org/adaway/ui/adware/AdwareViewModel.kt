package org.adaway.ui.adware

import android.app.Application
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.annotation.WorkerThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

open class AdwareViewModel(application: Application) : AndroidViewModel(application) {
    private val _adware = MutableStateFlow<List<AdwareInstall>?>(null)
    val adware: StateFlow<List<AdwareInstall>?> = _adware

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _adware.value = loadData()
        }
    }

    @WorkerThread
    private fun loadData(): List<AdwareInstall> {
        val packageManager = getApplication<Application>().packageManager
        return getAdwarePackages(packageManager)
            .map(::createInstallFromPackageInfo)
            .sorted()
    }

    private fun getAdwarePackages(packageManager: PackageManager): List<PackageInfo> {
        val adwarePackages = mutableListOf<PackageInfo>()
        for (applicationInfo in packageManager.getInstalledApplications(0)) {
            try {
                val packageInfo = packageManager.getPackageInfo(
                    applicationInfo.packageName,
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES
                )
                if (isAdware(packageInfo)) {
                    adwarePackages += packageInfo
                }
            } catch (exception: Exception) {
                Timber.e(exception, "An error occurred while scanning applications for adware")
            }
        }
        return adwarePackages
    }

    private fun isAdware(info: PackageInfo): Boolean {
        val packageName = info.packageName
        Timber.v("Scanning package %s", packageName)
        val matchActivity = info.activities?.let { checkComponent(packageName, "activity", it.asIterable()) } == true
        val matchReceiver = info.receivers?.let { checkComponent(packageName, "receiver", it.asIterable()) } == true
        val matchService = info.services?.let { checkComponent(packageName, "service", it.asIterable()) } == true
        return matchActivity || matchReceiver || matchService
    }

    private fun checkComponent(packageName: String, type: String, info: Iterable<ComponentInfo>): Boolean {
        for (componentInfo in info) {
            val componentName = componentInfo.name
            Timber.v("[%s] %s", type, componentName)
            for (adPackagePrefix in AD_PACKAGE_PREFIXES) {
                if (componentName.startsWith(adPackagePrefix)) {
                    Timber.i(
                        "Detected ad framework prefix %s in package %s as %s %s",
                        adPackagePrefix,
                        packageName,
                        type,
                        componentName
                    )
                    return true
                }
            }
        }
        return false
    }

    private fun createInstallFromPackageInfo(packageInfo: PackageInfo): AdwareInstall {
        val packageManager = getApplication<Application>().packageManager
        val applicationInfo = packageInfo.applicationInfo ?: packageManager.getApplicationInfo(packageInfo.packageName, 0)
        val applicationName = packageManager.getApplicationLabel(applicationInfo).toString()
        return AdwareInstall(applicationName, packageInfo.packageName)
    }

    companion object {
        private val AD_PACKAGE_PREFIXES = arrayOf(
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
            "cn.kuguo."
        )
    }
}
