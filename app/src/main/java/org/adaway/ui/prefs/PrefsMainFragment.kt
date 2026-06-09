package org.adaway.ui.prefs

import androidx.compose.foundation.layout.Arrangement
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
import org.adaway.ui.compose.ExpressiveSelectionBottomSheet

@Composable
internal fun PrefsMainScreen(
    darkThemeMode: String,
    dynamicColorEnabled: Boolean,
    dynamicColorSupported: Boolean,
    enableIpv6: Boolean,
    enableTelemetry: Boolean,
    enableDebug: Boolean,
    telemetrySupported: Boolean,
    rootConfigEnabled: Boolean,
    vpnConfigEnabled: Boolean,
    onThemeSelected: (String) -> Unit,
    onDynamicColorEnabledChanged: (Boolean) -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenRootConfig: () -> Unit,
    onOpenVpnConfig: () -> Unit,
    onEnableIpv6Changed: (Boolean) -> Unit,
    onOpenBackupRestore: () -> Unit,
    onEnableTelemetryChanged: (Boolean) -> Unit,
    onEnableDebugChanged: (Boolean) -> Unit
) {
    val themeLabels = stringArrayResource(R.array.pref_dark_theme_modes)
    val themeValues = stringArrayResource(R.array.pref_dark_theme_mode_entry_values)
    val selectedThemeLabel = remember(darkThemeMode, themeLabels, themeValues) {
        val index = themeValues.indexOf(darkThemeMode)
        if (index in themeLabels.indices) {
            themeLabels[index]
        } else {
            themeLabels.lastOrNull().orEmpty()
        }
    }
    var showThemeDialog by remember { mutableStateOf(false) }

    ExpressiveSelectionBottomSheet(
        show = showThemeDialog,
        onDismissRequest = { showThemeDialog = false },
        title = stringResource(R.string.pref_dark_theme),
        options = themeValues.toList(),
        selectedOption = darkThemeMode,
        optionLabel = { value ->
            val index = themeValues.indexOf(value)
            if (index in themeLabels.indices) themeLabels[index] else ""
        },
        onOptionSelected = onThemeSelected
    )

    ExpressivePage {
        PreferenceCategoryHeader(titleRes = R.string.pref_general_category)
        PreferenceSection(shape = ExpressiveAsymmetricShape1) {
            PreferenceRow(
                iconRes = R.drawable.ic_brightness_medium_24dp,
                titleRes = R.string.pref_dark_theme,
                summary = selectedThemeLabel,
                onClick = { showThemeDialog = true }
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_brightness_medium_24dp,
                titleRes = R.string.pref_dynamic_colors,
                summary = stringResource(
                    if (dynamicColorSupported) {
                        R.string.pref_dynamic_colors_summary
                    } else {
                        R.string.pref_dynamic_colors_unsupported_summary
                    }
                ),
                checked = dynamicColorEnabled && dynamicColorSupported,
                enabled = dynamicColorSupported,
                onCheckedChange = onDynamicColorEnabledChanged
            )
            PreferenceDivider()
            PreferenceRow(
                iconRes = R.drawable.ic_sync_24dp,
                titleRes = R.string.pref_update_configuration,
                onClick = onOpenUpdate
            )
        }

        PreferenceCategoryHeader(titleRes = R.string.pref_ad_block_category)
        PreferenceSection(shape = ExpressiveAsymmetricShape2) {
            PreferenceRow(
                iconRes = R.drawable.ic_superuser_24dp,
                titleRes = R.string.pref_root_ad_blocker_configuration,
                enabled = rootConfigEnabled,
                onClick = onOpenRootConfig
            )
            PreferenceDivider()
            PreferenceRow(
                iconRes = R.drawable.ic_vpn_key_24dp,
                titleRes = R.string.pref_vpn_ad_blocker_configuration,
                enabled = vpnConfigEnabled,
                onClick = onOpenVpnConfig
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_ipv6_24dp,
                titleRes = R.string.pref_enable_ipv6,
                checked = enableIpv6,
                onCheckedChange = onEnableIpv6Changed
            )
            PreferenceDivider()
            PreferenceRow(
                iconRes = R.drawable.ic_sd_storage_24dp,
                titleRes = R.string.pref_backup_restore,
                onClick = onOpenBackupRestore
            )
        }

        PreferenceCategoryHeader(titleRes = R.string.pref_debug_category)
        PreferenceSection(shape = ExpressiveAsymmetricShape1) {
            PreferenceToggleRow(
                iconRes = R.drawable.outline_cloud_upload_24,
                titleRes = R.string.pref_enable_telemetry,
                summary = stringResource(
                    if (telemetrySupported) {
                        R.string.pref_enable_telemetry_summary
                    } else {
                        R.string.pref_enable_telemetry_disabled_summary
                    }
                ),
                checked = enableTelemetry,
                enabled = telemetrySupported,
                onCheckedChange = onEnableTelemetryChanged
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_bug_report_24dp,
                titleRes = R.string.pref_enable_debug,
                summary = stringResource(R.string.pref_enable_debug_summary),
                checked = enableDebug,
                onCheckedChange = onEnableDebugChanged
            )
        }
        Spacer(modifier = Modifier.size(32.dp))
    }
}



