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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
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
    session: ScriptActions.Session,
    show: Boolean,
    onClose: () -> Unit,
) {
    OverlayDialog(
        title = session.node.title,
        show = show,
        onDismissRequest = { if (!session.running) onClose() },
    ) {
        val scroll = rememberScrollState()
        LaunchedEffect(session.lines.size) {
            scroll.scrollTo(scroll.maxValue)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 420.dp)
                .verticalScroll(scroll),
        ) {
            Text(
                text = session.lines.joinToString("\n") { it.text },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(modifier = Modifier.padding(top = 12.dp)) {
            if (session.running && session.node.interruptable) {
                TextButton(
                    text = "停止",
                    onClick = { session.interrupt() },
                    modifier = Modifier.weight(1f),
                )
            }
            if (!session.running) {
                TextButton(
                    text = "关闭" + exitSuffix(session),
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
        summary = node.warning.ifEmpty { "确定执行该操作吗？" },
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
