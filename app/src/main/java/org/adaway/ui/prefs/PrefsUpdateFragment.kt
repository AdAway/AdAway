package org.adaway.ui.prefs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressiveAsymmetricShape2
import org.adaway.ui.compose.ExpressivePage

@Composable
internal fun PrefsUpdateScreen(
    notificationsDisabled: Boolean,
    checkAppStartup: Boolean,
    checkAppDaily: Boolean,
    includeBetaReleases: Boolean,
    includeBetaEnabled: Boolean,
    checkHostsStartup: Boolean,
    checkHostsDaily: Boolean,
    automaticUpdateDaily: Boolean,
    updateOnlyOnWifi: Boolean,
    onOpenNotifications: () -> Unit,
    onCheckAppStartupChanged: (Boolean) -> Unit,
    onCheckAppDailyChanged: (Boolean) -> Unit,
    onIncludeBetaChanged: (Boolean) -> Unit,
    onCheckHostsStartupChanged: (Boolean) -> Unit,
    onCheckHostsDailyChanged: (Boolean) -> Unit,
    onAutomaticUpdateDailyChanged: (Boolean) -> Unit,
    onUpdateOnlyWifiChanged: (Boolean) -> Unit
) {
    ExpressivePage {
        if (notificationsDisabled) {
            PreferenceSection(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                shape = ExpressiveAsymmetricShape1
            ) {
                PreferenceRow(
                    iconRes = R.drawable.notifications_off_24,
                    titleRes = R.string.pref_update_enable_notifications,
                    summary = stringResource(R.string.pref_update_enable_notifications_summary),
                    onClick = onOpenNotifications,
                    iconTint = MaterialTheme.colorScheme.onErrorContainer,
                    titleColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        PreferenceCategoryHeader(titleRes = R.string.pref_update_app_category)
        PreferenceSection(shape = ExpressiveAsymmetricShape2) {
            PreferenceToggleRow(
                iconRes = R.drawable.ic_sync_24dp,
                titleRes = R.string.pref_update_check_app_startup,
                checked = checkAppStartup,
                onCheckedChange = onCheckAppStartupChanged
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_sync_24dp,
                titleRes = R.string.pref_update_check_app_daily,
                checked = checkAppDaily,
                onCheckedChange = onCheckAppDailyChanged
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_outline_rule_24,
                titleRes = R.string.pref_update_include_beta_releases,
                checked = includeBetaReleases,
                enabled = includeBetaEnabled,
                onCheckedChange = onIncludeBetaChanged
            )
        }

        PreferenceCategoryHeader(titleRes = R.string.pref_update_hosts_category)
        PreferenceSection(shape = ExpressiveAsymmetricShape1) {
            PreferenceToggleRow(
                iconRes = R.drawable.ic_sync_24dp,
                titleRes = R.string.pref_update_check,
                checked = checkHostsStartup,
                onCheckedChange = onCheckHostsStartupChanged
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_sync_24dp,
                titleRes = R.string.pref_update_check_hosts_daily,
                checked = checkHostsDaily,
                onCheckedChange = onCheckHostsDailyChanged
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_playlist_add_24dp,
                titleRes = R.string.pref_update_sync_on_update,
                checked = automaticUpdateDaily,
                enabled = checkHostsDaily,
                onCheckedChange = onAutomaticUpdateDailyChanged
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_vpn_key_24dp,
                titleRes = R.string.pref_update_sync_unmetered_only,
                checked = updateOnlyOnWifi,
                enabled = checkHostsDaily,
                onCheckedChange = onUpdateOnlyWifiChanged
            )
        }
        Spacer(modifier = Modifier.size(32.dp))
    }
}



