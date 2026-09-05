// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectkr.krscript.core.model.RunnableNode
import com.projectkr.shell.runtime.ScriptActions
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val progressPattern = Regex("progress:\\[(-?\\d+)/(\\d+)\\]")

/**
 * Streaming script log rendered in an OverlayDialog — the Compose counterpart of
 * the original DialogLogFragment. It keeps the newest bounded output, makes it
 * selectable/copyable, and distinguishes stderr without changing script output.
 */
@Composable
fun LogDialog(
    session: ScriptActions.Session?,
    show: Boolean,
    onClose: () -> Unit,
) {
    // Retain the last session while the exit animation plays.
    var last by remember { mutableStateOf<ScriptActions.Session?>(null) }
    if (session != null) last = session
    val active = last ?: return

    // A different script session must not inherit the previous progress state.
    var progressCurrent by remember(active.tag) { mutableIntStateOf(-2) }
    var progressTotal by remember(active.tag) { mutableIntStateOf(-2) }
    LaunchedEffect(active.tag, active.lines.size) {
        for (i in (active.lines.size - 1).coerceAtLeast(0) downTo 0) {
            val match = progressPattern.find(active.lines[i].text) ?: continue
            progressCurrent = match.groupValues[1].toInt()
            progressTotal = match.groupValues[2].toInt()
            return@LaunchedEffect
        }
    }

    val normalColor = MiuixTheme.colorScheme.onBackground
    val errorColor = MiuixTheme.colorScheme.error
    val logText = remember(active.tag, active.lines.size, errorColor) {
        buildAnnotatedString {
            active.lines.forEachIndexed { index, line ->
                if (index > 0) append('\n')
                if (line.isError) {
                    pushStyle(SpanStyle(color = errorColor))
                    append(line.text)
                    pop()
                } else {
                    append(line.text)
                }
            }
        }
    }
    val statusText = when {
        active.running -> "正在执行…"
        active.exitCode == 0 -> "执行完成"
        active.exitCode == null -> "执行已结束"
        else -> "执行失败 · 退出码 ${active.exitCode}"
    }
    val statusColor = if (active.running || active.exitCode == 0) {
        MiuixTheme.colorScheme.primary
    } else {
        errorColor
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    OverlayDialog(
        title = active.node.title.ifEmpty { "脚本日志" },
        show = show,
        onDismissRequest = { if (!active.running) onClose() },
    ) {
        val scroll = rememberScrollState()
        LaunchedEffect(active.tag, active.lines.size) {
            scroll.scrollTo(scroll.maxValue)
        }

        Text(
            text = statusText,
            style = MiuixTheme.textStyles.footnote1,
            color = statusColor,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (active.truncated) {
            Text(
                text = "已保留最近 5000 行输出，较早内容已省略",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // progress:[current/total] drives a determinate/indeterminate indicator.
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
            SelectionContainer {
                Text(
                    text = logText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = normalColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(modifier = Modifier.padding(top = 12.dp)) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            TextButton(
                text = "复制",
                onClick = {
                    clipboard?.setPrimaryClip(ClipData.newPlainText("log", logText.text))
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

/** Confirmation dialog for `confirm="true"` nodes. */
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
