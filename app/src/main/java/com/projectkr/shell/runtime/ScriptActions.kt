// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.runtime

import com.projectkr.krscript.core.exec.ScriptProcessRunner
import com.projectkr.krscript.core.model.NodeInfoBase
import com.projectkr.krscript.core.model.RunnableNode
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Facade over script execution for the UI layer.
 *
 * - [eval] runs quick get/set scripts through the persistent shell.
 * - [stream] runs user actions in a dedicated process with live log output and
 *   tag-based interruption (original bg-task/default behavior).
 */
object ScriptActions {

    data class LogLine(val text: String, val isError: Boolean)

    class Session(
        val tag: String,
        val node: RunnableNode,
        val script: String,
    ) {
        /** Bounded tail buffer rendered by [LogDialog]. */
        val lines = mutableStateListOf<LogLine>()
        var truncated by mutableStateOf(false)
            private set
        var running by mutableStateOf(true)
        var exitCode by mutableStateOf<Int?>(null)

        /**
         * Keep the newest output: for real scripts, the final error/summary is
         * more actionable than the earliest startup lines once the cap is hit.
         */
        fun appendLine(text: String, isError: Boolean) {
            if (lines.size >= MAX_LINES) {
                lines.removeAt(0)
                truncated = true
            }
            lines.add(LogLine(text, isError))
        }

        fun interrupt() {
            KrScriptRuntime.shell.execute(ScriptProcessRunner.interruptCommand(tag))
        }
    }

    /** Quick synchronous script run (switch/picker set, lock checks…). */
    fun eval(script: String?, node: NodeInfoBase?): String {
        if (!KrScriptRuntime.isReady) return ""
        val raw = KrScriptRuntime.scriptEnv.executeResult(script, node)
        val ctx = KrScriptRuntime.appContext ?: return raw
        // @string/@dimen rows localize here exactly as the original did.
        return raw.split("\n").joinToString("\n") { ShellTranslation.resolveRow(ctx, it) }
    }

    /**
     * Starts [script] with [params] in a dedicated streaming process.
     * Must be invoked from a background dispatcher.
     */
    fun stream(node: RunnableNode, script: String, params: Map<String, String> = emptyMap()): Session {
        val session = Session(
            tag = "pio_" + System.currentTimeMillis(),
            node = node,
            script = script,
        )
        if (!KrScriptRuntime.isReady) {
            session.appendLine("脚本引擎尚未初始化完成，请稍后重试", true)
            session.running = false
            session.exitCode = -1
            return session
        }
        val runner = ScriptProcessRunner(
            environment = KrScriptRuntime.scriptEnv,
            extractor = KrScriptRuntime.extractor,
            rootMode = KrScriptRuntime.rooted,
        )
        val process = runner.execute(
            script = script,
            node = node,
            params = params,
            tag = session.tag,
            onLine = { line, isErr ->
                val translated = KrScriptRuntime.appContext?.let {
                    ShellTranslation.resolveRow(it, line)
                } ?: line
                session.appendLine(translated, isErr)
            },
            onExit = { code ->
                session.exitCode = code
                session.running = false
            },
        )
        if (process == null) {
            session.appendLine("未能启动命令行进程", true)
            session.running = false
            session.exitCode = -1
        }
        return session
    }

    private const val MAX_LINES = 5000
}
