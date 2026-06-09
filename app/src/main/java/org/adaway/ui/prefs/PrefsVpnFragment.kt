package org.adaway.ui.prefs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressiveAsymmetricShape2
import org.adaway.ui.compose.ExpressivePage
import org.adaway.ui.compose.safeClickable
import org.adaway.ui.compose.ExpressiveSelectionBottomSheet

@Composable
internal fun PrefsVpnScreen(
    serviceOnBoot: Boolean,
    watchdogEnabled: Boolean,
    excludedSystemApps: String,
    onServiceOnBootChanged: (Boolean) -> Unit,
    onWatchdogChanged: (Boolean) -> Unit,
    onExcludedSystemAppsChanged: (String) -> Unit,
    onOpenExcludedUserApps: () -> Unit
) {
    val excludedEntries = stringArrayResource(R.array.pref_vpn_excluded_system_apps_entries)
    val excludedValues = stringArrayResource(R.array.pref_vpn_excluded_system_apps_values)
    val selectedExcludedLabel = remember(excludedSystemApps, excludedEntries, excludedValues) {
        val index = excludedValues.indexOf(excludedSystemApps)
        if (index in excludedEntries.indices) {
            excludedEntries[index]
        } else {
            excludedEntries.firstOrNull().orEmpty()
        }
    }
    var showExcludedDialog by remember { mutableStateOf(false) }

    ExpressiveSelectionBottomSheet(
        show = showExcludedDialog,
        onDismissRequest = { showExcludedDialog = false },
        title = stringResource(R.string.pref_vpn_exclude_system_apps),
        options = excludedValues.toList(),
        selectedOption = excludedSystemApps,
        optionLabel = { value ->
            val index = excludedValues.indexOf(value)
            if (index in excludedEntries.indices) excludedEntries[index] else ""
        },
        onOptionSelected = onExcludedSystemAppsChanged
    )

    ExpressivePage {
        PreferenceCategoryHeader(titleRes = R.string.pref_general_category)
        PreferenceSection(shape = ExpressiveAsymmetricShape1) {
            PreferenceToggleRow(
                iconRes = R.drawable.ic_vpn_key_24dp,
                titleRes = R.string.pref_vpn_service_on_boot,
                checked = serviceOnBoot,
                onCheckedChange = onServiceOnBootChanged
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_sync_24dp,
                titleRes = R.string.pref_vpn_service_monitor,
                summary = stringResource(R.string.pref_vpn_service_monitor_description),
                checked = watchdogEnabled,
                onCheckedChange = onWatchdogChanged
            )
        }

        PreferenceCategoryHeader(titleRes = R.string.pref_vpn_excluded_apps)
        PreferenceSection(shape = ExpressiveAsymmetricShape2) {
            PreferenceDescription(textRes = R.string.pref_vpn_excluded_apps_description)
            PreferenceDivider()
            PreferenceRow(
                iconRes = R.drawable.ic_settings_24dp,
                titleRes = R.string.pref_vpn_exclude_system_apps,
                summary = selectedExcludedLabel,
                onClick = { showExcludedDialog = true }
            )
            PreferenceDivider()
            PreferenceRow(
                iconRes = R.drawable.ic_menu_24dp,
                titleRes = R.string.pref_vpn_exclude_user_apps,
                onClick = onOpenExcludedUserApps
            )
        }
        Spacer(modifier = Modifier.size(32.dp))
    }
}



