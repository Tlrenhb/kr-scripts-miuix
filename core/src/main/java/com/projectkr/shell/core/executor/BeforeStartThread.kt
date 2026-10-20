// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.executor

import android.content.Context
import com.projectkr.shell.core.shell.CheckRootStatus
import java.io.BufferedReader
import java.io.DataOutputStream
import java.util.HashMap

/**
 * Port of `pio/.../SplashActivity.kt`'s `BeforeStartThread`/`StreamReadThread`
 * @ b90f2cc (§3.6 of docs/FRAMEWORK_PARITY.md).
 *
 * Runs the `before_start_sh` script through the executor with the whole
 * kr-script.conf map exported as env vars (pass `config.getVariables()` as
 * [params]), tag `"pio-splash"`. Process is `su` when rooted else `sh`.
 * stdout+stderr stream line-by-line to [LogCallback.onLogOutput] (the original
 * renders a rolling ≤6-row window with `……` elision — rendering is the host's
 * job). [LogCallback.onExit] runs after `process.waitFor()`, and — matching the
 * original's soft-failure semantics — also on a null process or any exception:
 * before_start can never hard-block startup.
 */
class BeforeStartThread(
    private val context: Context,
    private val script: String,
    private val params: HashMap<String, String>?,
    private val logCallback: LogCallback,
) : Thread() {

    interface LogCallback {
        fun onLogOutput(line: String)
        fun onExit()
    }

    override fun run() {
        try {
            val process = if (CheckRootStatus.lastCheckResult) {
                com.projectkr.shell.core.shell.ShellExecutor.getSuperUserRuntime()
            } else {
                com.projectkr.shell.core.shell.ShellExecutor.getRuntime()
            }
            if (process != null) {
                val outputStream = DataOutputStream(process.outputStream)

                ScriptEnvironment.executeShell(context, outputStream, script, params, null, "pio-splash")

                StreamReadThread(process.inputStream.bufferedReader(), logCallback).start()
                StreamReadThread(process.errorStream.bufferedReader(), logCallback).start()

                process.waitFor()
                logCallback.onExit()
            } else {
                logCallback.onExit()
            }
        } catch (ex: Exception) {
            logCallback.onExit()
        }
    }

    private class StreamReadThread(private val reader: BufferedReader, private val logCallback: LogCallback) : Thread() {
        override fun run() {
            while (true) {
                val line = try {
                    reader.readLine()
                } catch (ex: Exception) {
                    break
                }
                if (line == null) {
                    break
                } else {
                    logCallback.onLogOutput(line)
                }
            }
        }
    }
}
