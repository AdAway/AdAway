package org.adaway.ui.adware

import org.adaway.ui.compose.safeClickable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.StateFlow
import org.adaway.R
import org.adaway.ui.compose.ExpressiveSection

/**
 * Compose UI for scanning and uninstalling adware.
 */
@Composable
internal fun AdwareScreen(
    adware: StateFlow<List<AdwareInstall>?>,
    onUninstall: (AdwareInstall) -> Unit
) {
    val data by adware.collectAsStateWithLifecycle()

    ExpressiveSection(
        modifier = Modifier.padding(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.adware_header),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text(
                text = stringResource(R.string.adware_description),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )

            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            val currentData = data
            if (currentData == null) {
                Text(
                    text = stringResource(R.string.adware_scanning),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (currentData.isEmpty()) {
                Text(
                    text = stringResource(R.string.adware_empty),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
                    modifier = Modifier.height(300.dp) // Limit height if needed, or let it expand
                ) {
                    items(currentData, key = { install ->
                        install[AdwareInstall.PACKAGE_NAME_KEY] ?: install.hashCode()
                    }) { install ->
                        AdwareInstallItem(install = install, onClick = { onUninstall(install) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AdwareInstallItem(
    install: AdwareInstall,
    onClick: () -> Unit
) {
    val appName = install[AdwareInstall.APPLICATION_NAME_KEY].orEmpty()
    val packageName = install[AdwareInstall.PACKAGE_NAME_KEY].orEmpty()
    
    ExpressiveSection(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .safeClickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}



