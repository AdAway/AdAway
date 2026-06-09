package org.adaway.ui.prefs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import org.adaway.ui.compose.ExpressiveScaffold
import org.adaway.ui.compose.ExpressiveTopBar

@Composable
internal fun PrefsRoute(
    destination: PrefsDestination,
    onNavigateBack: () -> Unit,
    onNavigate: (PrefsDestination) -> Unit,
    onOpenVpnExcludedApps: () -> Unit,
    viewModel: PrefsViewModel = viewModel()
) {
    ExpressiveScaffold(
        topBar = {
            ExpressiveTopBar(
                title = stringResource(destination.titleRes),
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrefsContent(
                destination = destination,
                viewModel = viewModel,
                onNavigate = onNavigate,
                onOpenVpnExcludedApps = onOpenVpnExcludedApps
            )
        }
    }
}
