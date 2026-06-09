package org.adaway.ui.source

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adaway.R
import org.adaway.db.AppDatabase
import org.adaway.db.entity.HostsSource
import org.adaway.db.entity.SourceType
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressiveAsymmetricShape2
import org.adaway.ui.compose.ExpressiveScaffold
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.compose.ExpressiveTopBar

@Composable
internal fun SourceEditRoute(
    sourceId: Int?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val hostsSourceDao = remember(context) {
        AppDatabase.getInstance(context.applicationContext).hostsSourceDao()
    }
    var edited by remember(sourceId) { mutableStateOf<HostsSource?>(null) }
    var screenState by remember(sourceId) {
        mutableStateOf(
            SourceEditScreenState(
                urlLocation = context.getString(R.string.source_edit_url_location_default)
            )
        )
    }
    val editing = sourceId != null
    val coroutineScope = rememberCoroutineScope()

    val startActivityLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val uri: Uri? = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some providers grant only a temporary read permission.
            }
            screenState = screenState.copy(
                fileLocation = uri.toString(),
                locationError = null
            )
        }
    }

    LaunchedEffect(sourceId, hostsSourceDao) {
        if (sourceId == null) {
            return@LaunchedEffect
        }
        val source = withContext(Dispatchers.IO) {
            hostsSourceDao.getById(sourceId).orElse(null)
        }
        if (source != null) {
            edited = source
            screenState = source.toScreenState(context)
        }
    }

    fun openDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = ANY_MIME_TYPE
            addFlags(FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityLauncher.launch(intent)
    }

    fun saveSource() {
        val validation = validateSource(screenState)
        screenState = validation.state
        val source = validation.source ?: return
        val sourceToReplace = edited
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                if (editing && sourceToReplace != null) {
                    hostsSourceDao.delete(sourceToReplace)
                }
                hostsSourceDao.insert(source)
            }
            onNavigateBack()
        }
    }

    fun deleteEditedSource() {
        val source = edited ?: return
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                hostsSourceDao.delete(source)
            }
            onNavigateBack()
        }
    }

    SourceEditScreen(
        state = screenState,
        editing = editing,
        onNavigateBack = onNavigateBack,
        onSave = ::saveSource,
        onDelete = ::deleteEditedSource,
        onLabelChanged = { value ->
            screenState = screenState.copy(label = value, labelError = null)
        },
        onFormatSelected = { allowFormat ->
            screenState = screenState.copy(
                allowFormat = allowFormat,
                redirectedHosts = if (allowFormat) false else screenState.redirectedHosts
            )
        },
        onTypeSelected = { type ->
            if (screenState.type != type) {
                screenState = screenState.copy(
                    type = type,
                    locationError = null,
                    urlLocation = if (type == SourceInputType.URL && screenState.urlLocation.isBlank()) {
                        context.getString(R.string.source_edit_url_location_default)
                    } else {
                        screenState.urlLocation
                    }
                )
                if (type == SourceInputType.FILE) {
                    openDocument()
                }
            }
        },
        onUrlChanged = { value ->
            screenState = screenState.copy(urlLocation = value, locationError = null)
        },
        onFileLocationClick = ::openDocument,
        onRedirectedChanged = { checked ->
            screenState = screenState.copy(redirectedHosts = checked)
        }
    )
}

private enum class SourceInputType {
    URL,
    FILE
}

private data class SourceEditScreenState(
    val label: String = "",
    @param:StringRes @field:StringRes val labelError: Int? = null,
    val allowFormat: Boolean = false,
    val type: SourceInputType = SourceInputType.URL,
    val urlLocation: String = "",
    val fileLocation: String = "",
    @param:StringRes @field:StringRes val locationError: Int? = null,
    val redirectedHosts: Boolean = false
)

private data class SourceValidation(
    val state: SourceEditScreenState,
    val source: HostsSource?
)

private fun HostsSource.toScreenState(context: Context): SourceEditScreenState {
    val inputType = when (type) {
        SourceType.FILE -> SourceInputType.FILE
        else -> SourceInputType.URL
    }
    return SourceEditScreenState(
        label = label,
        allowFormat = isAllowEnabled,
        type = inputType,
        urlLocation = if (inputType == SourceInputType.URL) {
            url
        } else {
            context.getString(R.string.source_edit_url_location_default)
        },
        fileLocation = if (inputType == SourceInputType.FILE) url else "",
        redirectedHosts = isRedirectEnabled
    )
}

private fun validateSource(screenState: SourceEditScreenState): SourceValidation {
    var state = screenState.copy(labelError = null, locationError = null)
    val label = state.label.trim()
    if (label.isEmpty()) {
        state = state.copy(labelError = R.string.source_edit_label_required)
    }

    val url = if (state.type == SourceInputType.URL) {
        val value = state.urlLocation.trim()
        if (value.isEmpty()) {
            state = state.copy(locationError = R.string.source_edit_url_location_required)
        } else if (!HostsSource.isValidUrl(value)) {
            state = state.copy(locationError = R.string.source_edit_location_invalid)
        }
        value
    } else {
        val value = state.fileLocation.trim()
        if (!HostsSource.isValidUrl(value)) {
            state = state.copy(locationError = R.string.source_edit_location_invalid)
        }
        value
    }

    if (state.labelError != null || state.locationError != null) {
        return SourceValidation(state, null)
    }

    val source = HostsSource().apply {
        this.label = label
        this.url = url
        setAllowEnabled(state.allowFormat)
        setRedirectEnabled(!state.allowFormat && state.redirectedHosts)
    }
    return SourceValidation(state, source)
}

@Composable
private fun SourceEditScreen(
    state: SourceEditScreenState,
    editing: Boolean,
    onNavigateBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onFormatSelected: (Boolean) -> Unit,
    onTypeSelected: (SourceInputType) -> Unit,
    onUrlChanged: (String) -> Unit,
    onFileLocationClick: () -> Unit,
    onRedirectedChanged: (Boolean) -> Unit
) {
    ExpressiveScaffold(
        topBar = {
            ExpressiveTopBar(
                title = stringResource(
                    if (editing) R.string.source_edit_title else R.string.source_edit_add_title
                ),
                onNavigateBack = onNavigateBack,
                actions = {
                    if (editing) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                painter = painterResource(R.drawable.outline_delete_24),
                                contentDescription = stringResource(R.string.checkbox_list_context_delete)
                            )
                        }
                    }
                    IconButton(onClick = onSave) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_check_24),
                            contentDescription = stringResource(R.string.checkbox_list_context_apply)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            ExpressiveSection(shape = ExpressiveAsymmetricShape1) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.source_edit_label),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.label,
                        onValueChange = onLabelChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = stringResource(R.string.source_edit_label)) },
                        singleLine = true,
                        isError = state.labelError != null,
                        supportingText = {
                            state.labelError?.let { error ->
                                Text(text = stringResource(error))
                            }
                        },
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ExpressiveSection(shape = ExpressiveAsymmetricShape2) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.source_edit_format),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SourceToggleButton(
                            text = stringResource(R.string.source_edit_format_block_list),
                            selected = !state.allowFormat,
                            modifier = Modifier.weight(1f),
                            onClick = { onFormatSelected(false) }
                        )
                        SourceToggleButton(
                            text = stringResource(R.string.source_edit_format_allow_list),
                            selected = state.allowFormat,
                            modifier = Modifier.weight(1f),
                            onClick = { onFormatSelected(true) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ExpressiveSection(shape = ExpressiveAsymmetricShape1) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.source_edit_type),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SourceToggleButton(
                            text = stringResource(R.string.source_edit_url),
                            selected = state.type == SourceInputType.URL,
                            modifier = Modifier.weight(1f),
                            onClick = { onTypeSelected(SourceInputType.URL) }
                        )
                        SourceToggleButton(
                            text = stringResource(R.string.source_edit_file),
                            selected = state.type == SourceInputType.FILE,
                            modifier = Modifier.weight(1f),
                            onClick = { onTypeSelected(SourceInputType.FILE) }
                        )
                    }

                    if (state.type == SourceInputType.URL) {
                        OutlinedTextField(
                            value = state.urlLocation,
                            onValueChange = onUrlChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            label = { Text(text = stringResource(R.string.source_edit_url_location)) },
                            singleLine = true,
                            isError = state.locationError != null,
                            supportingText = {
                                state.locationError?.let { error ->
                                    Text(text = stringResource(error))
                                }
                            },
                            shape = MaterialTheme.shapes.medium
                        )
                    } else {
                        val fileLocation = state.fileLocation.ifEmpty {
                            stringResource(R.string.source_edit_file_hint)
                        }
                        OutlinedButton(
                            onClick = onFileLocationClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = fileLocation,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        state.locationError?.let { error ->
                            Text(
                                text = stringResource(error),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp, start = 16.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = !state.allowFormat) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    ExpressiveSection(shape = ExpressiveAsymmetricShape2) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = state.redirectedHosts,
                                    onCheckedChange = onRedirectedChanged
                                )
                                Text(
                                    text = stringResource(R.string.source_edit_redirected_hosts),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            Text(
                                text = stringResource(R.string.source_edit_redirected_hosts_warning),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SourceToggleButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

private const val ANY_MIME_TYPE = "*/*"
