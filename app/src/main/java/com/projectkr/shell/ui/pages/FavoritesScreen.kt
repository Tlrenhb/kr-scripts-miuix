// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.pages

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import com.projectkr.krscript.core.model.GroupNode
import com.projectkr.krscript.core.model.NodeInfoBase
import com.projectkr.krscript.core.model.PageNode
import com.projectkr.shell.favorites.FavoritesStore
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem

/**
 * The 收藏 tab: renders favorited nodes by re-parsing their source configs and
 * filtering to the stored keys — fully interactive, like the original favorites.
 */
@Composable
fun FavoritesScreen(
    backStack: MutableList<NavKey>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val store = remember { FavoritesStore(context) }

val content = rememberPageContent(reloadKey) {
        loadFavoriteNodes(context)
    }

    val controller = remember(content.scope) {
        ExecutionController(
            scope = content.scope,
            openPage = { node -> openPageNode(context, node, backStack) },
            storeProvider = { store },
        )
    }

    val scrollBehavior = MiuixScrollBehavior()
    val snackbarHostState = remember { top.yukonga.miuix.kmp.basic.SnackbarHostState() }
    Scaffold(
        snackbarHost = {
            top.yukonga.miuix.kmp.basic.SnackbarHost(state = snackbarHostState)
        },
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = "收藏",
                scrollBehavior = scrollBehavior,
                actions = {
                    OverlayIconDropdownMenu(
                        entry = DropdownEntry(
                            items = (content.nodes ?: emptyList())
                                .filter { it.key.isNotEmpty() }
                                .map { node ->
                                    DropdownItem(
                                        text = "「${node.title}」添加到桌面",
                                        onClick = {
                                            if (com.projectkr.shell.shortcut.ShortcutHelper.pinPageShortcut(
                                                    context, node.currentPageConfigPath, node.title,
                                                )
                                            ) {
                                                content.scope.launch {
                                                    snackbarHostState.showSnackbar("快捷方式已创建")
                                                }
                                            }
                                        },
                                    )
                                },
                        ),
                    ) {
                        Icon(MiuixIcons.More, contentDescription = "更多")
                    }
                    com.projectkr.shell.ui.common.HintedAction(
                        text = "刷新",
                        icon = MiuixIcons.Refresh,
                        onClick = { reloadKey++ },
                    )
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
                // Bind the app bar collapse to the list's scroll deltas.
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            )
        }
    }
}

private fun loadFavoriteNodes(context: Context): List<NodeInfoBase> {
    val store = FavoritesStore(context)
    val entries = store.load()
    if (entries.isEmpty()) return emptyList()

    val runtime = com.projectkr.shell.runtime.KrScriptRuntime
    val extractor =
        com.projectkr.krscript.core.runtime.DefaultAssetExtractor(runtime.assetSource, runtime.fileStore)

    val result = ArrayList<NodeInfoBase>()
    val wanted = entries.associateBy { it.configPath to it.key }
    for (configPath in wanted.keys.map { it.first }.distinct()) {
        val page = PageNode(configPath)
        val nodes = PageLoader.loadSubPage(page) ?: continue
        flatten(nodes).forEach { node ->
            val key = node.key
            if (key.isNotEmpty() && (configPath to key) in wanted) {
                result.add(node)
            }
        }
    }
    return result
}

private fun flatten(nodes: List<NodeInfoBase>): List<NodeInfoBase> =
    nodes.flatMap { if (it is GroupNode) it.children else listOf(it) }
