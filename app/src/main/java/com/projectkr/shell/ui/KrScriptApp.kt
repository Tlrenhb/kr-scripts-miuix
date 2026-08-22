// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui

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
import com.projectkr.shell.ui.theme.KrColorMode
import com.projectkr.shell.ui.theme.loadColorMode
import com.projectkr.shell.ui.theme.rememberThemeController
import com.projectkr.shell.ui.theme.saveColorMode
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
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
    var colorMode by remember { mutableStateOf(loadColorMode(context)) }
    val controller = rememberThemeController(colorMode)

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
        ) {
            entry<Route.MainTabs> {
                MainTabsScreen(
                    colorMode = colorMode,
                    onColorModeChange = {
                        colorMode = it
                        saveColorMode(context, it)
                    },
                    backStack = backStack,
                )
            }

            entry<Route.PageDetail> { route ->
                PageDetailScreen(
                    configPath = route.configPath,
                    title = route.title,
                    nodeKey = route.nodeKey,
                    backStack = backStack,
                )
            }

            entry<Route.OnlinePage> { route ->
                OnlinePageScreen(
                    url = route.url,
                    title = route.title,
                    onBack = onBack,
                )
            }

            entry<Route.Monitor> {
                MonitorScreen(onBack = onBack)
            }

            entry<Route.FileSelector> { route ->
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
    colorMode: KrColorMode,
    onColorModeChange: (KrColorMode) -> Unit,
    backStack: MutableList<NavKey>,
) {
    val tabs = listOf(
        TabItem("首页", MiuixIcons.Home),
        TabItem("页面", MiuixIcons.ListView),
        TabItem("收藏", MiuixIcons.Favorites),
        TabItem("关于", MiuixIcons.Info),
    )
    var selectedTab by remember { mutableStateOf(0) }

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
            when (selectedTab) {
                0 -> HomeScreen(
                    rooted = com.projectkr.shell.runtime.KrScriptRuntime.isReady &&
                        com.projectkr.shell.runtime.KrScriptRuntime.rooted,
                    onOpenFileSelector = { pushIfAbsent(backStack, Route.FileSelector(startDir = "/")) },
                )
                1 -> PagesScreen(backStack = backStack)
                2 -> FavoritesScreen(backStack = backStack)
                else -> AboutScreen(
                    colorMode = colorMode,
                    onColorModeChange = onColorModeChange,
                )
            }
        }
    }
}
