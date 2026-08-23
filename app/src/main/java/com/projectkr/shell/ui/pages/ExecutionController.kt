// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.projectkr.krscript.core.model.ActionNode
import android.content.Context
import com.projectkr.krscript.core.model.ActionNode
import com.projectkr.krscript.core.model.PageNode
import com.projectkr.krscript.core.model.RunnableNode
import kotlinx.coroutines.delay
import com.projectkr.shell.runtime.ScriptActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shared execution flow behind every node list screen:
 * confirm → params collection → streaming session (log dialog / bg-task / hidden).
 */
class ExecutionController(
    private val scope: CoroutineScope,
    private val openPage: (PageNode) -> Unit,
    private val storeProvider: (() -> com.projectkr.shell.favorites.FavoritesStore)? = null,
    private val appContext: Context? = null,
) : NodeListCallbacks {

    override fun onPage(node: PageNode) = openPage(node)

    override fun canFavorite(node: com.projectkr.krscript.core.model.NodeInfoBase): Boolean =
        storeProvider != null &&
            node.currentPageConfigPath.isNotEmpty() &&
            node.key.isNotEmpty()

    override fun isFavorite(node: com.projectkr.krscript.core.model.NodeInfoBase): Boolean =
        storeProvider?.invoke()
            ?.isFavorite(node.currentPageConfigPath, node.key) ?: false

    override fun toggleFavorite(node: com.projectkr.krscript.core.model.NodeInfoBase) {
        storeProvider?.invoke()?.toggle(node.currentPageConfigPath, node.key, node.title)
    }

    var confirmRequest by mutableStateOf<RunnableNode?>(null)
        private set
    var paramsAction by mutableStateOf<ActionNode?>(null)
        private set
    var activeSession by mutableStateOf<ScriptActions.Session?>(null)
        private set

    /** Params collected by [com.projectkr.shell.ui.dialogs.ActionParamsDialog]. */
    var lastDraft: Map<String, String> = emptyMap()

    override fun onRunnable(node: RunnableNode, params: Map<String, String>) {
        when {
            activeSession != null || confirmRequest != null || paramsAction != null -> return
            node.confirm -> {
                confirmRequest = node
                pendingParams = params
            }
            node is ActionNode && !node.params.isNullOrEmpty() && params.isEmpty() ->
                paramsAction = node
            else -> start(node, params)
        }
    }

    private var pendingParams: Map<String, String> = emptyMap()

    fun submitConfirmation() {
        val node = confirmRequest ?: return
        confirmRequest = null
        start(node, pendingParams)
    }

    fun dismissConfirmation() {
        confirmRequest = null
        pendingParams = emptyMap()
    }

    fun submitParams(action: ActionNode, values: Map<String, String>) {
        lastDraft = values
        paramsAction = null
        start(action, values)
    }

    fun dismissParams() {
        paramsAction = null
    }

    fun closeSession() {
        activeSession = null
    }

    private fun start(node: RunnableNode, params: Map<String, String>) {
        when (node.shell) {
            RunnableNode.shellModeBgTask, RunnableNode.shellModeHidden ->
                appContext?.let { runDetached(node, params, it) }
            else -> scope.launch(Dispatchers.IO) {
                val session = ScriptActions.stream(
                    node = node,
                    script = node.setState.orEmpty(),
                    params = params,
                )
                activeSession = session
            }
        }
    }

    /** bg-task / hidden runs keep going without the log dialog. */
    private fun runDetached(node: RunnableNode, params: Map<String, String>, appContext: Context) {
        scope.launch(Dispatchers.IO) {
            val session = ScriptActions.stream(
                node = node,
                script = node.setState.orEmpty(),
                params = params,
            )
            // Wait quietly for completion, then surface it as a notification.
            while (session.running) delay(500)
            com.projectkr.shell.runtime.BgTaskNotifications.notifyDone(
                appContext,
                node.title.ifEmpty { "后台任务" },
                success = session.exitCode == 0,
            )
        }
    }
}
