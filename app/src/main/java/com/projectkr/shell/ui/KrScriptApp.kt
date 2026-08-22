// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.projectkr.shell.navigation.Route
import com.projectkr.shell.ui.fileselector.FileSelectorScreen
import com.projectkr.shell.ui.home.HomeScreen
import com.projectkr.shell.ui.monitor.MonitorScreen
import com.projectkr.shell.ui.online.OnlinePageScreen
import com.projectkr.shell.ui.pages.PageDetailScreen
import com.projectkr.shell.ui.pages.PagesScreen
import com.projectkr.shell.ui.theme.KrColorMode
import com.projectkr.shell.ui.theme.loadColorMode
import com.projectkr.shell.ui.theme.rememberThemeController
import com.projectkr.shell.ui.theme.saveColorMode
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
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
fun KrScriptApp() {
    val context = LocalContext.current
    var colorMode by remember { mutableStateOf(loadColorMode(context)) }
    val controller = rememberThemeController(colorMode)

    MiuixTheme(controller = controller) {
        // Launcher shortcuts deep-link straight into a page detail.
        val initialRoutes = remember {
            val cfg = (context as? android.app.Activity)?.intent?.getStringExtra(
                com.projectkr.shell.shortcut.ShortcutHelper.EXTRA_CONFIG
            ).orEmpty()
            val shortcutTitle = (context as? android.app.Activity)?.intent?.getStringExtra(
                com.projectkr.shell.shortcut.ShortcutHelper.EXTRA_TITLE
            ).orEmpty()
            if (cfg.isNotEmpty()) {
                listOf<Route>(Route.MainTabs, Route.PageDetail(cfg, shortcutTitle, ""))
            } else {
                listOf<Route>(Route.MainTabs)
            }
        }
        val backStack = rememberNavBackStack<Route>(*initialRoutes.toTypedArray())
        val onBack: () -> Unit = { backStack.removeLastOrNull() }

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
 * screens render inside one shared Scaffold so Overlay popups have a host.
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
        when (selectedTab) {
            0 -> Box(Modifier.padding(innerPadding)) {
                HomeScreen(
                    rooted = com.projectkr.shell.runtime.KrScriptRuntime.isReady &&
                        com.projectkr.shell.runtime.KrScriptRuntime.rooted,
                    onOpenFileSelector = { backStack.add(Route.FileSelector(startDir = "/")) },
                )
            }
            1 -> Box(Modifier.padding(innerPadding)) {
                PagesScreen(backStack = backStack)
            }
            else -> PlaceholderTab(tabs[selectedTab].label, innerPadding)
        }
    }
}

/** Temporary stand-in for tabs completed in later phases. */
@Composable
private fun PlaceholderTab(label: String, innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label)
    }
}
