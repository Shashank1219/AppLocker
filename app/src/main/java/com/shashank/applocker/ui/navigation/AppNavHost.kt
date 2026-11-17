package com.shashank.applocker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shashank.applocker.feature.apps.AppSelectionScreen
import com.shashank.applocker.feature.pin.ChangePinScreen
import com.shashank.applocker.feature.pin.PinSetupScreen
import com.shashank.applocker.feature.pin.PinUnlockScreen
import com.shashank.applocker.feature.protection.ProtectionScreen
import com.shashank.applocker.feature.settings.SettingsScreen

/** Screens reachable without the PIN. Everything else is gated on [isUnlocked]. */
private val openRoutes = setOf(Route.PinSetup.path, Route.PinUnlock.path)

/**
 * Navigation state can be restored after process death or rotation, so it is never trusted:
 * a protected route renders nothing while locked, and a locked session is sent back to
 * [startDestination] (the PIN screen).
 */
@Composable
fun AppNavHost(
    startDestination: String,
    isUnlocked: Boolean,
    onUnlocked: () -> Unit,
    onDataWiped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(isUnlocked, route) {
        if (!isUnlocked && route != null && route !in openRoutes) {
            navController.navigate(startDestination) { popUpTo(navController.graph.id) { inclusive = true } }
        }
    }
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(Route.PinSetup.path) {
            PinSetupScreen(onCompleted = {
                onUnlocked()
                navController.goToAndClear(Route.Settings)
            })
        }
        composable(Route.PinUnlock.path) {
            PinUnlockScreen(
                onUnlocked = {
                    onUnlocked()
                    navController.goToAndClear(Route.Settings)
                },
                // Device credential confirmed: let the user choose a new PIN.
                onRecoveryAuthorized = { navController.goToAndClear(Route.PinSetup) },
            )
        }
        composable(Route.Settings.path) {
            Gated(isUnlocked) {
                SettingsScreen(
                    onOpenApps = { navController.navigate(Route.AppSelection.path) },
                    onOpenProtection = { navController.navigate(Route.Protection.path) },
                    onChangePin = { navController.navigate(Route.ChangePin.path) },
                    onWiped = onDataWiped,
                )
            }
        }
        composable(Route.AppSelection.path) { Gated(isUnlocked) { AppSelectionScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.Protection.path) { Gated(isUnlocked) { ProtectionScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ChangePin.path) { Gated(isUnlocked) { ChangePinScreen(onClose = { navController.popBackStack() }) } }
    }
}

@Composable
private fun Gated(isUnlocked: Boolean, content: @Composable () -> Unit) {
    if (isUnlocked) content()
}

private fun NavController.goToAndClear(route: Route) {
    navigate(route.path) { popUpTo(graph.id) { inclusive = true } }
}
