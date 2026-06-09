package org.adaway.ui.prefs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressivePage

@Composable
internal fun PrefsBackupRestoreScreen(
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    ExpressivePage {
        PreferenceSection(shape = ExpressiveAsymmetricShape1) {
            PreferenceRow(
                iconRes = R.drawable.ic_save_24dp,
                titleRes = R.string.pref_backup,
                summary = stringResource(R.string.pref_backup_summary),
                onClick = onBackupClick
            )
            PreferenceDivider()
            PreferenceRow(
                iconRes = R.drawable.ic_settings_backup_restore_24dp,
                titleRes = R.string.pref_restore,
                summary = stringResource(R.string.pref_restore_summary),
                onClick = onRestoreClick
            )
        }
        Spacer(modifier = Modifier.size(32.dp))
    }
}
