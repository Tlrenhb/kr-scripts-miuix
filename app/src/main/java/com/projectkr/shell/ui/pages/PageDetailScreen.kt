// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.pages

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
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.projectkr.krscript.core.model.PageMenuOption
import com.projectkr.krscript.core.model.PageNode
import com.projectkr.shell.navigation.Route
import com.projectkr.shell.runtime.ScriptActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * A KrScript sub page: loads its config, renders the node list and hosts the
 * page menu options (TopBar dropdown) plus the shared execution dialogs.
 */
@Composable
fun PageDetailScreen(
    configPath: String,
    title: String,
    nodeKey: String,
    backStack: MutableList<NavKey>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val page = remember(nodeKey) {
        PageRegistry.get(nodeKey) ?: PageNode(configPath).apply { this.title = title }
    }

    val content = rememberPageContent(reloadKey) {
        PageLoader.loadSubPage(page)
    }
    LaunchedEffect(content.loading) {
        if (!content.loading) refreshing = false
    }
    val controller = remember(content.scope) {
        ExecutionController(
            scope = content.scope,
            openPage = { node -> openPageNode(context, node, backStack) },
            appContext = context.applicationContext,
        )
    }

    // Menu options: inline list from the parent config + options-sh script.
    var menuOptions by remember(nodeKey) { mutableStateOf(page.pageMenuOptions ?: emptyList()) }
    LaunchedEffect(page.pageMenuOptionsSh) {
        if (page.pageMenuOptionsSh.isNotEmpty()) {
            val lines = withContext(Dispatchers.IO) {
                ScriptActions.eval(page.pageMenuOptionsSh, page)
            }
            val fromScript = parseOptionLines(lines).map { item ->
                PageMenuOption(page.currentPageConfigPath).apply {
                    key = item.value ?: item.title.orEmpty()
                    this.title = item.title ?: item.value.orEmpty()
                }
            }
            menuOptions = page.pageMenuOptions.orEmpty() + fromScript
        }
    }

    fun handleMenuOption(option: PageMenuOption) {
        when (option.type) {
            "refresh" -> reloadKey++
            "finish" -> backStack.removeLastOrNull()
            "file" -> pushIfAbsent(backStack, Route.FileSelector(startDir = "/"))
            else -> controller.onRunnable(option, emptyMap())
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    if (menuOptions.isNotEmpty()) {
                        OverlayIconDropdownMenu(
                            entry = DropdownEntry(
                                items = menuOptions.map { option ->
                                    DropdownItem(
                                        text = option.title,
                                        onClick = { handleMenuOption(option) },
                                    )
                                },
                            ),
                        ) {
                            Icon(MiuixIcons.More, contentDescription = "菜单")
                        }
                    }
                },
            )
        },
    ) { inner ->
        PullToRefresh(
            // Doc contract: set isRefreshing true synchronously in onRefresh.
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                reloadKey++
            },
            modifier = Modifier.padding(inner),
        ) {
            NodeScreenBody(
                controller = controller,
                nodes = content.nodes,
                loading = content.loading,
                onRetry = { reloadKey++ },
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical(),
            )
        }
    }
}
