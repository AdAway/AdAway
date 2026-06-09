package org.adaway.ui.navigation

import android.content.Intent

object AdAwayRoute {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val HOSTS = "hosts"
    const val LOG = "log"
    const val HELP = "help"
    const val SUPPORT = "support"
    const val PREFS = "prefs"
    const val PREFS_UPDATE = "prefs/update"
    const val PREFS_ROOT = "prefs/root"
    const val PREFS_VPN = "prefs/vpn"
    const val PREFS_BACKUP_RESTORE = "prefs/backupRestore"
    const val VPN_EXCLUDED_APPS = "vpnExcludedApps"
    const val UPDATE = "update"

    const val SOURCE_ID_ARGUMENT = "sourceId"
    const val NO_SOURCE_ID = -1
    const val SOURCE_EDIT = "sourceEdit?$SOURCE_ID_ARGUMENT={$SOURCE_ID_ARGUMENT}"

    const val LIST_TAB_ARGUMENT = "tab"
    const val LISTS = "lists/{$LIST_TAB_ARGUMENT}"

    fun sourceEdit(sourceId: Int?): String {
        return if (sourceId == null) {
            "sourceEdit"
        } else {
            "sourceEdit?$SOURCE_ID_ARGUMENT=$sourceId"
        }
    }

    @JvmStatic
    fun list(tab: Int): String {
        return "lists/$tab"
    }
}

object ListsRouteDefaults {
    const val BLOCKED_HOSTS_TAB = 0
    const val ALLOWED_HOSTS_TAB = 1
    const val REDIRECTED_HOSTS_TAB = 2
}

object NavigationRequest {
    const val EXTRA_ROUTE = "org.adaway.ui.navigation.ROUTE"

    @JvmStatic
    fun routeFrom(intent: Intent?): String? {
        return intent?.getStringExtra(EXTRA_ROUTE)
    }
}
