package org.adaway.ui.prefs.exclusion

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.adaway.R
import org.adaway.helper.PreferenceHelper
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressiveAsymmetricShape2
import org.adaway.ui.compose.ExpressiveScaffold
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.compose.ExpressiveTopBar
import org.adaway.ui.compose.safeClickable

@Composable
internal fun VpnExcludedAppsRoute(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var applications by remember { mutableStateOf(emptyList<UserApp>()) }

    LaunchedEffect(context) {
        applications = withContext(Dispatchers.Default) {
            loadUserApplications(context.applicationContext)
        }
    }

    fun updateApplications(update: (UserApp) -> Unit) {
        applications.forEach(update)
        applications = applications.toList()
        PreferenceHelper.setVpnExcludedApps(
            context,
            applications
                .filter { application -> application.excluded }
                .map { application -> application.packageName.toString() }
                .toSet()
        )
    }

    VpnExcludedAppsScreen(
        applications = applications,
        onNavigateBack = onNavigateBack,
        onSelectAll = {
            updateApplications { application -> application.excluded = true }
        },
        onDeselectAll = {
            updateApplications { application -> application.excluded = false }
        },
        onToggleExcluded = { app, excluded ->
            updateApplications { application ->
                if (application.packageName == app.packageName) {
                    application.excluded = excluded
                }
            }
        }
    )
}

private fun loadUserApplications(context: Context): List<UserApp> {
    val packageManager: PackageManager = context.packageManager
    val self = context.applicationInfo
    val excludedApps = PreferenceHelper.getVpnExcludedApps(context)

    return packageManager.getInstalledApplications(0)
        .asSequence()
        .filter { applicationInfo ->
            (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        }
        .filter { applicationInfo ->
            applicationInfo.packageName != self.packageName
        }
        .map { applicationInfo ->
            UserApp(
                packageManager.getApplicationLabel(applicationInfo),
                applicationInfo.packageName,
                packageManager.getApplicationIcon(applicationInfo),
                excludedApps.contains(applicationInfo.packageName)
            )
        }
        .sorted()
        .toList()
}

@Composable
private fun VpnExcludedAppsScreen(
    applications: List<UserApp>,
    onNavigateBack: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onToggleExcluded: (UserApp, Boolean) -> Unit
) {
    ExpressiveScaffold(
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.pref_vpn_exclude_user_apps_activity),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = onSelectAll) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_check_24),
                            contentDescription = stringResource(R.string.pref_vpn_exclude_user_apps_select_all)
                        )
                    }
                    IconButton(onClick = onDeselectAll) {
                        Icon(
                            painter = painterResource(R.drawable.outline_delete_24),
                            contentDescription = stringResource(R.string.pref_vpn_exclude_user_apps_deselect_all)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(
                items = applications,
                key = { _, application -> application.packageName.toString() }
            ) { index, application ->
                UserAppCard(
                    application = application,
                    shape = if (index % 2 == 0) ExpressiveAsymmetricShape1 else ExpressiveAsymmetricShape2,
                    onToggle = { checked -> onToggleExcluded(application, checked) }
                )
            }
        }
    }
}

@Composable
private fun UserAppCard(
    application: UserApp,
    shape: Shape,
    onToggle: (Boolean) -> Unit
) {
    ExpressiveSection(
        modifier = Modifier.safeClickable { onToggle(!application.excluded) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape
    ) {
        val iconSizeDp = 40.dp
        val iconSizePx = with(LocalDensity.current) { iconSizeDp.roundToPx() }
        val iconBitmap = remember(application.icon, iconSizePx) {
            application.icon
                .toBitmap(width = iconSizePx, height = iconSizePx)
                .asImageBitmap()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(iconSizeDp)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = application.name.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = application.packageName.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                Switch(
                    checked = application.excluded,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}
