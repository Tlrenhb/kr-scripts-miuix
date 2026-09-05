// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectkr.krscript.core.model.RunnableNode
import com.projectkr.shell.runtime.ScriptActions
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Streaming script log rendered in an OverlayDialog — the Compose counterpart of
 * the original DialogLogFragment.
 */
@Composable
fun LogDialog(
    session: ScriptActions.Session?,
    show: Boolean,
    onClose: () -> Unit,
) {
    // Retain the last session while the hide animation plays.
    var last by remember { mutableStateOf<ScriptActions.Session?>(null) }
    if (session != null) last = session
    val active = last ?: return

    // progress:[current/total] protocol (docs/Extra.md): -1 total = loading.
    var progressCurrent by remember { mutableIntStateOf(-2) }
    var progressTotal by remember { mutableIntStateOf(-2) }
    LaunchedEffect(active.lines.size) {
        for (i in (active.lines.size - 1).coerceAtLeast(0) downTo 0) {
            val m = Regex("progress:\\[(-?\\d+)/(\\d+)\\]").find(active.lines[i].text)
            if (m != null) {
                progressCurrent = m.groupValues[1].toInt()
                progressTotal = m.groupValues[2].toInt()
                return@LaunchedEffect
            }
        }
    }

    val context = LocalContext.current
    OverlayDialog(
        title = active.node.title,
        show = show,
        onDismissRequest = { if (!active.running) onClose() },
    ) {
        val scroll = rememberScrollState()
        LaunchedEffect(active.lines.size) {
            scroll.scrollTo(scroll.maxValue)
        }

        // progress:[current/total] drives an indicator; equal values hide it,
        // current = -1 shows an indeterminate animation (docs/Extra.md).
        val showDeterminate = progressTotal > 0 && progressCurrent in 0 until progressTotal
        val showLoading = progressCurrent == -1
        if (showDeterminate || showLoading) {
            top.yukonga.miuix.kmp.basic.LinearProgressIndicator(
                progress = if (showDeterminate) progressCurrent.toFloat() / progressTotal else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 420.dp)
                .verticalScroll(scroll),
        ) {
            Text(
                text = active.lines.joinToString("\n") { it.text },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(modifier = Modifier.padding(top = 12.dp)) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            TextButton(
                text = "复制",
                onClick = {
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            "log",
                            active.lines.joinToString("\n") { it.text },
                        ),
                    )
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
            )
            if (active.running && active.node.interruptable) {
                TextButton(
                    text = "停止",
                    onClick = { active.interrupt() },
                    modifier = Modifier.weight(1f),
                )
            }
            if (!active.running) {
                TextButton(
                    text = "关闭" + exitSuffix(active),
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun exitSuffix(session: ScriptActions.Session): String =
    session.exitCode?.let { if (it == 0) "" else " (退出码 $it)" } ?: ""

/**
 * Confirmation dialog for `confirm="true"` nodes, showing the warning text.
 */
@Composable
fun ConfirmDialog(
    node: RunnableNode,
    show: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        title = node.title,
        summary = node.warning.ifEmpty { node.desc }.ifEmpty { "确定执行该操作吗？" },
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Row {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "确定",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
