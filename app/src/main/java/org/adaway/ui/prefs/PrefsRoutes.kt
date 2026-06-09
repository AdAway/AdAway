package org.adaway.ui.prefs

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.ActivityNotFoundException
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Build
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import android.provider.Settings.ACTION_SECURITY_SETTINGS
import android.provider.Settings.EXTRA_APP_PACKAGE
import android.view.ContextThemeWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.net.InetAddresses
import org.adaway.R
import org.adaway.model.adblocking.AdBlockMethod
import org.adaway.model.backup.BackupExporter
import org.adaway.model.backup.BackupImporter
import org.adaway.util.Constants.ANDROID_SYSTEM_ETC_HOSTS
import org.adaway.util.Constants.PREFS_NAME
import org.adaway.util.WebServerUtils.TEST_URL
import org.adaway.util.WebServerUtils.copyCertificate
import org.adaway.util.WebServerUtils.installCertificate
import org.adaway.vpn.VpnServiceControls
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import timber.log.Timber

internal enum class PrefsDestination(@param:StringRes @field:StringRes val titleRes: Int) {
    MAIN(R.string.pref_main_title),
    UPDATE(R.string.pref_update_title),
    ROOT(R.string.pref_root_title),
    VPN(R.string.pref_vpn_title),
    BACKUP_RESTORE(R.string.pref_backup_restore_title)
}

private data class RedirectionDialogState(
    val titleRes: Int,
    val addressType: Class<out InetAddress>,
    val initialValue: String,
    val onSaved: (String) -> Unit
)

private data class MissingAppRequest(
    @param:StringRes @field:StringRes val titleRes: Int,
    @param:StringRes @field:StringRes val messageRes: Int,
    val googlePlayUri: String,
    val fdroidQuery: String
)

@Composable
internal fun PrefsContent(
    destination: PrefsDestination,
    viewModel: PrefsViewModel,
    onNavigate: (PrefsDestination) -> Unit,
    onOpenVpnExcludedApps: () -> Unit
) {
    ObserveOnResume {
        viewModel.reloadState()
    }

    when (destination) {
        PrefsDestination.MAIN -> PrefsMainRoute(viewModel = viewModel, onNavigate = onNavigate)
        PrefsDestination.UPDATE -> PrefsUpdateRoute(viewModel = viewModel)
        PrefsDestination.ROOT -> PrefsRootRoute(viewModel = viewModel)
        PrefsDestination.VPN -> PrefsVpnRoute(
            viewModel = viewModel,
            onOpenVpnExcludedApps = onOpenVpnExcludedApps
        )
        PrefsDestination.BACKUP_RESTORE -> PrefsBackupRestoreRoute(viewModel = viewModel)
    }
}

@Composable
private fun PrefsMainRoute(
    viewModel: PrefsViewModel,
    onNavigate: (PrefsDestination) -> Unit
) {
    PrefsMainScreen(
        darkThemeMode = viewModel.darkThemeMode,
        dynamicColorEnabled = viewModel.dynamicColorEnabled,
        dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        enableIpv6 = viewModel.enableIpv6,
        enableTelemetry = viewModel.enableTelemetry,
        enableDebug = viewModel.enableDebug,
        telemetrySupported = viewModel.telemetrySupported,
        rootConfigEnabled = viewModel.adBlockMethod == AdBlockMethod.ROOT,
        vpnConfigEnabled = viewModel.adBlockMethod == AdBlockMethod.VPN,
        onThemeSelected = viewModel::updateDarkThemeMode,
        onDynamicColorEnabledChanged = viewModel::updateDynamicColorEnabled,
        onOpenUpdate = { onNavigate(PrefsDestination.UPDATE) },
        onOpenRootConfig = { onNavigate(PrefsDestination.ROOT) },
        onOpenVpnConfig = { onNavigate(PrefsDestination.VPN) },
        onEnableIpv6Changed = viewModel::updateEnableIpv6,
        onOpenBackupRestore = { onNavigate(PrefsDestination.BACKUP_RESTORE) },
        onEnableTelemetryChanged = viewModel::updateEnableTelemetry,
        onEnableDebugChanged = viewModel::updateEnableDebug
    )
}

@Composable
private fun PrefsUpdateRoute(viewModel: PrefsViewModel) {
    val context = LocalContext.current
    PrefsUpdateScreen(
        notificationsDisabled = viewModel.notificationsDisabled,
        checkAppStartup = viewModel.checkAppStartup,
        checkAppDaily = viewModel.checkAppDaily,
        includeBetaReleases = viewModel.includeBetaReleases,
        includeBetaEnabled = viewModel.includeBetaEnabled,
        checkHostsStartup = viewModel.checkHostsStartup,
        checkHostsDaily = viewModel.checkHostsDaily,
        automaticUpdateDaily = viewModel.automaticUpdateDaily,
        updateOnlyOnWifi = viewModel.updateOnlyOnWifi,
        onOpenNotifications = {
            val settingsIntent = Intent(ACTION_APP_NOTIFICATION_SETTINGS)
                .addFlags(FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_APP_PACKAGE, context.packageName)
            context.startActivity(settingsIntent)
        },
        onCheckAppStartupChanged = viewModel::updateCheckAppStartup,
        onCheckAppDailyChanged = viewModel::updateCheckAppDaily,
        onIncludeBetaChanged = viewModel::updateIncludeBetaReleases,
        onCheckHostsStartupChanged = viewModel::updateCheckHostsStartup,
        onCheckHostsDailyChanged = viewModel::updateCheckHostsDaily,
        onAutomaticUpdateDailyChanged = viewModel::updateAutomaticUpdateDaily,
        onUpdateOnlyWifiChanged = viewModel::updateUpdateOnlyOnWifi
    )
}

@Composable
private fun PrefsRootRoute(viewModel: PrefsViewModel) {
    val context = LocalContext.current
    var redirectionDialog by remember { mutableStateOf<RedirectionDialogState?>(null) }
    var showCertificateDialog by rememberSaveable { mutableStateOf(false) }
    var missingAppRequest by remember { mutableStateOf<MissingAppRequest?>(null) }
    val openHostsFileLauncher = rememberLauncherForActivityResult(StartActivityForResult()) {
        try {
            val hostFile = File(ANDROID_SYSTEM_ETC_HOSTS).canonicalFile
            org.adaway.model.root.ShellUtils.remountPartition(hostFile, org.adaway.model.root.MountType.READ_ONLY)
        } catch (exception: IOException) {
            Timber.e(exception, "Failed to get hosts canonical file.")
        }
    }
    val certificateLauncher = rememberLauncherForActivityResult(
        CreateDocument(CERTIFICATE_MIME_TYPE)
    ) { uri ->
        if (copyWebServerCertificate(context, uri)) {
            showCertificateDialog = true
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.updateWebServerState()
    }
    ObserveOnResume {
        viewModel.updateWebServerState()
    }

    PrefsRootScreen(
        neverReboot = viewModel.neverReboot,
        redirectionIpv4 = viewModel.redirectionIpv4,
        redirectionIpv6 = viewModel.redirectionIpv6,
        ipv6Enabled = viewModel.ipv6Enabled,
        webServerEnabled = viewModel.webServerEnabled,
        webServerIcon = viewModel.webServerIcon,
        webServerStateSummaryRes = viewModel.webServerStateSummaryRes,
        onOpenHostsFile = {
            try {
                val hostFile = File(ANDROID_SYSTEM_ETC_HOSTS).canonicalFile
                val remount = !org.adaway.model.root.ShellUtils.isWritable(hostFile) && org.adaway.model.root.ShellUtils.remountPartition(hostFile, org.adaway.model.root.MountType.READ_WRITE)
                val intent = Intent()
                    .setAction(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse("file://${hostFile.absolutePath}"), "text/plain")
                if (remount) {
                    openHostsFileLauncher.launch(intent)
                } else {
                    context.startActivity(intent)
                }
            } catch (exception: IOException) {
                Timber.e(exception, "Failed to get hosts canonical file.")
            } catch (_: ActivityNotFoundException) {
                missingAppRequest = textEditorMissingRequest()
            }
        },
        onNeverRebootChanged = viewModel::updateNeverReboot,
        onEditIpv4 = { value ->
            redirectionDialog = RedirectionDialogState(
                titleRes = R.string.pref_redirection_ipv4,
                addressType = Inet4Address::class.java,
                initialValue = value,
                onSaved = viewModel::saveRedirectionIpv4
            )
        },
        onEditIpv6 = { value ->
            redirectionDialog = RedirectionDialogState(
                titleRes = R.string.pref_redirection_ipv6,
                addressType = Inet6Address::class.java,
                initialValue = value,
                onSaved = viewModel::saveRedirectionIpv6
            )
        },
        onWebServerEnabledChanged = viewModel::updateWebServerEnabled,
        onWebServerTest = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL)))
        },
        onInstallCertificate = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                installCertificate(context)
            } else {
                certificateLauncher.launch("adaway-webserver-certificate.crt")
            }
        },
        onWebServerIconChanged = viewModel::updateWebServerIcon
    )

    redirectionDialog?.let { state ->
        RedirectionDialog(
            state = state,
            onDismiss = { redirectionDialog = null },
            onSaved = { redirection ->
                state.onSaved(redirection)
                redirectionDialog = null
            }
        )
    }

    if (showCertificateDialog) {
        WebServerCertificateDialog(
            onDismiss = { showCertificateDialog = false },
            onOpenSettings = {
                showCertificateDialog = false
                context.startActivity(Intent(ACTION_SECURITY_SETTINGS))
            }
        )
    }

    missingAppRequest?.let { request ->
        MissingAppDialog(
            request = request,
            onDismiss = { missingAppRequest = null },
            onInstall = {
                missingAppRequest = null
                openMissingAppStore(context, request)
            }
        )
    }
}

@Composable
private fun PrefsVpnRoute(
    viewModel: PrefsViewModel,
    onOpenVpnExcludedApps: () -> Unit
) {
    val context = LocalContext.current
    var restartVpnOnResume by rememberSaveable { mutableStateOf(false) }

    ObserveOnResume {
        if (restartVpnOnResume && VpnServiceControls.isRunning(context)) {
            VpnServiceControls.stop(context)
            VpnServiceControls.start(context)
        }
        restartVpnOnResume = false
    }

    PrefsVpnScreen(
        serviceOnBoot = viewModel.serviceOnBoot,
        watchdogEnabled = viewModel.watchdogEnabled,
        excludedSystemApps = viewModel.excludedSystemApps,
        onServiceOnBootChanged = viewModel::updateServiceOnBoot,
        onWatchdogChanged = viewModel::updateWatchdogEnabled,
        onExcludedSystemAppsChanged = viewModel::updateExcludedSystemApps,
        onOpenExcludedUserApps = {
            restartVpnOnResume = true
            onOpenVpnExcludedApps()
        }
    )
}

@Composable
private fun PrefsBackupRestoreRoute(viewModel: PrefsViewModel) {
    val context = LocalContext.current
    val openDocumentContract = remember {
        object : OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                return super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
            }
        }
    }
    val importActivityLauncher = rememberLauncherForActivityResult(openDocumentContract) { backupUri ->
        if (backupUri != null) {
            BackupImporter.importFromBackup(context, backupUri)
            viewModel.reloadState()
        }
    }
    val exportActivityLauncher = rememberLauncherForActivityResult(
        CreateDocument(JSON_MIME_TYPE)
    ) { backupUri ->
        if (backupUri != null) {
            BackupExporter.exportToBackup(context, backupUri)
        }
    }

    PrefsBackupRestoreScreen(
        onBackupClick = { exportActivityLauncher.launch(BACKUP_FILE_NAME) },
        onRestoreClick = {
            val mimeTypes = when {
                Build.VERSION.SDK_INT < 28 -> arrayOf("*/*")
                Build.VERSION.SDK_INT < 29 -> arrayOf(JSON_MIME_TYPE, "application/octet-stream")
                else -> arrayOf(JSON_MIME_TYPE)
            }
            importActivityLauncher.launch(mimeTypes)
        }
    )
}

@Composable
private fun ObserveOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume by rememberUpdatedState(onResume)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun RedirectionDialog(
    state: RedirectionDialogState,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    var redirection by remember(state) { mutableStateOf(state.initialValue) }
    val valid = isValidRedirection(state.addressType, redirection.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(state.titleRes)) },
        text = {
            OutlinedTextField(
                value = redirection,
                onValueChange = { redirection = it },
                singleLine = true,
                isError = redirection.isNotBlank() && !valid,
                label = { Text(stringResource(state.titleRes)) }
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSaved(redirection.trim()) }
            ) {
                Text(stringResource(R.string.button_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

private fun isValidRedirection(addressType: Class<out InetAddress>, redirection: String): Boolean {
    return try {
        val inetAddress = InetAddresses.forString(redirection)
        addressType.isAssignableFrom(inetAddress.javaClass)
    } catch (_: IllegalArgumentException) {
        false
    }
}

private fun copyWebServerCertificate(context: Context, uri: Uri?): Boolean {
    if (uri == null) {
        return false
    }
    val wrapper = context as? ContextThemeWrapper ?: return false
    Timber.d("Certificate URI: %s", uri)
    copyCertificate(wrapper, uri)
    return true
}

@Composable
private fun WebServerCertificateDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pref_webserver_certificate_dialog_title)) },
        text = { Text(stringResource(R.string.pref_webserver_certificate_dialog_content)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.pref_webserver_certificate_dialog_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_close))
            }
        }
    )
}

@Composable
private fun MissingAppDialog(
    request: MissingAppRequest,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(request.titleRes)) },
        text = { Text(stringResource(request.messageRes)) },
        confirmButton = {
            TextButton(onClick = onInstall) {
                Text(stringResource(R.string.button_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_no))
            }
        }
    )
}

private fun textEditorMissingRequest(): MissingAppRequest {
    return MissingAppRequest(
        titleRes = R.string.no_text_editor_title,
        messageRes = R.string.no_text_editor,
        googlePlayUri = "market://details?id=jp.sblo.pandora.jota",
        fdroidQuery = "Text Edit"
    )
}

private fun openMissingAppStore(context: Context, request: MissingAppRequest) {
    val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse(request.googlePlayUri))
    try {
        context.startActivity(playIntent)
        return
    } catch (exception: ActivityNotFoundException) {
        Timber.e(exception, "No Google Play Store installed. Trying F-Droid.")
    }

    val fdroidIntent = Intent(Intent.ACTION_SEARCH)
        .setComponent(ComponentName("org.fdroid.fdroid", "org.fdroid.fdroid.SearchResults"))
        .putExtra(SearchManager.QUERY, request.fdroidQuery)
    try {
        context.startActivity(fdroidIntent)
    } catch (exception: ActivityNotFoundException) {
        Timber.e(exception, "No F-Droid installed.")
    }
}

private const val CERTIFICATE_MIME_TYPE = "application/x-x509-ca-cert"
private const val JSON_MIME_TYPE = "application/json"
private const val BACKUP_FILE_NAME = "adaway-backup.json"
