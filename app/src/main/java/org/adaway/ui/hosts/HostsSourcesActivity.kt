package org.adaway.ui.hosts

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.drop
import org.adaway.R
import org.adaway.db.entity.HostsSource
import org.adaway.ui.adblocking.ApplyConfigurationSnackbar
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressiveAsymmetricShape2
import org.adaway.ui.compose.ExpressiveScaffold
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.compose.ExpressiveStatusDot
import org.adaway.ui.compose.ExpressiveTopBar
import org.adaway.ui.compose.safeClickable
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
internal fun HostsSourcesRoute(
    onNavigateBack: () -> Unit,
    onEditSource: (Int?) -> Unit,
    viewModel: HostsSourcesViewModel = viewModel()
) {
    val rootView = LocalView.current
    val sources by viewModel.hostsSources.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, rootView) {
        val applySnackbar = ApplyConfigurationSnackbar(rootView, true, true)
        viewModel.hostsSources.drop(1).collect { sourceList ->
            if (sourceList.isNotEmpty()) {
                applySnackbar.notifyUpdateAvailable()
            }
        }
    }

    HostsSourcesScreen(
        sources = sources,
        onNavigateBack = onNavigateBack,
        onAddSource = { onEditSource(null) },
        onToggleSource = viewModel::toggleSourceEnabled,
        onEditSource = { source -> onEditSource(source.id) }
    )
}

@Composable
private fun SourcesSummaryHeaderCard(sources: List<HostsSource>) {
    val activeCount = remember(sources) { sources.count { it.isEnabled } }
    val totalCount = sources.size
    val totalHosts = remember(sources) { sources.sumOf { if (it.isEnabled && it.size > 0) it.size else 0 } }
    val formattedHosts = remember(totalHosts) {
        if (totalHosts >= 1_000_000) {
            String.format("%.1fM", totalHosts / 1_000_000f)
        } else if (totalHosts >= 1_000) {
            String.format("%.1fk", totalHosts / 1_000f)
        } else {
            totalHosts.toString()
        }
    }

    ExpressiveSection(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.hosts_sources_summary_active, activeCount, totalCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.hosts_sources_summary_blocked_hosts, formattedHosts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun HostsSourcesScreen(
    sources: List<HostsSource>,
    onNavigateBack: () -> Unit,
    onAddSource: () -> Unit,
    onToggleSource: (HostsSource) -> Unit,
    onEditSource: (HostsSource) -> Unit
) {
    ExpressiveScaffold(
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.hosts_title),
                onNavigateBack = onNavigateBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddSource,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_black_24px),
                    contentDescription = stringResource(R.string.hosts_add_dialog_title),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sources.isNotEmpty()) {
                item {
                    SourcesSummaryHeaderCard(sources = sources)
                }
            }
            itemsIndexed(
                items = sources,
                key = { _, source -> source.url }
            ) { index, source ->
                HostsSourceCard(
                    source = source,
                    shape = if (index % 2 == 0) ExpressiveAsymmetricShape1 else ExpressiveAsymmetricShape2,
                    onToggle = { onToggleSource(source) },
                    onEdit = { onEditSource(source) }
                )
            }
            if (sources.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.hosts_source_unknown_status),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HostsSourceCard(
    source: HostsSource,
    shape: Shape,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val updateText = getSourceUpdateText(source)
    val hostCountText = getSourceHostCount(source)

    val statusColor = when {
        !source.isEnabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        source.onlineModificationDate != null -> {
            val onlineDate = source.onlineModificationDate!!
            val localDate = source.localModificationDate
            if (localDate != null && onlineDate.isAfter(localDate)) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.tertiary
            }
        }
        source.localModificationDate != null -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }

    ExpressiveSection(
        modifier = Modifier.safeClickable(onClick = onEdit),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = source.isEnabled,
                onCheckedChange = { onToggle() }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = source.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveStatusDot(color = statusColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = updateText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hostCountText.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = hostCountText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getSourceUpdateText(source: HostsSource): String {
    if (!source.isEnabled) {
        return stringResource(R.string.hosts_source_disabled)
    }

    val lastOnlineModificationDefined = source.onlineModificationDate != null
    val lastLocalModificationDefined = source.localModificationDate != null

    if (lastOnlineModificationDefined) {
        val onlineDate = source.onlineModificationDate!!
        val approximateDelay = getApproximateDelay(onlineDate)
        if (!lastLocalModificationDefined) {
            return stringResource(R.string.hosts_source_last_update, approximateDelay)
        }
        val localDate = source.localModificationDate!!
        return if (onlineDate.isAfter(localDate)) {
            stringResource(R.string.hosts_source_need_update, approximateDelay)
        } else {
            stringResource(R.string.hosts_source_up_to_date, approximateDelay)
        }
    }

    if (lastLocalModificationDefined) {
        val approximateDelay = getApproximateDelay(source.localModificationDate!!)
        return stringResource(R.string.hosts_source_installed, approximateDelay)
    }

    return stringResource(R.string.hosts_source_unknown_status)
}

@Composable
private fun getApproximateDelay(from: ZonedDateTime): String {
    var delay = Duration.between(from, ZonedDateTime.now()).toMinutes()
    if (delay < 60) {
        return stringResource(R.string.hosts_source_few_minutes)
    }
    delay /= 60
    if (delay < 24) {
        val hours = delay.toInt()
        return pluralStringResource(R.plurals.hosts_source_hours, hours, hours)
    }
    delay /= 24
    if (delay < 30) {
        val days = delay.toInt()
        return pluralStringResource(R.plurals.hosts_source_days, days, days)
    }
    val months = (delay / 30).toInt()
    return pluralStringResource(R.plurals.hosts_source_months, months, months)
}

@Composable
private fun getSourceHostCount(source: HostsSource): String {
    if (source.size <= 0 || !source.isEnabled) {
        return ""
    }

    val prefixes = arrayOf("k", "M", "G")
    var value = source.size
    var length = 1
    while (value > 10) {
        value /= 10
        length++
    }
    var prefixIndex = (length - 1) / 3 - 1
    if (prefixIndex < 0) {
        return stringResource(R.string.hosts_count, source.size.toString())
    }
    if (prefixIndex >= prefixes.size) {
        prefixIndex = prefixes.lastIndex
        value = 13
    } else {
        val divisor = 10.0.pow((prefixIndex + 1) * 3.0)
        value = (source.size / divisor).roundToInt()
    }
    return stringResource(R.string.hosts_count, "$value${prefixes[prefixIndex]}")
}
