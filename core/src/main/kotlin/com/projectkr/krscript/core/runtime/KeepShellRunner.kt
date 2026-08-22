// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.runtime

import java.io.BufferedReader
import java.io.File
import java.util.concurrent.locks.ReentrantLock

/**
 * Persistent shell process (`su` or `sh`) with marker-delimited synchronous command
 * execution — a faithful port of the original KeepShell protocol.
 *
 * Commands are wrapped between `echo '|SH>>|'` and `echo '|<<SH|'` markers so that
 * output produced by the command itself is captured exactly, independent of exit codes.
 */
class KeepShellRunner(private val rootMode: Boolean = true) : ShellRunner {

    private var process: Process? = null
    private var writer: java.io.OutputStream? = null
    private var reader: BufferedReader? = null

    @Volatile
    private var idle = true
    val isIdle: Boolean get() = idle

    /** Whether this runner actually holds root (set by [createWithFallback]). */
    var rooted: Boolean = false
        private set

    private val lock = ReentrantLock()

    private val startTag = "|SH>>|"
    private val endTag = "|<<SH|"

    private fun ensureProcess() {
        if (process != null) return
        process = try {
            val builder = ProcessBuilder(if (rootMode) "su" else "sh")
            builder.redirectErrorStream(false)
            builder.start()
        } catch (ex: Exception) {
            null
        } ?: return
        writer = process!!.outputStream
        reader = process!!.inputStream.bufferedReader()
        // Drain stderr continuously; an undrained pipe can deadlock the shell
        // once its buffer fills (the original KeepShell did the same).
        val errStream = process!!.errorStream
        Thread {
            try {
                errStream.bufferedReader().forEachLine { /* discarded */ }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()
    }

    /** Destroys the underlying process; the next [execute] starts a fresh one. */
    fun tryExit() {
        try { writer?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        writer = null
        reader = null
        process = null
    }

    /**
     * Checks whether the current mode actually yields root privileges.
     */
    fun checkRoot(): Boolean {
        if (!rootMode) return false
        val result = execute(ROOT_CHECK_CMD).lowercase()
        val failed = result == "error" ||
            result.contains("permission denied") ||
            result.contains("not allowed") ||
            result == "not found"
        if (failed) {
            tryExit()
            return false
        }
        return result.contains("success")
    }

    override fun execute(cmd: String): String {
        lock.lockInterruptibly()
        try {
            ensureProcess()
            val out = writer ?: return "error"
            val rd = reader ?: return "error"

            idle = false
            out.write("\necho '$startTag'\n".toByteArray())
            out.write(cmd.toByteArray())
            out.write("\necho '$endTag'\n".toByteArray())
            out.flush()

            val cache = StringBuilder()
            var started = false
            while (true) {
                val line = rd.readLine() ?: break
                val endIdx = line.indexOf(endTag)
                val startIdx = line.indexOf(startTag)
                if (endIdx >= 0) {
                    if (started || startIdx >= 0) {
                        val from = if (startIdx >= 0) startIdx + startTag.length else 0
                        if (endIdx > from) cache.append(line.substring(from, endIdx))
                    }
                    break
                } else if (startIdx >= 0) {
                    cache.clear()
                    cache.append(line.substring(startIdx + startTag.length))
                    started = true
                } else if (started) {
                    cache.append(line).append('\n')
                }
            }
            return cache.toString().trim()
        } catch (ex: Exception) {
            tryExit()
            return "error"
        } finally {
            idle = true
            lock.unlock()
        }
    }

    companion object {
        private const val ROOT_CHECK_CMD =
            "if [[ \$(id -u 2>&1) == '0' ]] || [[ \$(\$UID) == '0' ]] || [[ \$(whoami 2>&1) == 'root' ]]; then\n" +
                "  echo 'success'\n" +
                "else\n" +
                "  exit 1\n" +
                "fi"

        /**
         * Creates a runner with an automatic sh → su fallback:
         * tries root first; when root is unavailable falls back to a plain shell.
         */
        fun createWithFallback(): KeepShellRunner {
            val root = KeepShellRunner(rootMode = true)
            if (root.checkRoot()) {
                root.rooted = true
                return root
            }
            root.tryExit()
            return KeepShellRunner(rootMode = false)
        }
    }
}
