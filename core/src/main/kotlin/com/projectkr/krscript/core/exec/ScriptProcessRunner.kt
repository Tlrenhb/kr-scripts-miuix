// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.exec

import com.projectkr.krscript.core.model.NodeInfoBase
import com.projectkr.krscript.core.runtime.AssetExtractor
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Runs a script in a dedicated process with live output streaming — the
 * foreground/bg-task execution path of the original ShellExecutor.
 *
 * The command sequence written to the new process mirrors the original contract:
 * `export KEY='value'` lines for every parameter (plus PAGE_* context),
 * then `<executor> "<cached-script>" "<tag>"`, then exit. [interrupt] kills the
 * whole session by tag via `pgrep -f`.
 */
class ScriptProcessRunner(
    private val environment: ScriptEnvironment,
    private val extractor: AssetExtractor,
    private val rootMode: Boolean,
) {

    /**
     * Starts [script]. Returns null when the process could not be spawned;
     * otherwise callbacks receive output until exit.
     *
     * @param onLine invoked per output line; [Boolean] marks stderr
     * @param onExit invoked once the process ends
     */
    fun execute(
        script: String,
        node: NodeInfoBase?,
        params: Map<String, String>,
        tag: String,
        onLine: (String, Boolean) -> Unit = { _, _ -> },
        onExit: (Int) -> Unit = {},
    ): Process? {
        val invocation = environment.executorCommand(script, tag)
        if (invocation.isEmpty()) return null

        // Caller params first; PAGE_* engine keys are written after so the
        // engine contract always wins (original ScriptEnvironmen ordering).
        val merged = LinkedHashMap<String, String>()
        merged.putAll(params)
        if (node != null && node.currentPageConfigPath.isNotEmpty()) {
            val parentDir = node.pageConfigDir
            val currentPath = node.currentPageConfigPath
            merged["PAGE_CONFIG_DIR"] = parentDir
            merged["PAGE_CONFIG_FILE"] = currentPath
            if (currentPath.startsWith(AssetExtractor.ASSETS_PREFIX)) {
                merged["PAGE_WORK_DIR"] = extractor.getExtractPath(parentDir)
                merged["PAGE_WORK_FILE"] = extractor.getExtractPath(currentPath)
            } else {
                merged["PAGE_WORK_DIR"] = parentDir
                merged["PAGE_WORK_FILE"] = currentPath
            }
        } else {
            merged["PAGE_CONFIG_DIR"] = ""
            merged["PAGE_CONFIG_FILE"] = ""
            merged["PAGE_WORK_DIR"] = ""
            merged["PAGE_WORK_FILE"] = ""
        }
        val process = try {
            ProcessBuilder(if (rootMode) "su" else "sh").start()
        } catch (ex: Exception) {
            return null
        }

        val out = process.outputStream
        Thread {
            try {
                val sb = StringBuilder()
                for ((key, value) in merged) {
                    sb.append("export ").append(key).append("='")
                        .append(value.replace("'", "'\\''")).append("'\n")
                }
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
                out.write(invocation.toByteArray(Charsets.UTF_8))
                out.write("\n\nsleep 0.2;\nexit\nexit\n".toByteArray(Charsets.UTF_8))
                out.flush()
            } catch (_: Exception) {
            } finally {
                try { out.close() } catch (_: Exception) {}
            }
        }.start()

        // stdout and stderr drain concurrently (original SimpleShellWatcher):
        // a stderr burst larger than the pipe buffer must never deadlock stdout.
        // The exit callback fires only after BOTH streams reach EOF and the
        // process has terminated, so events are complete before exit.
        val stdoutDone = java.util.concurrent.CountDownLatch(1)
        val stderrDone = java.util.concurrent.CountDownLatch(1)

        Thread {
            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    onLine(line, false)
                }
            } catch (_: Exception) {
            } finally {
                stdoutDone.countDown()
            }
        }.start()

        Thread {
            val errReader = BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8))
            try {
                while (true) {
                    val line = errReader.readLine() ?: break
                    onLine(line, true)
                }
            } catch (_: Exception) {
            } finally {
                stderrDone.countDown()
            }
        }.start()

        Thread {
            try {
                stdoutDone.await(60, java.util.concurrent.TimeUnit.SECONDS)
                stderrDone.await(60, java.util.concurrent.TimeUnit.SECONDS)
                val code = try {
                    process.waitFor()
                } catch (ex: InterruptedException) {
                    -1
                }
                onExit(code)
            } catch (_: Exception) {
                onExit(-1)
            }
        }.start()

        return process
    }

    companion object {
        /** Builds the interrupt command executed through the persistent shell. */
        fun interruptCommand(tag: String): String =
            "kill -s 1 `pgrep -f $tag`"
    }
}
