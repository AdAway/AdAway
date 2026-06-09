package org.adaway.ui.home

import org.adaway.ui.compose.safeClickable

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.helper.NotificationHelper
import org.adaway.helper.PreferenceHelper
import org.adaway.model.adblocking.AdBlockMethod
import org.adaway.ui.compose.ExpressiveAppContainer
import org.adaway.ui.compose.ExpressiveIconBadge
import org.adaway.ui.compose.ExpressiveFloatingBottomBar
import org.adaway.ui.compose.ExpressiveScaffold
import org.adaway.ui.compose.ExpressiveSection
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressiveAsymmetricShape2
import org.adaway.ui.compose.ScallopedShape
import org.adaway.ui.compose.WavyProgressIndicator
import org.adaway.ui.navigation.AdAwayRoute
import org.adaway.ui.navigation.AdAwayNavHost
import org.adaway.ui.navigation.NavigationRequest
import timber.log.Timber

/**
 * This class is the application main activity.
 */
class HomeActivity : ComponentActivity() {
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var prepareVpnLauncher: ActivityResultLauncher<Intent>
    private var requestedRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        NotificationHelper.clearUpdateNotifications(this)
        Timber.i("Starting main activity")

        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        val startDestination = getStartDestination()
        requestedRoute = if (startDestination == AdAwayRoute.WELCOME) {
            null
        } else {
            NavigationRequest.routeFrom(intent)
        }

        prepareVpnLauncher = registerForActivityResult(StartActivityForResult()) {}

        setContent {
            ExpressiveAppContainer {
                AdAwayNavHost(
                    homeViewModel = homeViewModel,
                    startDestination = startDestination,
                    onOpenProjectPage = ::showProjectPage,
                    onOpenUri = ::openUri,
                    onWelcomeComplete = ::checkFirstStep,
                    requestedRoute = requestedRoute,
                    onRouteConsumed = { requestedRoute = null }
                )
            }
        }

        if (savedInstanceState == null) {
            checkUpdateAtStartup()
        }
    }

    override fun onResume() {
        super.onResume()
        checkFirstStep()
    }

    private fun checkFirstStep() {
        val adBlockMethod = PreferenceHelper.getAdBlockMethod(this)
        if (adBlockMethod == AdBlockMethod.VPN) {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                prepareVpnLauncher.launch(prepareIntent)
            }
        }
    }

    private fun checkUpdateAtStartup() {
        if (PreferenceHelper.getUpdateCheckAppStartup(this)) {
            homeViewModel.checkForAppUpdate()
        }
        if (PreferenceHelper.getUpdateCheck(this)) {
            homeViewModel.update()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedRoute = if (PreferenceHelper.getAdBlockMethod(this) == AdBlockMethod.UNDEFINED) {
            null
        } else {
            NavigationRequest.routeFrom(intent)
        }
    }

    private fun showProjectPage() {
        openUri(Uri.parse(PROJECT_LINK))
    }

    private fun openUri(uri: Uri) {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun getStartDestination(): String {
        return if (PreferenceHelper.getAdBlockMethod(this) == AdBlockMethod.UNDEFINED) {
            AdAwayRoute.WELCOME
        } else {
            AdAwayRoute.HOME
        }
    }

    companion object {
        private const val PROJECT_LINK = "https://github.com/AdAway/AdAway"
    }
}

private data class HomeScreenState(
    val versionName: String = "",
    val updateAvailable: Boolean = false,
    val blockedHostCount: Int = 0,
    val allowedHostCount: Int = 0,
    val redirectHostCount: Int = 0,
    val upToDateSourceCount: Int = 0,
    val outdatedSourceCount: Int = 0,
    val pending: Boolean = false,
    val stateText: String = "",
    val adBlocked: Boolean = false,
    val drawerVisible: Boolean = false
)

@Composable
internal fun HomeRoute(
    viewModel: HomeViewModel,
    onOpenUpdate: () -> Unit,
    onOpenBlockedList: () -> Unit,
    onOpenAllowedList: () -> Unit,
    onOpenRedirectedList: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenProjectPage: () -> Unit
) {
    val versionName = remember(viewModel) { viewModel.versionName }
    val adBlocked by viewModel.adBlocked.collectAsStateWithLifecycle()
    val manifest by viewModel.appManifest.collectAsStateWithLifecycle()
    val blockedHostCount by viewModel.blockedHostCount.collectAsStateWithLifecycle()
    val allowedHostCount by viewModel.allowedHostCount.collectAsStateWithLifecycle()
    val redirectHostCount by viewModel.redirectHostCount.collectAsStateWithLifecycle()
    val upToDateSourceCount by viewModel.upToDateSourceCount.collectAsStateWithLifecycle()
    val outdatedSourceCount by viewModel.outdatedSourceCount.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val stateText by viewModel.state.collectAsStateWithLifecycle()
    var drawerVisible by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = drawerVisible) {
        drawerVisible = false
    }

    val state = remember(
        versionName,
        manifest,
        blockedHostCount,
        allowedHostCount,
        redirectHostCount,
        upToDateSourceCount,
        outdatedSourceCount,
        pending,
        stateText,
        adBlocked,
        drawerVisible
    ) {
        HomeScreenState(
            versionName = versionName,
            updateAvailable = manifest?.updateAvailable == true,
            blockedHostCount = blockedHostCount,
            allowedHostCount = allowedHostCount,
            redirectHostCount = redirectHostCount,
            upToDateSourceCount = upToDateSourceCount,
            outdatedSourceCount = outdatedSourceCount,
            pending = pending,
            stateText = stateText.orEmpty(),
            adBlocked = adBlocked,
            drawerVisible = drawerVisible
        )
    }

    HomeScreen(
        state = state,
        onToggleAdBlocking = viewModel::toggleAdBlocking,
        onOpenDrawer = { drawerVisible = true },
        onCloseDrawer = { drawerVisible = false },
        onOpenUpdate = onOpenUpdate,
        onOpenBlockedList = onOpenBlockedList,
        onOpenAllowedList = onOpenAllowedList,
        onOpenRedirectedList = onOpenRedirectedList,
        onOpenSources = onOpenSources,
        onCheckSources = viewModel::update,
        onSyncSources = viewModel::sync,
        onOpenLog = onOpenLog,
        onOpenHelp = onOpenHelp,
        onOpenSupport = onOpenSupport,
        onOpenPreferences = {
            drawerVisible = false
            onOpenPreferences()
        },
        onOpenProjectPage = {
            drawerVisible = false
            onOpenProjectPage()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: HomeScreenState,
    onToggleAdBlocking: () -> Unit,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenBlockedList: () -> Unit,
    onOpenAllowedList: () -> Unit,
    onOpenRedirectedList: () -> Unit,
    onOpenSources: () -> Unit,
    onCheckSources: () -> Unit,
    onSyncSources: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenProjectPage: () -> Unit
) {
    ExpressiveScaffold(
        bottomBar = {
            ExpressiveFloatingBottomBar {
                BottomAppBar(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    tonalElevation = 0.dp,
                    actions = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                painter = painterResource(R.drawable.ic_menu_24dp),
                                contentDescription = stringResource(R.string.open_drawer_button_description)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onCheckSources) {
                            Icon(
                                painter = painterResource(R.drawable.ic_sync_24dp),
                                contentDescription = stringResource(R.string.update_button)
                            )
                        }
                        IconButton(onClick = onOpenLog) {
                            Icon(
                                painter = painterResource(R.drawable.ic_playlist_add_24dp),
                                contentDescription = stringResource(R.string.show_log_button)
                            )
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = onToggleAdBlocking,
                            containerColor = if (state.adBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (state.adBlocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.large
                        ) {
                            if (state.adBlocked) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_pause_24dp),
                                    contentDescription = stringResource(R.string.adblock_pause_button_description)
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.logo),
                                    contentDescription = stringResource(R.string.app_logo),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeHeader(
                state = state,
                onOpenUpdate = onOpenUpdate
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeMetricCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.baseline_block_24,
                    iconTint = colorResource(R.color.blocked),
                    count = state.blockedHostCount,
                    labelRes = R.string.blocked_hosts_label,
                    shape = ExpressiveAsymmetricShape1,
                    onClick = onOpenBlockedList
                )
                HomeMetricCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.baseline_check_24,
                    iconTint = colorResource(R.color.allowed),
                    count = state.allowedHostCount,
                    labelRes = R.string.allowed_hosts_label,
                    shape = ExpressiveAsymmetricShape2,
                    onClick = onOpenAllowedList
                )
                HomeMetricCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.baseline_compare_arrows_24,
                    iconTint = colorResource(R.color.redirected),
                    count = state.redirectHostCount,
                    labelRes = R.string.redirect_hosts_label,
                    shape = ExpressiveAsymmetricShape1,
                    onClick = onOpenRedirectedList
                )
            }

            SourceStatusSection(
                state = state,
                onOpenSources = onOpenSources,
                onCheckSources = onCheckSources,
                onSyncSources = onSyncSources
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeQuickActionCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.ic_outline_rule_24,
                    labelRes = R.string.log_label,
                    shape = ExpressiveAsymmetricShape2,
                    onClick = onOpenLog
                )
                HomeQuickActionCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.ic_help_24dp,
                    labelRes = R.string.help_label,
                    shape = ExpressiveAsymmetricShape1,
                    onClick = onOpenHelp
                )
                HomeQuickActionCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconRes = R.drawable.baseline_favorite_24,
                    labelRes = R.string.support_label,
                    shape = ExpressiveAsymmetricShape2,
                    onClick = onOpenSupport
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (state.drawerVisible) {
            ModalBottomSheet(
                onDismissRequest = onCloseDrawer,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(bottom = 48.dp, top = 8.dp)) {
                    HomeDrawerItem(
                        label = stringResource(R.string.hosts_title),
                        iconRes = R.drawable.ic_collections_bookmark_24dp,
                        onClick = { 
                            onCloseDrawer()
                            onOpenSources() 
                        }
                    )
                    HomeDrawerItem(
                        label = stringResource(R.string.shortcut_your_lists),
                        iconRes = R.drawable.ic_list_red,
                        onClick = { 
                            onCloseDrawer()
                            onOpenBlockedList() 
                        }
                    )
                    HomeDrawerItem(
                        label = stringResource(R.string.log_label),
                        iconRes = R.drawable.ic_outline_rule_24,
                        onClick = { 
                            onCloseDrawer()
                            onOpenLog() 
                        }
                    )
                    
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp)
                    )
                    
                    HomeDrawerItem(
                        label = stringResource(R.string.preferences_drawer_item),
                        iconRes = R.drawable.ic_settings_24dp,
                        onClick = onOpenPreferences
                    )
                    HomeDrawerItem(
                        label = stringResource(R.string.github_project_drawer_item),
                        iconRes = R.drawable.ic_github_24dp,
                        onClick = onOpenProjectPage
                    )
                    HomeDrawerItem(
                        label = stringResource(R.string.help_label),
                        iconRes = R.drawable.ic_help_24dp,
                        onClick = {
                            onCloseDrawer()
                            onOpenHelp()
                        }
                    )
                    HomeDrawerItem(
                        label = stringResource(R.string.support_label),
                        iconRes = R.drawable.baseline_favorite_24,
                        onClick = {
                            onCloseDrawer()
                            onOpenSupport()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDrawerItem(
    label: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .safeClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveIconBadge(
            iconRes = iconRes,
            iconTint = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            size = 40.dp,
            iconSize = 20.dp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HomeHeader(
    state: HomeScreenState,
    onOpenUpdate: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = if (state.adBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(500),
        label = "statusColor"
    )
    val onStatusColor by animateColorAsState(
        targetValue = if (state.adBlocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(500),
        label = "onStatusColor"
    )

    ExpressiveSection(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(statusColor)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.adBlocked) stringResource(R.string.status_enabled) else stringResource(R.string.status_disabled),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = onStatusColor
                        )
                        Text(
                            text = if (state.adBlocked) stringResource(R.string.status_enabled_subtitle) else stringResource(R.string.status_disabled_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onStatusColor.copy(alpha = 0.85f)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(onStatusColor.copy(alpha = 0.15f))
                            .safeClickable(onClick = onOpenUpdate)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (state.updateAvailable) stringResource(R.string.update_available) else state.versionName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = onStatusColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
                
                Spacer(modifier = Modifier.width(20.dp))
                
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = stringResource(R.string.app_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeMetricCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    iconTint: Color,
    count: Int,
    @StringRes labelRes: Int,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    onClick: () -> Unit
) {
    ExpressiveSection(
        modifier = modifier.safeClickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ExpressiveIconBadge(
                iconRes = iconRes,
                iconTint = iconTint,
                containerColor = iconTint.copy(alpha = 0.12f),
                size = 44.dp,
                iconSize = 24.dp
            )
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                            fadeOut(animationSpec = tween(90))
                },
                label = "countTransition"
            ) { targetCount ->
                Text(
                    text = targetCount.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SourceStatusSection(
    state: HomeScreenState,
    onOpenSources: () -> Unit,
    onCheckSources: () -> Unit,
    onSyncSources: () -> Unit
) {
    ExpressiveSection(
        modifier = Modifier
            .padding(top = 16.dp)
            .safeClickable(onClick = onOpenSources),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = ExpressiveAsymmetricShape2
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(ScallopedShape(numPetals = 8, depth = 4.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_collections_bookmark_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(20.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.up_to_date_source_label,
                            state.upToDateSourceCount,
                            state.upToDateSourceCount
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.outdated_source_label,
                            state.outdatedSourceCount,
                            state.outdatedSourceCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.outdatedSourceCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCheckSources,
                        modifier = Modifier.then(if (state.pending) Modifier.size(0.dp) else Modifier)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sync_24dp),
                            contentDescription = stringResource(R.string.check_hosts_update_description),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onSyncSources,
                        modifier = Modifier.then(if (state.pending) Modifier.size(0.dp) else Modifier)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_get_app_24dp),
                            contentDescription = stringResource(R.string.update_hosts_description),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            AnimatedContent(
                targetState = state.pending,
                label = "progressTransition"
            ) { isPending ->
                if (isPending) {
                    Column(modifier = Modifier.padding(top = 20.dp)) {
                        WavyProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        if (state.stateText.isNotEmpty()) {
                            Text(
                                text = state.stateText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeQuickActionCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    @StringRes labelRes: Int,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    onClick: () -> Unit
) {
    ExpressiveSection(
        modifier = modifier.safeClickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ExpressiveIconBadge(
                iconRes = iconRes,
                iconTint = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                size = 48.dp,
                iconSize = 26.dp
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}



