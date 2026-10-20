// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.shell

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Port of the non-UI half of `pio/.../permissions/CheckRootStatus.kt` @ b90f2cc.
 *
 * Runs the real root check ([KeepShellPublic.checkRoot]) on a background thread,
 * caches the result in [lastCheckResult], and enforces the original 15 s `su`
 * watchdog: on timeout the wedged shell is killed via [KeepShellPublic.tryExit] and
 * [onTimeout] fires instead. All UI (retry/exit/skip dialogs) is left to the host —
 * the callbacks fire on the main thread.
 *
 * @param onResult called with the check result (`false` = root denied; the original
 *                 shows `error_root` with Retry/Exit and, unless `force_root`, Skip)
 * @param onTimeout called when the watchdog fires (the original shows
 *                  `error_su_timeout` with Retry/Exit)
 */
class CheckRootStatus(
    private val onResult: ((granted: Boolean) -> Unit)? = null,
    private val onTimeout: (() -> Unit)? = null,
) {
    private val myHandler: Handler = Handler(Looper.getMainLooper())
    private var thread: Thread? = null

    fun forceGetRoot() {
        if (lastCheckResult) {
            myHandler.post { onResult?.invoke(true) }
        } else {
            // 与原版一致：completed 是每次调用捕获的局部量
            var completed = false
            val t = Thread {
                rootStatus = KeepShellPublic.checkRoot()
                if (completed) {
                    return@Thread
                }

                completed = true

                myHandler.post {
                    if (lastCheckResult) {
                        onResult?.invoke(true)
                    } else {
                        KeepShellPublic.tryExit()
                        onResult?.invoke(false)
                    }
                }
            }
            thread = t
            t.start()

            // 15 秒 su 超时看门狗
            Thread {
                try {
                    Thread.sleep(1000 * 15)
                } catch (ignored: InterruptedException) {
                }

                if (!completed) {
                    // 原版不置 completed（超时后检查线程仍可能回补结果，导致两个弹窗并存）；
                    // 此处置位保证超时后只走 onTimeout
                    completed = true
                    KeepShellPublic.tryExit()
                    myHandler.post {
                        onTimeout?.invoke()
                    }
                }
            }.start()
        }
    }

    /** 中断进行中的检查线程（原版的 Retry 路径会 interrupt 旧线程后重新 forceGetRoot） */
    fun interrupt() {
        val t = thread
        if (t != null && t.isAlive && !t.isInterrupted) {
            t.interrupt()
            thread = null
        }
    }

    companion object {
        @Volatile
        private var rootStatus = false

        /** 最后的ROOT检测结果 */
        val lastCheckResult: Boolean
            get() = rootStatus

        private fun checkPermission(context: Context, permission: String): Boolean =
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

        fun grantPermission(context: Context) {
            val cmds = StringBuilder()
            /*
            // 必需的权限（原版即被注释掉，保留注释状态）
            val requiredPermission = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            ...
            */

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!checkPermission(context, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) {
                    cmds.append("dumpsys deviceidle whitelist +${context.packageName};\n")
                }
            }
            KeepShellPublic.doCmdSync(cmds.toString())
        }
    }
}
