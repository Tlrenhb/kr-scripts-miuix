// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.shell

import com.projectkr.shell.core.config.ShellRunner
import java.io.BufferedReader
import java.io.DataOutputStream

/**
 * 简易常驻 Shell：保持一个 su/sh 进程，通过标记行分割每次命令输出。
 */
class KeepShellRunner : ShellRunner {

    private var process: Process? = null
    private var writer: DataOutputStream? = null
    private var reader: BufferedReader? = null

    @Synchronized
    override fun execute(script: String): String {
        if (script.isBlank()) return ""
        val proc = ensureStarted()
        if (proc == null) {
            return RootShellRunner().execute(script)
        }
        return try {
            val start = "___MIUIX_START___"
            val end = "___MIUIX_END___"
            writer?.writeBytes("echo $start\n")
            writer?.writeBytes(script.trim() + "\n")
            writer?.writeBytes("echo $end\n")
            writer?.flush()

            val output = StringBuilder()
            var line: String?
            while (true) {
                line = reader?.readLine() ?: break
                if (line == end) break
                if (line != start) {
                    output.appendLine(line)
                }
            }
            output.toString().trim()
        } catch (e: Exception) {
            destroy()
            RootShellRunner().execute(script)
        }
    }

    private fun ensureStarted(): Process? {
        if (process?.isAlive == true) return process
        destroy()
        return try {
            val builder = if (suAvailable()) {
                ProcessBuilder("su")
            } else {
                ProcessBuilder("sh")
            }
            builder.redirectErrorStream(true)
            val p = builder.start()
            process = p
            writer = DataOutputStream(p.outputStream)
            reader = p.inputStream.bufferedReader()
            p
        } catch (e: Exception) {
            null
        }
    }

    private fun suAvailable(): Boolean {
        return try {
            val p = ProcessBuilder("su", "-c", "true").start()
            val ok = p.waitFor() == 0
            p.destroy()
            ok
        } catch (e: Exception) {
            false
        }
    }

    private fun destroy() {
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { process?.destroy() }
        writer = null
        reader = null
        process = null
    }
}
