// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.runtime

/**
 * Executes a shell command synchronously and returns its captured output
 * (trimmed). Implementations return the literal string `"error"` on failure,
 * matching the original KrScript convention.
 */
fun interface ShellRunner {
    fun execute(cmd: String): String
}
