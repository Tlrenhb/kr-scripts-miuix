// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
) {
    NodeListContent(
        nodes = nodes ?: emptyList(),
        callbacks = controller,
        modifier = modifier,
    )

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
}
