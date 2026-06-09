package org.adaway.ui.prefs

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressiveAsymmetricShape2
import org.adaway.ui.compose.ExpressivePage

@Composable
internal fun PrefsRootScreen(
    neverReboot: Boolean,
    redirectionIpv4: String,
    redirectionIpv6: String,
    ipv6Enabled: Boolean,
    webServerEnabled: Boolean,
    webServerIcon: Boolean,
    @StringRes webServerStateSummaryRes: Int,
    onOpenHostsFile: () -> Unit,
    onNeverRebootChanged: (Boolean) -> Unit,
    onEditIpv4: (String) -> Unit,
    onEditIpv6: (String) -> Unit,
    onWebServerEnabledChanged: (Boolean) -> Unit,
    onWebServerTest: () -> Unit,
    onInstallCertificate: () -> Unit,
    onWebServerIconChanged: (Boolean) -> Unit
) {
    ExpressivePage {
        PreferenceCategoryHeader(titleRes = R.string.pref_hosts_installation)
        PreferenceSection(shape = ExpressiveAsymmetricShape1) {
            PreferenceRow(
                iconRes = R.drawable.ic_collections_bookmark_24dp,
                titleRes = R.string.pref_root_open_hosts,
                onClick = onOpenHostsFile
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_settings_24dp,
                titleRes = R.string.pref_never_reboot,
                checked = neverReboot,
                onCheckedChange = onNeverRebootChanged
            )
        }

        PreferenceCategoryHeader(titleRes = R.string.pref_hosts_redirection)
        PreferenceSection(shape = ExpressiveAsymmetricShape2) {
            PreferenceRow(
                iconRes = R.drawable.ic_outline_rule_24,
                titleRes = R.string.pref_redirection_ipv4,
                summary = redirectionIpv4,
                onClick = { onEditIpv4(redirectionIpv4) }
            )
            PreferenceDivider()
            PreferenceRow(
                iconRes = R.drawable.ic_outline_rule_24,
                titleRes = R.string.pref_redirection_ipv6,
                summary = redirectionIpv6,
                enabled = ipv6Enabled,
                onClick = { onEditIpv6(redirectionIpv6) }
            )
        }

        PreferenceCategoryHeader(titleRes = R.string.pref_webserver)
        PreferenceSection(shape = ExpressiveAsymmetricShape1) {
            PreferenceDescription(textRes = R.string.pref_webserver_summary)
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_sync_24dp,
                titleRes = R.string.pref_webserver_enabled,
                checked = webServerEnabled,
                onCheckedChange = onWebServerEnabledChanged
            )
            PreferenceDivider()
            PreferenceRow(
                iconRes = R.drawable.ic_help_24dp,
                titleRes = R.string.pref_webserver_test,
                summary = stringResource(webServerStateSummaryRes),
                enabled = webServerEnabled,
                onClick = onWebServerTest
            )
            PreferenceDivider()
            PreferenceRow(
                iconRes = R.drawable.ic_get_app_24dp,
                titleRes = R.string.pref_webserver_certificate,
                enabled = webServerEnabled,
                onClick = onInstallCertificate
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.logo,
                titleRes = R.string.pref_webserver_icon,
                checked = webServerIcon,
                enabled = webServerEnabled,
                iconTint = Color.Unspecified,
                onCheckedChange = onWebServerIconChanged
            )
        }
        Spacer(modifier = Modifier.size(32.dp))
    }
}
