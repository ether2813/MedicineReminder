package com.mediminder.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mediminder.app.R
import com.mediminder.app.ui.detail.DetailScreen
import com.mediminder.app.ui.editor.EditorScreen
import com.mediminder.app.ui.history.HistoryScreen
import com.mediminder.app.ui.medicines.MedicinesScreen
import com.mediminder.app.ui.settings.SettingsScreen
import com.mediminder.app.ui.today.TodayScreen

object Routes {
    const val TODAY = "today"
    const val MEDICINES = "medicines"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val EDITOR = "editor?id={id}"
    const val DETAIL = "medicine/{id}"
    fun editor(id: Long = 0L) = "editor?id=$id"
    fun detail(id: Long) = "medicine/$id"
}

private data class TopLevel(
    val route: String,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

private val topLevel = listOf(
    TopLevel(Routes.TODAY, R.string.nav_today, Icons.Rounded.Today, Icons.Outlined.Today),
    TopLevel(Routes.MEDICINES, R.string.nav_medicines, Icons.Rounded.Medication, Icons.Outlined.Medication),
    TopLevel(Routes.HISTORY, R.string.nav_history, Icons.Rounded.History, Icons.Outlined.History),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
)

@Composable
fun MediNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = topLevel.any { t -> destination?.hierarchy?.any { it.route == t.route } == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar {
                    topLevel.forEach { item ->
                        val selected = destination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) item.selectedIcon else item.icon, null) },
                            label = { Text(stringResource(item.label), maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.TODAY,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.98f) },
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onAddMedicine = { nav.navigate(Routes.editor()) },
                    onOpenMedicine = { nav.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.MEDICINES) {
                MedicinesScreen(
                    onAddMedicine = { nav.navigate(Routes.editor()) },
                    onOpenMedicine = { nav.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                Routes.EDITOR,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
            ) { entry ->
                EditorScreen(
                    medicineId = entry.arguments?.getLong("id") ?: 0L,
                    onClose = { nav.popBackStack() },
                )
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                DetailScreen(
                    medicineId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editor(id)) },
                )
            }
        }
    }
}
