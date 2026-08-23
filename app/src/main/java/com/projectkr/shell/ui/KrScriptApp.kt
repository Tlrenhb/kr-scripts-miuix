// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.projectkr.shell.ShortcutLaunch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.projectkr.shell.navigation.Route
import com.projectkr.shell.ui.fileselector.FileSelectorScreen
import com.projectkr.shell.ui.about.AboutScreen
import com.projectkr.shell.ui.home.HomeScreen
import com.projectkr.shell.ui.monitor.MonitorScreen
import com.projectkr.shell.ui.online.OnlinePageScreen
import com.projectkr.shell.ui.pages.PageDetailScreen
import com.projectkr.shell.ui.pages.FavoritesScreen
import com.projectkr.shell.ui.pages.PagesScreen
import com.projectkr.shell.ui.pages.pushIfAbsent
import com.projectkr.shell.ui.theme.loadThemeConfig
import com.projectkr.shell.ui.theme.rememberThemeController
import com.projectkr.shell.ui.theme.saveThemeConfig
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class TabItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

/**
 * Application root: owns the single [MiuixTheme] and the navigation back stack.
 * Tab selection lives in [MainTabsScreen] — never derived from nav entries.
 */
@Composable
fun KrScriptApp(shortcut: ShortcutLaunch? = null) {
    val context = LocalContext.current
    var themeConfig by remember { mutableStateOf(loadThemeConfig(context)) }
    val controller = rememberThemeController(themeConfig)

    MiuixTheme(controller = controller) {
        val backStack = rememberNavBackStack<Route>(Route.MainTabs)
        val onBack: () -> Unit = { backStack.removeLastOrNull() }

        // Shortcut deep links (cold and warm start) push the target page once.
        LaunchedEffect(shortcut) {
            shortcut?.let { launch ->
                val route = Route.PageDetail(
                    configPath = launch.configPath,
                    title = launch.title,
                    nodeKey = "",
                )
                if (backStack.none { it == route }) backStack.add(route)
            }
        }

        NavDisplay(
            backStack = backStack,
            onBack = onBack,
            // Miuix motion system: slide+parallax transitions, device-corner
            // clipping while pages animate over each other, input blocked
            // mid-transition, and edge-swipe back on pushed pages.
            transition = NavTransitions.MiuixDefault,
            effects = NavDisplayEffects(
                cornerClipRadius = rememberNavSystemCornerRadius(),
                blockInputDuringTransition = true,
            ),
        ) {
            entry<Route.MainTabs> {
                MainTabsScreen(
                    themeConfig = themeConfig,
                    onThemeConfigChange = {
                        themeConfig = it
                        saveThemeConfig(context, it)
                    },
                    backStack = backStack,
                )
            }

            entry<Route.PageDetail>(
                swipeDismiss = NavSwipeDirection.LeftToRight,
            ) { route ->
                PageDetailScreen(
                    configPath = route.configPath,
                    title = route.title,
                    nodeKey = route.nodeKey,
                    backStack = backStack,
                )
            }

            entry<Route.OnlinePage>(
                swipeDismiss = NavSwipeDirection.LeftToRight,
            ) { route ->
                OnlinePageScreen(
                    url = route.url,
                    title = route.title,
                    onBack = onBack,
                )
            }

            entry<Route.Monitor>(
                swipeDismiss = NavSwipeDirection.LeftToRight,
            ) {
                MonitorScreen(onBack = onBack)
            }

            // Sheet-like: slides bottom-up, dismiss by dragging down.
            entry<Route.FileSelector>(
                transition = NavTransitions.Modal,
                swipeDismiss = NavSwipeDirection.TopToBottom,
            ) { route ->
                FileSelectorScreen(
                    startDir = route.startDir,
                    onBack = onBack,
                )
            }
        }
    }
}

/**
 * Four-tab shell (首页 / 页面 / 收藏 / 关于). Tab selection is local UI state;
 * each tab screen hosts its own Scaffold (and thus its own Overlay host).
 */
@Composable
private fun MainTabsScreen(
    themeConfig: com.projectkr.shell.ui.theme.KrThemeConfig,
    onThemeConfigChange: (com.projectkr.shell.ui.theme.KrThemeConfig) -> Unit,
    backStack: MutableList<NavKey>,
) {
    val tabs = listOf(
        TabItem("首页", MiuixIcons.Home),
        TabItem("页面", MiuixIcons.ListView),
        TabItem("收藏", MiuixIcons.Favorites),
        TabItem("关于", MiuixIcons.Info),
    )
    var selectedTab by remember { mutableStateOf(0) }

    // Android 13+: ask once for notification permission (bg-task completion).
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Outer Scaffold owns the NavigationBar; consumeWindowInsets makes the
    // inner per-tab Scaffolds see the already-applied insets exactly once
    // (per the Scaffold KDoc: padding + consumeWindowInsets).
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = tab.icon,
                        label = tab.label,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    // Directional slide: moving right slides content left.
                    val toRight = targetState > initialState
                    if (toRight) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "tab-switch",
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(
                        rooted = com.projectkr.shell.runtime.KrScriptRuntime.isReady &&
                            com.projectkr.shell.runtime.KrScriptRuntime.rooted,
                        onOpenFileSelector = { pushIfAbsent(backStack, Route.FileSelector(startDir = "/")) },
                    )
                    1 -> PagesScreen(backStack = backStack)
                    2 -> FavoritesScreen(backStack = backStack)
                    else -> AboutScreen(
                        themeConfig = themeConfig,
                        onThemeConfigChange = onThemeConfigChange,
                    )
                }
            }
        }
    }
}
