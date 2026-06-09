package org.adaway.ui.log

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import org.adaway.ui.compose.WavyProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.adaway.R
import org.adaway.db.entity.ListType
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar
import org.adaway.ui.compose.ExpressiveScaffold
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.compose.ExpressiveTopBar
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressiveAsymmetricShape2
import org.adaway.ui.compose.safeCombinedClickable
import org.adaway.util.Clipboard
import org.adaway.util.RegexUtils

private fun onLogEntryAction(
    context: android.content.Context,
    viewModel: LogViewModel,
    applySnackbar: ApplyConfigurationSnackbar,
    entry: LogEntry,
    targetType: ListType,
    onRequestRedirection: (String) -> Unit
) {
    if (entry.type == targetType) {
        viewModel.removeListItem(entry.host)
        applySnackbar.notifyUpdateAvailable()
        return
    }
    if (targetType == ListType.REDIRECTED) {
        onRequestRedirection(entry.host)
        return
    }
    viewModel.addListItem(entry.host, targetType, null)
    applySnackbar.notifyUpdateAvailable()
}

private fun openHostInBrowser(context: android.content.Context, hostName: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("http://$hostName")
    }
    context.startActivity(intent)
}

@Composable
internal fun LogRoute(
    onNavigateBack: () -> Unit,
    viewModel: LogViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val rootView = LocalView.current
    val applySnackbar = remember(rootView) {
        ApplyConfigurationSnackbar(rootView, false, false)
    }
    var redirectHost by remember { mutableStateOf<String?>(null) }
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val blockedRequestsIgnored = remember(viewModel) { viewModel.areBlockedRequestsIgnored() }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateLogs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LogScreen(
        logs = logs,
        recording = recording,
        refreshing = refreshing,
        blockedRequestsIgnored = blockedRequestsIgnored,
        onNavigateBack = onNavigateBack,
        onSort = viewModel::toggleSort,
        onClear = viewModel::clearLogs,
        onRefresh = viewModel::updateLogs,
        onToggleRecording = viewModel::toggleRecording,
        onEntryAction = { entry, targetType ->
            onLogEntryAction(
                context = context,
                viewModel = viewModel,
                applySnackbar = applySnackbar,
                entry = entry,
                targetType = targetType,
                onRequestRedirection = { host -> redirectHost = host }
            )
        },
        onOpenHost = { hostName -> openHostInBrowser(context, hostName) },
        onCopyHost = { hostName -> Clipboard.copyHostToClipboard(context, hostName) }
    )

    redirectHost?.let { hostName ->
        RedirectIpDialog(
            onDismiss = { redirectHost = null },
            onConfirm = { ip ->
                viewModel.addListItem(hostName, ListType.REDIRECTED, ip)
                applySnackbar.notifyUpdateAvailable()
                redirectHost = null
            }
        )
    }
}

@Composable
private fun LogScreen(
    logs: List<LogEntry>,
    recording: Boolean,
    refreshing: Boolean,
    blockedRequestsIgnored: Boolean,
    onNavigateBack: () -> Unit,
    onSort: () -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onToggleRecording: () -> Unit,
    onEntryAction: (LogEntry, ListType) -> Unit,
    onOpenHost: (String) -> Unit,
    onCopyHost: (String) -> Unit
) {
    ExpressiveScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.shortcut_dns_requests),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = onSort) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_sort_by_alpha_24),
                            contentDescription = stringResource(R.string.tcpdump_menu_sort)
                        )
                    }
                    IconButton(onClick = onClear) {
                        Icon(
                            painter = painterResource(R.drawable.outline_delete_24),
                            contentDescription = stringResource(R.string.tcpdump_menu_clear)
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sync_24dp),
                            contentDescription = stringResource(R.string.menu_refresh)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onToggleRecording,
                containerColor = if (recording) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (recording) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    painter = painterResource(
                        if (recording) R.drawable.ic_pause_24dp else R.drawable.ic_record_24dp
                    ),
                    contentDescription = stringResource(R.string.log_toggle_recording_description),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (refreshing) {
                WavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            if (logs.isEmpty()) {
                val message = buildString {
                    append(stringResource(R.string.log_start_recording))
                    if (blockedRequestsIgnored) {
                        append(stringResource(R.string.log_blocked_requests_ignored))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    ExpressiveSection {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = logs,
                        key = { _, entry -> entry.host }
                    ) { index, entry ->
                        LogEntryRow(
                            entry = entry,
                            shape = if (index % 2 == 0) ExpressiveAsymmetricShape1 else ExpressiveAsymmetricShape2,
                            onAction = onEntryAction,
                            onOpenHost = onOpenHost,
                            onCopyHost = onCopyHost
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(
    entry: LogEntry,
    shape: Shape,
    onAction: (LogEntry, ListType) -> Unit,
    onOpenHost: (String) -> Unit,
    onCopyHost: (String) -> Unit
) {
    ExpressiveSection(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogActionButton(
                iconRes = R.drawable.baseline_block_24,
                active = entry.type == ListType.BLOCKED,
                activeColor = MaterialTheme.colorScheme.error,
                onClick = { onAction(entry, ListType.BLOCKED) }
            )
            LogActionButton(
                iconRes = R.drawable.baseline_check_24,
                active = entry.type == ListType.ALLOWED,
                activeColor = MaterialTheme.colorScheme.tertiary,
                onClick = { onAction(entry, ListType.ALLOWED) }
            )
            LogActionButton(
                iconRes = R.drawable.baseline_compare_arrows_24,
                active = entry.type == ListType.REDIRECTED,
                activeColor = MaterialTheme.colorScheme.secondary,
                onClick = { onAction(entry, ListType.REDIRECTED) }
            )
            Text(
                text = entry.host,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 6.dp)
                    .safeCombinedClickable(
                        onClick = { onOpenHost(entry.host) },
                        onLongClick = { onCopyHost(entry.host) }
                    )
            )
        }
    }
}

@Composable
private fun LogActionButton(
    iconRes: Int,
    active: Boolean,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (active) {
                activeColor
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun RedirectIpDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var redirection by remember { mutableStateOf("0.0.0.0") }
    val valid = RegexUtils.isValidIP(redirection)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_redirect_dialog_title)) },
        text = {
            OutlinedTextField(
                value = redirection,
                onValueChange = { redirection = it },
                singleLine = true,
                isError = redirection.isNotBlank() && !valid,
                label = { Text(stringResource(R.string.list_dialog_ip)) }
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(redirection.trim()) }
            ) {
                Text(stringResource(R.string.button_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}
