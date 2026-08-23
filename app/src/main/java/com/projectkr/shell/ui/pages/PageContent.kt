// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectkr.krscript.core.model.ActionNode
import com.projectkr.krscript.core.model.NodeInfoBase
import com.projectkr.krscript.core.model.RunnableNode
import com.projectkr.shell.ui.dialogs.ActionParamsDialog
import com.projectkr.shell.ui.dialogs.ConfirmDialog
import com.projectkr.shell.ui.dialogs.LogDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Shared page-rendering state used by both the 页面 tab and pushed PageDetail
 * screens: loads nodes on IO and tracks refresh cycles.
 */
class PageContentState(
    val nodes: List<NodeInfoBase>?,
    val loading: Boolean,
    internal val scope: CoroutineScope,
)

@Composable
fun rememberPageContent(reloadKey: Int, loadNodes: () -> List<NodeInfoBase>?): PageContentState {
    var nodes by remember { mutableStateOf<List<NodeInfoBase>?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        loading = true
        nodes = withContext(Dispatchers.IO) { loadNodes() }
        loading = false
    }

    return PageContentState(nodes, loading, scope)
}

/**
 * Renders node content plus the confirm / params / log dialogs bound to one
 * [ExecutionController]. Every KrScript page screen reuses this.
 */
@Composable
fun NodeScreenBody(
    controller: ExecutionController,
    nodes: List<NodeInfoBase>?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Box(modifier) {
        when {
            // Parsing in flight.
            loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            // Parse failed (readConfigXml returned null).
            nodes == null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("页面配置解析失败", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(12.dp))
                TextButton(text = "重试", onClick = onRetry)
            }

            // Config parsed but defines nothing.
            nodes.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无功能", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }

            else -> NodeListContent(
                nodes = nodes,
                callbacks = controller,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // Doc lifecycle: keep dialogs composed and bind `show` to state so the
    // hide animation runs; retain the last payload while the animation plays.
    var lastConfirm by remember { mutableStateOf<RunnableNode?>(null) }
    controller.confirmRequest?.let { lastConfirm = it }
    lastConfirm?.let { node ->
        ConfirmDialog(
            node = node,
            show = controller.confirmRequest != null,
            onConfirm = { controller.submitConfirmation() },
            onDismiss = { controller.dismissConfirmation() },
        )
    }

    var lastParams by remember { mutableStateOf<ActionNode?>(null) }
    controller.paramsAction?.let { lastParams = it }
    lastParams?.let { action ->
        ActionParamsDialog(
            node = action,
            show = controller.paramsAction != null,
            onSubmit = { values -> controller.submitParams(action, values) },
            onDismiss = { controller.dismissParams() },
        )
    }

    var lastSession by remember { mutableStateOf<com.projectkr.shell.runtime.ScriptActions.Session?>(null) }
    controller.activeSession?.let { lastSession = it }
    LogDialog(
        session = lastSession,
        show = controller.activeSession != null,
        onClose = { controller.closeSession() },
    )

    // Completion flags of VISIBLE sessions: reload-page / auto-finish / blocks.
    LaunchedEffect(controller.activeSession?.running, controller.activeSession?.exitCode) {
        val s = controller.activeSession ?: return@LaunchedEffect
        if (!s.running) controller.onSessionCompleted(s)
    }
}
