// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/** Power operations from the original DialogPower. */
/** Commands are the exact strings from the original strings.xml power_*_cmd. */
enum class PowerAction(val label: String, val command: String) {
    REBOOT("重启", "sync;svc power reboot || reboot;"),
    HOT_REBOOT("热重启", "sync;am restart || busybox killall system_server;"),
    RECOVERY("重启到 Recovery", "sync;reboot recovery;"),
    FASTBOOT("重启到 Bootloader", "sync;reboot bootloader;"),
    EMERGENCY("紧急模式 (EDL)", "sync;reboot edl;"),
    SHUTDOWN("关机", "sync;svc power shutdown || reboot -p;"),
}

/**
 * Power menu dialog — a dedicated streaming session per action so the user can
 * watch the (usually silent) command and interrupt if needed.
 */
@Composable
fun PowerMenuDialog(
    show: Boolean,
    rooted: Boolean,
    onStartAction: (PowerAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmAction by remember { mutableStateOf<PowerAction?>(null) }

    OverlayDialog(
        title = "电源菜单",
        summary = if (rooted) null else "需要 ROOT 权限",
        show = show,
        onDismissRequest = onDismiss,
    ) {
        PowerAction.entries.forEach { action ->
            TextButton(
                text = action.label,
                onClick = { confirmAction = action },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }
    }

    // Retain the action while the hide animation plays; `show` tracks state.
    var lastAction by remember { mutableStateOf<PowerAction?>(null) }
    confirmAction?.let { lastAction = it }
    lastAction?.let { action ->
        OverlayDialog(
            title = action.label,
            summary = "确定执行「${action.label}」吗？",
            show = confirmAction != null,
            onDismissRequest = { confirmAction = null },
        ) {
            androidx.compose.foundation.layout.Row {
                TextButton(
                    text = "取消",
                    onClick = { confirmAction = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "确定",
                    onClick = {
                        confirmAction = null
                        onDismiss()
                        onStartAction(action)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
