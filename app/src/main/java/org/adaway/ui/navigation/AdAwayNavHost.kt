package org.adaway.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.adaway.R
import org.adaway.model.error.HostError
import org.adaway.ui.help.HelpRoute
import org.adaway.ui.home.HomeRoute
import org.adaway.ui.home.HomeViewModel
import org.adaway.ui.hosts.HostsSourcesRoute
import org.adaway.ui.lists.ListsRoute
import org.adaway.ui.log.LogRoute
import org.adaway.ui.prefs.PrefsDestination
import org.adaway.ui.prefs.PrefsRoute
import org.adaway.ui.prefs.PrefsViewModel
import org.adaway.ui.prefs.exclusion.VpnExcludedAppsRoute
import org.adaway.ui.source.SourceEditRoute
import org.adaway.ui.support.SupportLinks
import org.adaway.ui.support.SupportRoute
import org.adaway.ui.update.UpdateRoute
import org.adaway.ui.update.UpdateViewModel
import org.adaway.ui.welcome.WelcomeRoute

@Composable
internal fun AdAwayNavHost(
    homeViewModel: HomeViewModel,
    startDestination: String = AdAwayRoute.HOME,
    onOpenProjectPage: () -> Unit,
    onOpenUri: (Uri) -> Unit,
    onWelcomeComplete: () -> Unit = {},
    requestedRoute: String? = null,
    onRouteConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val prefsViewModel = viewModel<PrefsViewModel>()
    var visibleHostError by remember { mutableStateOf<HostError?>(null) }

    LaunchedEffect(requestedRoute) {
        val route = requestedRoute ?: return@LaunchedEffect
        navController.navigateSingleTop(route)
        onRouteConsumed()
    }

    LaunchedEffect(homeViewModel, currentRoute) {
        homeViewModel.error.collect { hostError ->
            if (currentRoute == AdAwayRoute.WELCOME || currentRoute == null && startDestination == AdAwayRoute.WELCOME) {
                return@collect
            }
            visibleHostError = hostError
        }
    }

    visibleHostError?.let { error ->
        HostErrorDialog(
            error = error,
            onDismiss = { visibleHostError = null },
            onOpenHelp = {
                visibleHostError = null
                navController.navigateSingleTop(AdAwayRoute.HELP)
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(NAVIGATION_ANIMATION_MILLIS)
            ) + fadeIn(animationSpec = tween(NAVIGATION_ANIMATION_MILLIS))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(NAVIGATION_ANIMATION_MILLIS)
            ) + fadeOut(animationSpec = tween(NAVIGATION_ANIMATION_MILLIS))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(NAVIGATION_ANIMATION_MILLIS)
            ) + fadeIn(animationSpec = tween(NAVIGATION_ANIMATION_MILLIS))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(NAVIGATION_ANIMATION_MILLIS)
            ) + fadeOut(animationSpec = tween(NAVIGATION_ANIMATION_MILLIS))
        }
    ) {
        composable(AdAwayRoute.WELCOME) {
            WelcomeRoute(
                onFinish = {
                    navController.navigate(AdAwayRoute.HOME) {
                        popUpTo(AdAwayRoute.WELCOME) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                    onWelcomeComplete()
                }
            )
        }
        composable(AdAwayRoute.HOME) {
            HomeRoute(
                viewModel = homeViewModel,
                onOpenUpdate = { navController.navigateSingleTop(AdAwayRoute.UPDATE) },
                onOpenBlockedList = {
                    navController.navigateSingleTop(AdAwayRoute.list(ListsRouteDefaults.BLOCKED_HOSTS_TAB))
                },
                onOpenAllowedList = {
                    navController.navigateSingleTop(AdAwayRoute.list(ListsRouteDefaults.ALLOWED_HOSTS_TAB))
                },
                onOpenRedirectedList = {
                    navController.navigateSingleTop(AdAwayRoute.list(ListsRouteDefaults.REDIRECTED_HOSTS_TAB))
                },
                onOpenSources = { navController.navigateSingleTop(AdAwayRoute.HOSTS) },
                onOpenLog = { navController.navigateSingleTop(AdAwayRoute.LOG) },
                onOpenHelp = { navController.navigateSingleTop(AdAwayRoute.HELP) },
                onOpenSupport = { navController.navigateSingleTop(AdAwayRoute.SUPPORT) },
                onOpenPreferences = { navController.navigateSingleTop(AdAwayRoute.PREFS) },
                onOpenProjectPage = onOpenProjectPage
            )
        }
        composable(AdAwayRoute.HOSTS) {
            HostsSourcesRoute(
                onNavigateBack = { navController.navigateUp() },
                onEditSource = { sourceId ->
                    navController.navigateSingleTop(AdAwayRoute.sourceEdit(sourceId))
                }
            )
        }
        composable(
            route = AdAwayRoute.SOURCE_EDIT,
            arguments = listOf(
                navArgument(AdAwayRoute.SOURCE_ID_ARGUMENT) {
                    type = NavType.IntType
                    defaultValue = AdAwayRoute.NO_SOURCE_ID
                }
            )
        ) { backStackEntry ->
            val sourceId = backStackEntry.arguments
                ?.getInt(AdAwayRoute.SOURCE_ID_ARGUMENT, AdAwayRoute.NO_SOURCE_ID)
                ?.takeIf { id -> id != AdAwayRoute.NO_SOURCE_ID }
            SourceEditRoute(
                sourceId = sourceId,
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable(
            route = AdAwayRoute.LISTS,
            arguments = listOf(
                navArgument(AdAwayRoute.LIST_TAB_ARGUMENT) {
                    type = NavType.IntType
                    defaultValue = ListsRouteDefaults.BLOCKED_HOSTS_TAB
                }
            )
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments
                ?.getInt(AdAwayRoute.LIST_TAB_ARGUMENT, ListsRouteDefaults.BLOCKED_HOSTS_TAB)
                ?: ListsRouteDefaults.BLOCKED_HOSTS_TAB
            ListsRoute(
                initialTab = initialTab,
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable(AdAwayRoute.LOG) {
            LogRoute(onNavigateBack = { navController.navigateUp() })
        }
        composable(AdAwayRoute.HELP) {
            HelpRoute(onNavigateBack = { navController.navigateUp() })
        }
        composable(AdAwayRoute.SUPPORT) {
            SupportRoute(onNavigateBack = { navController.navigateUp() })
        }
        composable(AdAwayRoute.PREFS) {
            PrefsRoute(
                destination = PrefsDestination.MAIN,
                viewModel = prefsViewModel,
                onNavigateBack = { navController.navigateUp() },
                onNavigate = { destination ->
                    navController.navigateSingleTop(destination.toRoute())
                },
                onOpenVpnExcludedApps = {
                    navController.navigateSingleTop(AdAwayRoute.VPN_EXCLUDED_APPS)
                }
            )
        }
        composable(AdAwayRoute.PREFS_UPDATE) {
            PrefsRoute(
                destination = PrefsDestination.UPDATE,
                viewModel = prefsViewModel,
                onNavigateBack = { navController.navigateUp() },
                onNavigate = { destination ->
                    navController.navigateSingleTop(destination.toRoute())
                },
                onOpenVpnExcludedApps = {
                    navController.navigateSingleTop(AdAwayRoute.VPN_EXCLUDED_APPS)
                }
            )
        }
        composable(AdAwayRoute.PREFS_ROOT) {
            PrefsRoute(
                destination = PrefsDestination.ROOT,
                viewModel = prefsViewModel,
                onNavigateBack = { navController.navigateUp() },
                onNavigate = { destination ->
                    navController.navigateSingleTop(destination.toRoute())
                },
                onOpenVpnExcludedApps = {
                    navController.navigateSingleTop(AdAwayRoute.VPN_EXCLUDED_APPS)
                }
            )
        }
        composable(AdAwayRoute.PREFS_VPN) {
            PrefsRoute(
                destination = PrefsDestination.VPN,
                viewModel = prefsViewModel,
                onNavigateBack = { navController.navigateUp() },
                onNavigate = { destination ->
                    navController.navigateSingleTop(destination.toRoute())
                },
                onOpenVpnExcludedApps = {
                    navController.navigateSingleTop(AdAwayRoute.VPN_EXCLUDED_APPS)
                }
            )
        }
        composable(AdAwayRoute.PREFS_BACKUP_RESTORE) {
            PrefsRoute(
                destination = PrefsDestination.BACKUP_RESTORE,
                viewModel = prefsViewModel,
                onNavigateBack = { navController.navigateUp() },
                onNavigate = { destination ->
                    navController.navigateSingleTop(destination.toRoute())
                },
                onOpenVpnExcludedApps = {
                    navController.navigateSingleTop(AdAwayRoute.VPN_EXCLUDED_APPS)
                }
            )
        }
        composable(AdAwayRoute.VPN_EXCLUDED_APPS) {
            VpnExcludedAppsRoute(onNavigateBack = { navController.navigateUp() })
        }
        composable(AdAwayRoute.UPDATE) {
            UpdateRoute(
                viewModel = viewModel<UpdateViewModel>(),
                onNavigateBack = { navController.navigateUp() },
                onDonate = { onOpenUri(SupportLinks.SUPPORT_LINK) },
                onSponsor = { onOpenUri(SupportLinks.SPONSORSHIP_LINK) }
            )
        }
    }
}

private fun PrefsDestination.toRoute(): String {
    return when (this) {
        PrefsDestination.MAIN -> AdAwayRoute.PREFS
        PrefsDestination.UPDATE -> AdAwayRoute.PREFS_UPDATE
        PrefsDestination.ROOT -> AdAwayRoute.PREFS_ROOT
        PrefsDestination.VPN -> AdAwayRoute.PREFS_VPN
        PrefsDestination.BACKUP_RESTORE -> AdAwayRoute.PREFS_BACKUP_RESTORE
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

@Composable
private fun HostErrorDialog(
    error: HostError,
    onDismiss: () -> Unit,
    onOpenHelp: () -> Unit
) {
    val message = stringResource(error.detailsKey) + "\n\n" + stringResource(R.string.error_dialog_help)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_error_outline_24dp),
                contentDescription = null
            )
        },
        title = { Text(stringResource(error.messageKey)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_close))
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenHelp) {
                Text(stringResource(R.string.button_help))
            }
        }
    )
}

private const val NAVIGATION_ANIMATION_MILLIS = 220
