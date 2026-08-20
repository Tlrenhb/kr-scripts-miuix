// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.config

/**
 * Shell 执行抽象，方便在 Compose 重写阶段替换旧的 ScriptEnvironmen。
 */
interface ShellRunner {
    fun execute(script: String): String
}
