// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import com.projectkr.krscript.core.model.GroupNode
import com.projectkr.krscript.core.model.NodeInfoBase
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar

/**
 * The 页面 tab: renders the page list from kr-script.conf with pull-to-refresh,
 * search filtering and the shared execution dialogs.
 */
@Composable
fun PagesScreen(
    backStack: MutableList<NavKey>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val content = rememberPageContent(reloadKey) { PageLoader.loadTopPage() }
    LaunchedEffect(content.loading) {
        if (!content.loading) refreshing = false
    }

    val controller = remember(content.scope) {
        ExecutionController(
            scope = content.scope,
            openPage = { node -> openPageNode(context, node, backStack) },
            storeProvider = { com.projectkr.shell.favorites.FavoritesStore(context) },
        )
    }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = "页面",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { searching = !searching }) {
                        Icon(MiuixIcons.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { reloadKey++ }) {
                        Icon(MiuixIcons.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.padding(inner)) {
            AnimatedVisibility(visible = searching) {
                SearchBar(
                    inputField = {
                        InputField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { searchExpanded = false },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            label = "搜索功能",
                        )
                    },
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                ) {
                }
            }
            PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    reloadKey++
                },
                topAppBarScrollBehavior = scrollBehavior,
            ) {
                NodeScreenBody(
                    controller = controller,
                    nodes = filterNodes(content.nodes, searchQuery),
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                )
            }
        }
    }
}


/**
 * Filters nodes by query across title/desc/summary. Groups whose children all
 * match nothing are dropped; matching groups keep only their matching children.
 * Never mutates the parsed originals.
 */
internal fun filterNodes(nodes: List<NodeInfoBase>?, query: String): List<NodeInfoBase> {
    if (nodes == null || query.isEmpty()) return nodes ?: emptyList()
    val q = query.lowercase()

    fun NodeInfoBase.matches(): Boolean =
        title.lowercase().contains(q) || desc.lowercase().contains(q) ||
            summary.lowercase().contains(q)

    return nodes.mapNotNull { node ->
        when (node) {
            is GroupNode -> {
                val children = node.children.filter { it.matches() }
                if (children.isEmpty()) {
                    null
                } else {
                    GroupNode(node.currentPageConfigPath).apply {
                        key = node.key
                        title = node.title
                        desc = node.desc
                        summary = node.summary
                        supported = true
                        this.children.addAll(children)
                    }
                }
            }
            else -> if (node.matches()) node else null
        }
    }
}
