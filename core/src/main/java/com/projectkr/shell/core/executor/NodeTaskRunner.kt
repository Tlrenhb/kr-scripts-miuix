// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.executor

import android.content.Context
import com.projectkr.shell.core.model.AutoRunTask
import com.projectkr.shell.core.model.GroupNode
import com.projectkr.shell.core.model.NodeInfoBase
import com.projectkr.shell.core.model.RunnableNode
import java.util.HashMap

/**
 * Execution-side port of `ActionListFragment.actionExecute`'s shell-mode dispatch
 * (§3.3 step 1 of docs/FRAMEWORK_PARITY.md) and the auto-run entry helpers (§3.6).
 *
 * Dispatch by `RunnableNode.shell`:
 * - `"bg-task"` → [BgTaskThread.startTask] (notification task)
 * - `"hidden"` → guarded [HiddenTaskThread.startTaskGuarded] (singleton; busy →
 *   [onHiddenTaskBusy], the host should toast `kr_hidden_task_running`)
 * - else (`"default"`) → [ShellExecutor.execute] with the caller-supplied
 *   [defaultModeHandler] (the streaming log UI is the host's; the original shows
 *   `DialogLogFragment` here). A null handler for default mode throws —
 *   the original unconditionally opens the log dialog for this mode.
 */
object NodeTaskRunner {

    /**
     * @return the [Process] for default mode, null for bg/hidden modes (matching the
     *         original, where `startTask` does not expose the process)
     */
    fun execute(
        context: Context,
        nodeInfo: RunnableNode,
        script: String,
        params: HashMap<String, String>?,
        onExit: Runnable,
        onDismiss: Runnable,
        defaultModeHandler: ShellHandlerBase? = null,
        bgCustomizer: BgTaskThread.BgTaskNotificationCustomizer? = null,
        onBgTaskStarted: (() -> Unit)? = null,
        onHiddenTaskBusy: (() -> Unit)? = null,
    ): Process? {
        return when (nodeInfo.shell) {
            RunnableNode.shellModeBgTask -> {
                BgTaskThread.startTask(context, script, params, nodeInfo, onExit, onDismiss, bgCustomizer, onBgTaskStarted)
                null
            }

            RunnableNode.shellModeHidden -> {
                if (!HiddenTaskThread.startTaskGuarded(context, script, params, nodeInfo, onExit, onDismiss)) {
                    onHiddenTaskBusy?.invoke()
                }
                null
            }

            else -> {
                requireNotNull(defaultModeHandler) {
                    "default shell mode requires a ShellHandlerBase (the host renders the streaming log UI)"
                }
                ShellExecutor().execute(context, nodeInfo, script, onExit, params, defaultModeHandler)
            }
        }
    }

    /**
     * 在节点列表中按 key 递归查找（原版为 ListItemGroup.triggerActionByKey 的递归遍历）。
     * 仅 [GroupNode] 会下钻（对应原版 group 的子项容器）。
     */
    fun findNodeByKey(nodes: List<NodeInfoBase>, key: String): NodeInfoBase? {
        for (node in nodes) {
            if (node.key == key) {
                return node
            }
            if (node is GroupNode) {
                val hit = findNodeByKey(node.children, key)
                if (hit != null) {
                    return hit
                }
            }
        }
        return null
    }

    /**
     * AutoRunTask 处理（§3.6）：首个页面加载完成后，按 [AutoRunTask.key] 找到目标
     * [RunnableNode] 并交给 [trigger]（宿主应在此走自己的“点击行”管线，以保留
     * confirm/warning/lock 门控），把触发结果回传给 [AutoRunTask.onCompleted]；
     * 找不到 → `onCompleted(false)`（宿主 toast `kr_auto_run_item_losted`）。
     * key 为空时不做任何事（与原版一致）。
     */
    fun triggerAutoRunTask(
        nodes: List<NodeInfoBase>,
        autoRunTask: AutoRunTask?,
        trigger: (RunnableNode) -> Boolean,
    ) {
        autoRunTask?.run {
            val key = key
            if (!key.isNullOrEmpty()) {
                val node = findNodeByKey(nodes, key)
                onCompleted(if (node is RunnableNode) trigger(node) else false)
            }
        }
    }
}
