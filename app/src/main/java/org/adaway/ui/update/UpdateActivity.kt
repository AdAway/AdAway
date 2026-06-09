package org.adaway.ui.update

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.ui.compose.ExpressiveActionCard
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressiveAsymmetricShape2
import org.adaway.ui.compose.ExpressivePage
import org.adaway.ui.compose.ExpressiveScaffold
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.compose.ExpressiveTopBar
import org.adaway.ui.compose.ScallopedShape
import org.adaway.ui.compose.WavyProgressIndicator

private data class UpdateScreenState(
    @param:StringRes @field:StringRes val headerRes: Int = R.string.update_up_to_date_header,
    val changelog: String = "",
    val showUpdateButton: Boolean = false,
    val showProgress: Boolean = false,
    val progress: Int? = null,
    val progressLabel: String = ""
)

@Composable
internal fun UpdateRoute(
    viewModel: UpdateViewModel,
    onNavigateBack: () -> Unit,
    onDonate: () -> Unit,
    onSponsor: () -> Unit
) {
    val context = LocalContext.current
    val manifest by viewModel.appManifest.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val progressLabel = downloadStatus?.format(context).orEmpty()
    val state = remember(manifest, downloadStatus, downloading, progressLabel) {
        val updateAvailable = manifest?.updateAvailable == true
        val showProgress = downloading || downloadStatus != null
        UpdateScreenState(
            headerRes = if (updateAvailable) {
                R.string.update_update_available_header
            } else {
                R.string.update_up_to_date_header
            },
            changelog = manifest?.changelog.orEmpty(),
            showUpdateButton = updateAvailable && !showProgress,
            showProgress = showProgress,
            progress = downloadStatus?.progress,
            progressLabel = progressLabel
        )
    }

    UpdateScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onUpdate = viewModel::update,
        onDonate = onDonate,
        onSponsor = onSponsor
    )
}

@Composable
private fun UpdateScreen(
    state: UpdateScreenState,
    onNavigateBack: () -> Unit,
    onUpdate: () -> Unit,
    onDonate: () -> Unit,
    onSponsor: () -> Unit
) {
    ExpressiveScaffold(
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.update_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        ExpressivePage(
            modifier = Modifier.padding(innerPadding)
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = stringResource(R.string.app_logo),
                modifier = Modifier.size(140.dp)
            )

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(
            targetState = state.headerRes,
            transitionSpec = {
                fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                        fadeOut(animationSpec = tween(90))
            },
            label = "headerTransition"
        ) { targetHeader ->
            Text(
                text = stringResource(targetHeader),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (state.showUpdateButton) {
            Button(
                onClick = onUpdate,
                modifier = Modifier.padding(top = 24.dp),
                shape = ExpressiveAsymmetricShape1
            ) {
                Text(
                    text = stringResource(R.string.update_update_button),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (state.showProgress) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val progress = state.progress
                if (progress == null) {
                    WavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    WavyProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                if (state.progressLabel.isNotEmpty()) {
                    Text(
                        text = state.progressLabel,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        ExpressiveSection(
            modifier = Modifier.padding(top = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = ExpressiveAsymmetricShape1
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.update_last_changelog_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = state.changelog,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        }

        ExpressiveSection(
            modifier = Modifier.padding(top = 16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = ExpressiveAsymmetricShape2
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.update_support_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExpressiveActionCard(
                        label = stringResource(R.string.update_donate_button),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = ExpressiveAsymmetricShape1,
                        onClick = onDonate,
                        icon = {
                            Image(
                                painter = painterResource(R.drawable.paypal),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                    ExpressiveActionCard(
                        label = stringResource(R.string.update_sponsor_button),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = ExpressiveAsymmetricShape2,
                        onClick = onSponsor,
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_github_24dp),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
