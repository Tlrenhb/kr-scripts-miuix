// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.shell

import com.projectkr.shell.core.config.ShellRunner

/**
 * 轻量级 ShellRunner，先用 sh 执行；后续可替换为 KeepShell / ScriptEnvironmen。
 */
class RootShellRunner : ShellRunner {
    override fun execute(script: String): String {
        if (script.isBlank()) return ""
        return try {
            val process = ProcessBuilder("sh", "-c", script)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }
    }
}
