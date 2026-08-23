// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.projectkr.krscript.core.model.ActionNode
import android.content.Context
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
    /** Fired when a completed run asks for a page reload (reload-page / updateBlocks). */
    val onReloadRequest: () -> Unit = {},
    /** Fired when a completed run declares auto-finish (close the page). */
    val onAutoFinish: () -> Unit = {},
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
        if (activeSession != null || confirmRequest != null || paramsAction != null) return

        // SDK gates (original ListItemClickable help dialogs, toasted here).
        val sdk = android.os.Build.VERSION.SDK_INT
        if (node.minSdkVersion > 0 && sdk < node.minSdkVersion) {
            toast("需要 Android ${node.minSdkVersion} 及以上版本")
            return
        }
        if (node.maxSdkVersion in 1..999 && sdk > node.maxSdkVersion) {
            toast("仅支持 Android ${node.maxSdkVersion} 及以下版本")
            return
        }

        // lock / lock-state gate: evaluated through the script engine
        // (original nodeUnlocked: unlock/unlocked/false/0 releases the node).
        if (node.locked || node.lockShell.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val out = if (node.lockShell.isEmpty()) "" else {
                    ScriptActions.eval(node.lockShell, node).trim()
                }
                val lower = out.lowercase()
                val unlocked = lower == "unlock" || lower == "unlocked" ||
                    lower == "false" || lower == "0"
                val lockMsg = when {
                    node.lockShell.isEmpty() -> "该功能已锁定"
                    unlocked -> null
                    else -> out.ifEmpty { "该功能已锁定" }
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (lockMsg != null) {
                        toast(lockMsg)
                    } else {
                        dispatch(node, params)
                    }
                }
            }
            return
        }

        dispatch(node, params)
    }

    private fun toast(msg: String) {
        appContext?.let {
            android.widget.Toast.makeText(it, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun dispatch(node: RunnableNode, params: Map<String, String>) {
        val needsConfirm = node.confirm || node.warning.isNotEmpty()
        when {
            needsConfirm -> {
                pendingParams = params
                confirmRequest = node
            }
            node is ActionNode && !node.params.isNullOrEmpty() && params.isEmpty() ->
                paramsAction = node
            else -> start(node, params)
        }
    }

    /** Runs after any session finishes; honors the original completion flags. */
    fun onSessionCompleted(session: com.projectkr.shell.runtime.ScriptActions.Session) {
        val node = session.node
        if (node.autoFinish) onAutoFinish()
        if (node.reloadPage || !node.updateBlocks.isNullOrEmpty()) {
            // updateBlocks originally refreshed only the named blocks; a full
            // page reload is the compatible superset for this rewrite.
            onReloadRequest()
        }
        // hidden mode surfaces stderr through a toast instead of a notification.
        if (node.shell == RunnableNode.shellModeHidden) {
            val errors = session.lines.filter { it.isError }
            if (errors.isNotEmpty() || session.exitCode != 0) {
                appContext?.let { ctx ->
                    android.widget.Toast.makeText(
                        ctx,
                        errors.takeLast(3).joinToString("\n") { it.text }
                            .ifEmpty { "后台脚本执行失败" },
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
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
            while (session.running) delay(500)
            // Original HiddenTaskThread never posted notifications — it toasted
            // collected errors only when the run failed.
            if (node.shell == RunnableNode.shellModeBgTask) {
                com.projectkr.shell.runtime.BgTaskNotifications.notifyDone(
                    appContext,
                    node.title.ifEmpty { "后台任务" },
                    success = session.exitCode == 0,
                )
            } else {
                val errs = session.lines.filter { it.isError }.takeLast(3)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (errs.isNotEmpty() || session.exitCode != 0) {
                        android.widget.Toast.makeText(
                            appContext,
                            errs.joinToString("\n") { it.text }.ifEmpty { "后台脚本执行失败" },
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }
}
