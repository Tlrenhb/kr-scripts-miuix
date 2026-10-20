// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.power

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.projectkr.shell.core.shell.KeepShellPublic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/** 原版 DialogPower 的精确命令（pio strings.xml @ b90f2cc，FRAMEWORK_PARITY §5.9） */
private val POWER_ACTIONS: List<Pair<String, String>> = listOf(
    "重启" to "sync;svc power reboot || reboot;",
    "热重启" to "sync;am restart || busybox killall system_server;",
    "Recovery" to "sync;reboot recovery;",
    "Bootloader" to "sync;reboot bootloader;",
    "EDL (9008)" to "sync;reboot edl;",
    "关机" to "sync;svc power shutdown || reboot -p;",
)

/** 非 root 回退使用的独立 sh 实例 */
private const val SH_INSTANCE_KEY = "power_menu_sh"

/**
 * 电源菜单对话框（FRAMEWORK_PARITY §5.9，对齐原版 DialogPower）。
 *
 * OverlayDialog + TextButton 列表。点击后先关闭对话框，再在 IO 线程执行命令：
 * 优先 root（KeepShellPublic.doCmdSync，默认实例为 root 模式）；
 * root 不可用时回退非 root sh，仍失败则 Toast 提示。
 */
@Composable
fun PowerMenuDialog(show: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    OverlayDialog(
        show = show,
        title = "电源菜单",
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            POWER_ACTIONS.forEach { (label, command) ->
                TextButton(
                    text = label,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onDismiss()
                        scope.launch {
                            val success = withContext(Dispatchers.IO) {
                                // root 执行；失败回退非 root sh
                                val rootResult = try {
                                    KeepShellPublic.doCmdSync(command)
                                } catch (ignored: Exception) {
                                    "error"
                                }
                                if (rootResult != "error") {
                                    true
                                } else {
                                    try {
                                        KeepShellPublic.getInstance(SH_INSTANCE_KEY, false)
                                            .doCmdSync(command) != "error"
                                    } catch (ignored: Exception) {
                                        false
                                    }
                                }
                            }
                            if (!success) {
                                Toast.makeText(
                                    context,
                                    "命令执行失败（可能需要 ROOT 权限）",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                )
            }
            TextButton(
                text = "取消",
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss,
            )
        }
    }
}
