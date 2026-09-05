// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.projectkr.shell.ShortcutLaunch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState

private enum class TabKind { HOME, PAGES, FAVORITES, ABOUT }

private data class TabItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val kind: TabKind,
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
                    nodeKey = launch.nodeKey,
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
                    extension = route.extension,
                    folderMode = route.folderMode,
                    onBack = onBack,
                )
            }
        }
    }
}

/**
 * Four-tab shell (首页 / 页面 / 收藏 / 设置). Tab selection is local UI state;
 * each tab screen hosts its own Scaffold (and thus its own Overlay host).
 */
@Composable
private fun MainTabsScreen(
    themeConfig: com.projectkr.shell.ui.theme.KrThemeConfig,
    onThemeConfigChange: (com.projectkr.shell.ui.theme.KrThemeConfig) -> Unit,
    backStack: MutableList<NavKey>,
) {
    // Original rule: the dashboard tab exists only when root is available AND
    // kr-script.conf allow_home_page != "0".
    val runtime = com.projectkr.shell.runtime.KrScriptRuntime
    val showHome = runtime.isReady && runtime.allowHomePage && runtime.rooted
    val tabs = buildList {
        if (showHome) add(TabItem("首页", MiuixIcons.Home, TabKind.HOME))
        add(TabItem("页面", MiuixIcons.ListView, TabKind.PAGES))
        add(TabItem("收藏", MiuixIcons.Favorites, TabKind.FAVORITES))
        add(TabItem("设置", MiuixIcons.Settings, TabKind.ABOUT))
    }
    // Tab selection survives configuration/size changes; compact and wide
    // chrome deliberately consume this one source of truth.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Clamp selection when the home tab disappears after init completes.
    LaunchedEffect(showHome) {
        if (!showHome && selectedTab == 0) selectedTab = 0 // first visible is 页面
        selectedTab = selectedTab.coerceIn(0, tabs.lastIndex)
    }

    // Android 13+: ask once for notification permission (bg-task completion).
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // KernelSU/Miuix adaptive shell: touch-first bottom navigation on compact
    // screens, expanding NavigationRail from the same selected-tab state when
    // a tablet, foldable, desktop, or wide landscape window has room.
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val useNavigationRail = with(density) {
        val width = windowSize.width.toDp()
        val height = windowSize.height.toDp()
        width >= 840.dp || (width >= 600.dp && height / width < 1.2f)
    }
    val railState = rememberNavigationRailState(
        // Keep the rail collapsed initially; users can opt into its official
        // built-in expanded presentation from its header control.
        initialValue = NavigationRailValue.Collapsed,
    )

    @Composable
    fun TabContent() {
        androidx.compose.animation.AnimatedContent(
            targetState = selectedTab,
            // Keep tab changes calm: only a standard fade, with no depth,
            // card transition, or gesture-physics treatment.
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tab-switch",
        ) { tabIndex ->
            when (tabs.getOrNull(tabIndex.coerceIn(0, tabs.lastIndex))?.kind) {
                TabKind.HOME -> HomeScreen(
                    rooted = runtime.rooted,
                    onOpenFileSelector = { pushIfAbsent(backStack, Route.FileSelector(startDir = "/")) },
                    onOpenMonitor = { pushIfAbsent(backStack, Route.Monitor) },
                )
                TabKind.PAGES -> PagesScreen(backStack = backStack)
                TabKind.FAVORITES -> FavoritesScreen(backStack = backStack)
                else -> AboutScreen(
                    themeConfig = themeConfig,
                    onThemeConfigChange = onThemeConfigChange,
                )
            }
        }
    }

    // Keep TabContent at one composition location. Switching phone ↔ tablet
    // therefore changes chrome only, rather than disposing every tab's local
    // scroll/form state (the Miuix Example uses this same structure).
    Row(modifier = Modifier.fillMaxSize()) {
        if (useNavigationRail) {
            NavigationRail(state = railState) {
                tabs.forEachIndexed { index, tab ->
                    NavigationRailItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = tab.icon,
                        label = tab.label,
                    )
                }
            }
        }
        Scaffold(
            modifier = if (useNavigationRail) {
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            },
            bottomBar = {
                if (!useNavigationRail) {
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
                }
            },
        ) { innerPadding ->
            // The outer shell consumes its insets once. Every individual tab
            // still owns its Scaffold/popup host, but receives no duplicate
            // status/nav-bar inset from this ancestor.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                TabContent()
            }
        }
    }
}
