// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectkr.krscript.core.model.ActionNode
import com.projectkr.krscript.core.config.PathAnalysis
import com.projectkr.krscript.core.model.GroupNode
import com.projectkr.krscript.core.model.NodeInfoBase
import com.projectkr.krscript.core.model.PageNode
import com.projectkr.krscript.core.model.PickerNode
import com.projectkr.krscript.core.model.RunnableNode
import com.projectkr.krscript.core.model.SelectItem
import com.projectkr.krscript.core.model.SwitchNode
import com.projectkr.krscript.core.model.TextNode
import com.projectkr.shell.runtime.ScriptActions
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Callbacks the node list needs from its host screen.
 */
interface NodeListCallbacks {
    /**
     * A runnable node fired: switch toggle ([params] carries `state`),
     * picker selection (`state` carries the chosen value), or action click.
     */
    fun onRunnable(node: RunnableNode, params: Map<String, String>)

    /** Opens a page node (sub config / html / link / activity). */
    fun onPage(node: PageNode)

    /** Whether the favorite star should be offered for this node. */
    fun canFavorite(node: NodeInfoBase): Boolean = false

    fun isFavorite(node: NodeInfoBase): Boolean = false

    fun toggleFavorite(node: NodeInfoBase) {}
}

/**
 * Renders KrScript nodes with Miuix components:
 *
 *  - Group  → SmallTitle + Card grouping
 *  - Switch → SwitchPreference
 *  - Picker → OverlayDropdownPreference
 *  - Action / Page → ArrowPreference
 *  - Text   → styled slice rows
 */
@Composable
fun NodeListContent(
    nodes: List<NodeInfoBase>,
    callbacks: NodeListCallbacks,
    modifier: Modifier = Modifier,
) {
    // Must be a scrollable container: PullToRefresh and TopAppBar collapse are
    // both driven by nested-scroll deltas dispatched from this list.
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        nodes.forEach { node ->
            when (node) {
                is GroupNode -> item(key = node.index) {
                    GroupSection(node, callbacks)
                    if (node.children.isEmpty()) Spacer(Modifier.height(12.dp))
                }
                is TextNode -> item(key = node.index) { TextCard(node) }
                is PageNode -> item(key = node.index) {
                    NodeCard { PageRow(node, callbacks) }
                }
                is ActionNode -> item(key = node.index) {
                    NodeCard { ArrowAction(node, callbacks) }
                }
                is SwitchNode -> item(key = node.index) {
                    NodeCard { SwitchRow(node, callbacks) }
                }
                is PickerNode -> item(key = node.index) {
                    NodeCard { PickerRow(node, callbacks) }
                }
            }
        }
    }
}

@Composable
private fun GroupSection(group: GroupNode, callbacks: NodeListCallbacks) {
    if (!group.supported) return
    SmallTitle(text = group.title)
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            renderGroupChildren(group.children, callbacks)
        }
    }
}

/**
 * Renders children inside a group Card. Text nodes and nested groups are
 * supported (original PageLayoutRender rendered both inside groups).
 */
@Composable
private fun renderGroupChildren(
    children: List<NodeInfoBase>,
    callbacks: NodeListCallbacks,
) {
    children.forEach { child ->
        when (child) {
            is SwitchNode -> SwitchRow(child, callbacks)
            is PickerNode -> PickerRow(child, callbacks)
            is ActionNode -> ArrowAction(child, callbacks)
            is PageNode -> PageRow(child, callbacks)
            is TextNode -> {
                if (child.summary.isNotEmpty()) {
                    Text(
                        text = child.summary,
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                child.rows.forEach { row -> TextSlice(row) }
            }
            is GroupNode -> {
                Text(
                    text = child.title,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                renderGroupChildren(child.children, callbacks)
            }
            else -> {}
        }
    }
}

@Composable
private fun NodeCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
    ) {
        content()
    }
}

@Composable
private fun SwitchRow(node: SwitchNode, callbacks: NodeListCallbacks) {
    var checked by remember(node.index) { mutableStateOf(node.checked) }
    SwitchPreference(
        title = node.title,
        summary = node.summary.ifEmpty { node.desc },
        checked = checked,
        startAction = { NodeIcon(node) },
        onCheckedChange = { value ->
            checked = value
            node.checked = value // keep model and UI in one direction
            callbacks.onRunnable(node, mapOf("state" to if (value) "1" else "0"))
        },
        endActions = { FavoriteStar(node, callbacks) },
    )
}

/**
 * Renders the XML `icon`/`icon-path` attribute: resolved against the page dir
 * (assets / disk / root fallback) and decoded off the main thread.
 */
@Composable
private fun NodeIcon(node: NodeInfoBase) {
    val clickable = node as? com.projectkr.krscript.core.model.ClickableNode
    // IconPathAnalysis order: logo first, then icon.
    val iconPath = (clickable?.logoPath ?: "").ifEmpty { clickable?.iconPath ?: "" }.trim()
    if (iconPath.isEmpty()) return

    var bitmap by remember(iconPath, node.currentPageConfigPath) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(iconPath, node.currentPageConfigPath) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val runtime = com.projectkr.shell.runtime.KrScriptRuntime
                if (!runtime.isReady) return@runCatching null
                val extractor = com.projectkr.krscript.core.runtime.DefaultAssetExtractor(runtime.assetSource, runtime.fileStore)
                val locator = PathAnalysis(
                    assets = runtime.assetSource,
                    files = runtime.fileStore,
                    shell = runtime.shell,
                    extractor = extractor,
                    parentDir = node.pageConfigDir,
                )
                locator.parsePath(iconPath)?.stream?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    bitmap?.let { bmp ->
        androidx.compose.foundation.Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Star toggle with a pop bounce when favorited. */
@Composable
fun FavoriteStar(node: NodeInfoBase, callbacks: NodeListCallbacks) {
    if (!callbacks.canFavorite(node)) return
    val favorited = callbacks.isFavorite(node)
    val bounce = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(favorited) {
        if (favorited) {
            bounce.snapTo(1.4f)
            bounce.animateTo(
                1f,
                androidx.compose.animation.core.spring(dampingRatio = 0.4f),
            )
        } else {
            bounce.snapTo(1f)
        }
    }
    IconButton(onClick = { callbacks.toggleFavorite(node) }) {
        Icon(
            imageVector = if (favorited) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
            contentDescription = if (favorited) "取消收藏" else "收藏",
            tint = if (favorited) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    scaleX = bounce.value
                    scaleY = bounce.value
                },
        )
    }
}

@Composable
private fun PickerRow(node: PickerNode, callbacks: NodeListCallbacks) {
    var options by remember(node.index) { mutableStateOf(node.options ?: ArrayList()) }
    LaunchedEffect(node.optionsSh) {
        if (node.optionsSh.isNotEmpty() && options.isEmpty()) {
            options = withContext(Dispatchers.IO) {
                parseOptionLines(ScriptActions.eval(node.optionsSh, node))
            }
        }
    }
    val titles = options.map { it.title.orEmpty() }
    // Selection is snapshot state (doc pattern); the model field mirrors it.
    var selectedIndex by remember(options) {
        mutableIntStateOf(options.indexOfFirst { it.value == node.value })
    }

    OverlayDropdownPreference(
        title = node.title,
        summary = node.summary.ifEmpty { node.desc },
        items = titles,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index ->
            selectedIndex = index
            if (index in options.indices) {
                node.value = options[index].value
                callbacks.onRunnable(node, mapOf("state" to options[index].value.orEmpty()))
            }
        },
    )
}

@Composable
private fun ArrowAction(node: ActionNode, callbacks: NodeListCallbacks) {
    ArrowPreference(
        title = node.title,
        summary = node.summary.ifEmpty { node.desc },
        startAction = { NodeIcon(node) },
        onClick = { callbacks.onRunnable(node, emptyMap()) },
        endActions = { FavoriteStar(node, callbacks) },
    )
}

@Composable
private fun PageRow(node: PageNode, callbacks: NodeListCallbacks) {
    ArrowPreference(
        title = node.title,
        summary = node.summary.ifEmpty { node.desc },
        startAction = { NodeIcon(node) },
        onClick = { callbacks.onPage(node) },
        endActions = { FavoriteStar(node, callbacks) },
    )
}

@Composable
private fun TextCard(node: TextNode) {
    SmallTitle(text = node.title)
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (node.summary.isNotEmpty()) {
                Text(
                    text = node.summary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            node.rows.forEach { row -> TextSlice(row) }
        }
    }
}

@Composable
private fun TextSlice(row: TextNode.TextRow) {
    var text by remember(row) { mutableStateOf(row.text) }
    val sliceResult = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    LaunchedEffect(row.dynamicTextSh) {
        if (row.dynamicTextSh.isNotEmpty()) {
            text = withContext(Dispatchers.IO) {
                ScriptActions.eval(row.dynamicTextSh, null)
            }
        }
    }
    val align = when (row.align) {
        TextNode.Align.LEFT -> TextAlign.Left
        TextNode.Align.RIGHT -> TextAlign.Right
        TextNode.Align.CENTER -> TextAlign.Center
        TextNode.Align.NORMAL -> TextAlign.Start
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val clickableMods = when {
        row.link.isNotEmpty() -> Modifier.clickable { openInBrowser(context, row.link) }
        row.activity.isNotEmpty() -> Modifier.clickable {
            com.projectkr.shell.ui.pages.openActivity(context, row.activity)
        }
        row.onClickScript.isNotEmpty() -> Modifier.clickable {
            // Original ListItemText: the script output is shown in a dialog.
            val result = com.projectkr.shell.runtime.ScriptActions.eval(
                row.onClickScript, null,
            )
            if (result.trim().isNotEmpty()) {
                sliceResult.value = result
            }
        }
        else -> Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableMods)
            .then(
                if (row.bgColor != -1) Modifier.background(Color(row.bgColor)) else Modifier
            ),
    ) {
        Text(
            text = text,
            fontSize = if (row.size > 0) row.size.sp else 15.sp,
            fontWeight = if (row.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (row.italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (row.underline) TextDecoration.Underline else null,
            color = if (row.color != -1) Color(row.color) else Color.Unspecified,
            textAlign = align,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    // Original ListItemText: run-script results surface in a help dialog.
    sliceResult.value?.let { result ->
        top.yukonga.miuix.kmp.overlay.OverlayDialog(
            title = "脚本输出",
            show = true,
            onDismissRequest = { sliceResult.value = null },
        ) {
            Text(
                text = result,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        }
    }
}

/** Parses `key|title` option lines produced by options-sh scripts. */
fun parseOptionLines(result: String): ArrayList<SelectItem> {
    val items = ArrayList<SelectItem>()
    result.split("\n").forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@forEach
        val item = SelectItem()
        if (line.contains("|")) {
            val parts = line.split("|", limit = 2)
            item.value = parts[0]
            item.title = parts.getOrElse(1) { parts[0] }
        } else {
            item.value = line
            item.title = line
        }
        items.add(item)
    }
    return items
}
