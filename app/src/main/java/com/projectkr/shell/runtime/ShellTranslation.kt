// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.runtime

import android.content.Context

/**
 * Resolves `@string:name` / `@dimen:name` output rows against app resources —
 * port of the original common/shell/ShellTranslation.kt.
 */
object ShellTranslation {

    private val colonForm = Regex("^@(string|dimen):[_a-z]+.*", RegexOption.IGNORE_CASE)
    private val slashForm = Regex("^@(string|dimen)/[_a-z]+.*", RegexOption.IGNORE_CASE)

    fun resolveRow(context: Context, originRow: String): String {
        val separator = when {
            colonForm.matches(originRow) -> ':'
            slashForm.matches(originRow) -> '/'
            else -> return originRow
        }
        val row = originRow.trim()
        val type = row.substring(1, row.indexOf(separator)).lowercase()
        val name = row.substring(row.indexOf(separator) + 1)
        val id = context.resources.getIdentifier(name, type, context.packageName)
        return try {
            when (type) {
                "string" -> context.resources.getString(id)
                "dimen" -> context.resources.getDimension(id).toString()
                else -> originRow
            }
        } catch (ex: Exception) {
            // Fallback: scripts may embed a default inside [(" … )].
            if (row.contains("[(") && row.contains(")]")) {
                row.substring(row.indexOf("[(") + 2, row.indexOf(")]"))
            } else {
                originRow
            }
        }
    }

    fun resolveRows(context: Context, rows: List<String>): String =
        rows.joinToString("\n") { resolveRow(context, it) }
}
