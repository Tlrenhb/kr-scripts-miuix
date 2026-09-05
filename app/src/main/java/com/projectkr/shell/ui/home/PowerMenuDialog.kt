// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

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
 * Page-scoped confirmation for the power action selected from the app-bar
 * popup. Keeping the popup and confirmation separate avoids stacked overlays
 * fighting for focus or predictive back handling.
 */
@Composable
fun PowerActionConfirmDialog(
    action: PowerAction?,
    onStartAction: (PowerAction) -> Unit,
    onDismiss: () -> Unit,
) {
    // Keep the selected value while the OverlayDialog exit animation plays.
    var retainedAction by remember { mutableStateOf<PowerAction?>(null) }
    action?.let { retainedAction = it }
    val confirmedAction = retainedAction ?: return

    OverlayDialog(
        title = confirmedAction.label,
        summary = "确定执行「${confirmedAction.label}」吗？",
        show = action != null,
        onDismissRequest = onDismiss,
        onDismissFinished = {
            if (action == null) retainedAction = null
        },
    ) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "确定",
                onClick = {
                    onDismiss()
                    onStartAction(confirmedAction)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
