package org.adaway.ui.welcome

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Activity.RESULT_OK
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.provider.Settings
import android.provider.Settings.ACTION_VPN_SETTINGS
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.delay
import org.adaway.R
import org.adaway.helper.PreferenceHelper
import org.adaway.model.adblocking.AdBlockMethod
import org.adaway.model.error.HostError
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import org.adaway.ui.compose.ExpressiveActionCard
import org.adaway.ui.compose.ExpressivePage
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.compose.ExpressiveIconBadge
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressiveAsymmetricShape2
import org.adaway.ui.compose.ScallopedShape
import org.adaway.ui.compose.safeClickable
import org.adaway.ui.home.HomeViewModel
import org.adaway.ui.support.SupportLinks
import org.adaway.util.log.SentryLog

private enum class SetupMethod {
    NONE,
    ROOT,
    VPN
}

private data class MethodEntry(
    val iconRes: Int,
    val textRes: Int,
    val tint: Color
)

@Composable
fun WelcomeMethodStep(onCanProceedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var selectedMethod by rememberSaveable { mutableStateOf(SetupMethod.NONE) }
    var showRootMissingDialog by rememberSaveable { mutableStateOf(false) }
    var alwaysOnVpnMessage by rememberSaveable { mutableStateOf<Int?>(null) }

    val prepareVpnLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            SentryLog.recordBreadcrumb("Enable vpn ad-blocking method")
            PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.VPN)
            selectedMethod = SetupMethod.VPN
        } else {
            PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.UNDEFINED)
            selectedMethod = SetupMethod.NONE
            alwaysOnVpnMessage = getAlwaysOnVpnMessage(context)
        }
    }

    LaunchedEffect(selectedMethod) {
        onCanProceedChange(selectedMethod != SetupMethod.NONE)
    }

    val onRootClick = {
        PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.UNDEFINED)
        selectedMethod = SetupMethod.NONE

        Shell.getShell()
        if (java.lang.Boolean.TRUE == Shell.isAppGrantedRoot()) {
            SentryLog.recordBreadcrumb("Enable root ad-blocking method")
            PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.ROOT)
            selectedMethod = SetupMethod.ROOT
        } else {
            showRootMissingDialog = true
        }
    }

    val onVpnClick = {
        PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.UNDEFINED)
        selectedMethod = SetupMethod.NONE

        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent == null) {
            SentryLog.recordBreadcrumb("Enable vpn ad-blocking method")
            PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.VPN)
            selectedMethod = SetupMethod.VPN
        } else {
            prepareVpnLauncher.launch(prepareIntent)
        }
    }

    ExpressivePage {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .padding(top = 16.dp)
                .size(120.dp)
        )

        Text(
            text = stringResource(R.string.welcome_method_header),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        )

        Text(
            text = stringResource(R.string.welcome_method_summary),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WelcomeMethodCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                selected = selectedMethod == SetupMethod.ROOT,
                iconRes = R.drawable.ic_superuser_24dp,
                iconDescription = R.string.welcome_root_method_logo,
                titleRes = R.string.welcome_root_method_title,
                shape = ExpressiveAsymmetricShape1,
                entries = listOf(
                    MethodEntry(
                        iconRes = R.drawable.ic_add_circle_outline_24dp,
                        textRes = R.string.welcome_root_method_text1,
                        tint = MaterialTheme.colorScheme.primary
                    ),
                    MethodEntry(
                        iconRes = R.drawable.ic_add_circle_outline_24dp,
                        textRes = R.string.welcome_root_method_text2,
                        tint = MaterialTheme.colorScheme.primary
                    ),
                    MethodEntry(
                        iconRes = R.drawable.ic_remove_circle_outline_24dp,
                        textRes = R.string.welcome_root_method_text3,
                        tint = MaterialTheme.colorScheme.error
                    )
                ),
                onClick = onRootClick
            )

            WelcomeMethodCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                selected = selectedMethod == SetupMethod.VPN,
                iconRes = R.drawable.ic_vpn_key_24dp,
                iconDescription = R.string.welcome_vpn_method_logo,
                titleRes = R.string.welcome_vpn_method_title,
                shape = ExpressiveAsymmetricShape2,
                entries = listOf(
                    MethodEntry(
                        iconRes = R.drawable.ic_remove_circle_outline_24dp,
                        textRes = R.string.welcome_vpn_method_text1,
                        tint = MaterialTheme.colorScheme.error
                    ),
                    MethodEntry(
                        iconRes = R.drawable.ic_remove_circle_outline_24dp,
                        textRes = R.string.welcome_vpn_method_text2,
                        tint = MaterialTheme.colorScheme.error
                    ),
                    MethodEntry(
                        iconRes = R.drawable.ic_add_circle_outline_24dp,
                        textRes = R.string.welcome_vpn_method_text3,
                        tint = MaterialTheme.colorScheme.primary
                    )
                ),
                onClick = onVpnClick
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showRootMissingDialog) {
        AlertDialog(
            onDismissRequest = { showRootMissingDialog = false },
            title = { Text(stringResource(R.string.welcome_root_missing_title)) },
            text = { Text(stringResource(R.string.welcome_root_missile_description)) },
            confirmButton = {
                TextButton(onClick = { showRootMissingDialog = false }) {
                    Text(stringResource(R.string.button_close))
                }
            }
        )
    }

    alwaysOnVpnMessage?.let { messageRes ->
        AlertDialog(
            onDismissRequest = { alwaysOnVpnMessage = null },
            title = { Text(stringResource(R.string.welcome_vpn_alwayson_title)) },
            text = { Text(stringResource(messageRes)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        alwaysOnVpnMessage = null
                        context.startActivity(Intent(ACTION_VPN_SETTINGS))
                    }
                ) {
                    Text(stringResource(R.string.welcome_vpn_alwayson_settings_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { alwaysOnVpnMessage = null }) {
                    Text(stringResource(R.string.button_close))
                }
            }
        )
    }
}

@Composable
fun WelcomeSyncStep(
    onCanProceedChange: (Boolean) -> Unit,
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current

    var headerTextRes by rememberSaveable { mutableIntStateOf(R.string.welcome_sync_header) }
    var showProgress by rememberSaveable { mutableStateOf(true) }
    var showSyncedIcon by rememberSaveable { mutableStateOf(false) }
    var showErrorIcon by rememberSaveable { mutableStateOf(false) }
    var showRetry by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf("") }
    var showNotificationsText by rememberSaveable { mutableStateOf(false) }
    var syncStarted by rememberSaveable { mutableStateOf(false) }
    var requestNotificationsPermission by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { _ -> }

    val adBlocked by homeViewModel.adBlocked.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        onCanProceedChange(false)

        if (!syncStarted) {
            syncStarted = true
            homeViewModel.sync()
        }

        if (
            SDK_INT >= TIRAMISU &&
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            showNotificationsText = true
            requestNotificationsPermission = true
            delay(10_000)
            if (requestNotificationsPermission) {
                requestNotificationsPermission = false
                permissionLauncher.launch(POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(adBlocked) {
        if (adBlocked) {
            homeViewModel.enableAllSources()
            headerTextRes = R.string.welcome_synced_header
            showProgress = false
            showSyncedIcon = true
            showErrorIcon = false
            showRetry = false
            errorText = ""
            onCanProceedChange(true)

            if (SDK_INT >= TIRAMISU && requestNotificationsPermission) {
                requestNotificationsPermission = false
                permissionLauncher.launch(POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(homeViewModel) {
        homeViewModel.error.collect { hostError: HostError ->
            val errorMessage = context.getString(hostError.messageKey)
            errorText = context.getString(R.string.welcome_sync_error, errorMessage)
            showProgress = false
            showSyncedIcon = false
            showErrorIcon = true
            showRetry = true
            onCanProceedChange(false)
        }
    }

    val onRetry = {
        showErrorIcon = false
        showRetry = false
        errorText = ""
        showProgress = true
        onCanProceedChange(false)
        homeViewModel.sync()
    }

    ExpressivePage {
        Spacer(modifier = Modifier.size(32.dp))

        AnimatedContent(
            targetState = Triple(showProgress, showSyncedIcon, showErrorIcon),
            transitionSpec = {
                fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                    fadeOut(animationSpec = tween(90))
            },
            label = "statusIconTransition"
        ) { (progress, synced, showError) ->
            when {
                progress -> Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(ScallopedShape(12, 8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(80.dp),
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                synced -> ExpressiveIconBadge(
                    iconRes = R.drawable.baseline_check_24,
                    iconTint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    size = 120.dp,
                    iconSize = 64.dp,
                    shape = ScallopedShape(12, 8.dp)
                )

                showError -> ExpressiveIconBadge(
                    iconRes = R.drawable.ic_cloud_off_24dp,
                    iconTint = MaterialTheme.colorScheme.error,
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    size = 120.dp,
                    iconSize = 64.dp,
                    shape = ScallopedShape(12, 8.dp)
                )
            }
        }

        AnimatedContent(
            targetState = headerTextRes,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
            )
        }

        ExpressiveSection(
            modifier = Modifier.padding(top = 32.dp),
            shape = ExpressiveAsymmetricShape1
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.welcome_sync_summary),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showRetry) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .safeClickable(onClick = onRetry)
                    ) {
                        ExpressiveIconBadge(
                            iconRes = R.drawable.ic_sync_24dp,
                            iconTint = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            size = 64.dp,
                            iconSize = 32.dp,
                            shape = ScallopedShape(8, 4.dp)
                        )
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        )
                    }
                }
            }
        }

        if (showNotificationsText) {
            Text(
                text = stringResource(R.string.welcome_sync_notifications),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, bottom = 16.dp)
            )
        }
    }

}

@Composable
fun WelcomeSupportStep(onCanProceedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as? Application
    val showSponsorship = remember { SentryLog.isStub() }
    var telemetryEnabled by rememberSaveable {
        mutableStateOf(PreferenceHelper.getTelemetryEnabled(context))
    }

    LaunchedEffect(Unit) {
        onCanProceedChange(true)
    }

    val onSupportClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, SupportLinks.SUPPORT_LINK))
    }
    val onSponsorshipClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, SupportLinks.SPONSORSHIP_LINK))
    }
    val onTelemetryChanged = { enabled: Boolean ->
        telemetryEnabled = enabled
        PreferenceHelper.setTelemetryEnabled(context, enabled)
        if (application != null) {
            SentryLog.setEnabled(application, enabled)
        }
    }

    val heartTransition = rememberInfiniteTransition(label = "welcomeHeart")
    val heartScale by heartTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "welcomeHeartScale"
    )

    ExpressivePage {
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .size(136.dp)
                .scale(heartScale)
                .clip(ScallopedShape(numPetals = 12, depth = 8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeClickable(onClick = onSupportClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.baseline_favorite_24),
                contentDescription = stringResource(R.string.welcome_support_logo),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
        }

        Text(
            text = stringResource(R.string.welcome_support_header),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        )

        Text(
            text = stringResource(R.string.welcome_support_summary),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        ExpressiveActionCard(
            label = stringResource(R.string.welcome_support_button),
            icon = {
                Image(
                    painter = painterResource(R.drawable.paypal),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            },
            shape = ExpressiveAsymmetricShape1,
            onClick = onSupportClick
        )

        if (showSponsorship) {
            ExpressiveActionCard(
                label = stringResource(R.string.support_sponsorship_button),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_github_32dp),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp)
                    )
                },
                shape = ExpressiveAsymmetricShape2,
                onClick = onSponsorshipClick
            )
        } else {
            ExpressiveSection(
                modifier = Modifier.padding(top = 24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                shape = ExpressiveAsymmetricShape2
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Text(
                        text = stringResource(R.string.welcome_support_telemetry_summary),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .safeClickable { onTelemetryChanged(!telemetryEnabled) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Checkbox(
                            checked = telemetryEnabled,
                            onCheckedChange = onTelemetryChanged
                        )
                        Text(
                            text = stringResource(R.string.welcome_support_telemetry_button),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun WelcomeMethodCard(
    modifier: Modifier = Modifier,
    selected: Boolean,
    iconRes: Int,
    iconDescription: Int,
    titleRes: Int,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.extraLarge,
    entries: List<MethodEntry>,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "cardColor"
    )

    Card(
        modifier = modifier.safeClickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = shape
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            ExpressiveIconBadge(
                iconRes = iconRes,
                contentDescription = stringResource(iconDescription),
                iconTint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                },
                size = 48.dp,
                iconSize = 24.dp,
                shape = ScallopedShape(numPetals = 8, depth = 3.dp)
            )

            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            entries.forEach { entry ->
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        painter = painterResource(entry.iconRes),
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            entry.tint
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(entry.textRes),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

private fun getAlwaysOnVpnMessage(context: Context): Int? {
    var alwaysOnMessage = R.string.welcome_vpn_alwayson_description
    try {
        val alwaysOn = Settings.Secure.getString(context.contentResolver, "always_on_vpn_app")
        if (alwaysOn == null) {
            return null
        }
    } catch (_: SecurityException) {
        alwaysOnMessage = R.string.welcome_vpn_alwayson_blocked_description
    }
    return alwaysOnMessage
}
