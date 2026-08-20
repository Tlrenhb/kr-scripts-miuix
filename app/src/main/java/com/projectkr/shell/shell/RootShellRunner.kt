// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.shell

import com.projectkr.shell.core.config.ShellRunner

/**
 * 轻量级 ShellRunner：优先用 sh 执行，失败时尝试 su -c。
 */
class RootShellRunner : ShellRunner {
    override fun execute(script: String): String {
        if (script.isBlank()) return ""
        val shResult = runProcess("sh", "-c", script)
        if (shResult.first) return shResult.second
        return runProcess("su", "-c", script).second
    }

    private fun runProcess(vararg command: String): Pair<Boolean, String> {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val success = process.waitFor() == 0
            success to output
        } catch (e: Exception) {
            false to ""
        }
    }
}
