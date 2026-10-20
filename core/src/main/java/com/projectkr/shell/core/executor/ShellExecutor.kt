// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.executor

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.projectkr.shell.core.model.RunnableNode
import java.io.DataOutputStream
import java.util.HashMap

/**
 * Port of `krscript/src/main/java/com/omarea/krscript/executor/ShellExecutor.java` @ b90f2cc
 * (§3.3/§3.5 of docs/FRAMEWORK_PARITY.md).
 *
 * One [ShellExecutor] instance executes exactly one action (single-use `started`
 * flag). Each execution spawns a FRESH `su` (rooted) or `sh` process, streams
 * stdout/stderr/exit through [ShellHandlerBase] via [SimpleShellWatcher], and sends
 * `EVENT_START` twice (`"shell@android:\n"`, then the script text) before
 * [ShellHandlerBase.onStart] receives the force-stop runnable.
 *
 * Interrupt (§3.5): the force-stop runnable exists iff
 * `node.interruptable || node.shell == RunnableNode.shellModeBgTask`. It runs
 * `kill -s 1 \`pgrep -f <sessionTag>\`` (SIGHUP to every process whose cmdline
 * contains the tag) through the framework's root shell, closes all three process
 * streams, then `destroyForcibly()` (API ≥ 26) / `destroy()`.
 */
class ShellExecutor {
    private var started = false
    private val sessionTag = "pio_" + System.currentTimeMillis()

    private fun killProcess(context: Context) {
        ScriptEnvironment.executeResultRoot(
            context,
            String.format("kill -s 1 `pgrep -f %s`", sessionTag),
            null
        )
        // KeepShellPublic.doCmdSync(String.format("kill -s 1 `pgrep -f %s`", sessionTag));
    }

    /**
     * 执行脚本
     */
    fun execute(
        context: Context,
        nodeInfo: RunnableNode,
        cmds: String,
        onExit: Runnable?,
        params: HashMap<String, String>?,
        shellHandlerBase: ShellHandlerBase,
    ): Process? {
        if (started) {
            return null
        }

        val process = ScriptEnvironment.getRuntime()
        if (process == null) {
            // 原版在主线程直接 Toast；此处切到主线程保证后台调用也安全
            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(context, "未能启动命令行进程", Toast.LENGTH_SHORT).show()
                } catch (ignored: Exception) {
                }
            }
            onExit?.run()
        } else {
            val forceStopRunnable: Runnable? =
                if (nodeInfo.interruptable || nodeInfo.shell == RunnableNode.shellModeBgTask) {
                    Runnable {
                        /*
                        // 没啥用，这个pid和在shell创建的子进程不是父子关系，杀死此进程对shell里创建的进程毫无影响
                        // （原版被注释掉的 pid 反射代码，保留注释状态）
                        */
                        killProcess(context)

                        try {
                            process.inputStream.close()
                        } catch (ignored: Exception) {
                        }
                        try {
                            process.outputStream.close()
                        } catch (ignored: Exception) {
                        }
                        try {
                            process.errorStream.close()
                        } catch (ignored: Exception) {
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                process.destroyForcibly()
                            } catch (ex: Exception) {
                                Log.e("KrScriptError", "" + ex.message)
                            }
                        } else {
                            try {
                                process.destroy()
                            } catch (ex: Exception) {
                                Log.e("KrScriptError", "" + ex.message)
                            }
                        }
                    }
                } else {
                    null
                }
            SimpleShellWatcher().setHandler(context, process, shellHandlerBase, onExit)

            val outputStream = process.outputStream
            val dataOutputStream = DataOutputStream(outputStream)
            try {
                shellHandlerBase.sendMessage(shellHandlerBase.obtainMessage(ShellHandlerBase.EVENT_START, "shell@android:\n"))
                shellHandlerBase.sendMessage(shellHandlerBase.obtainMessage(ShellHandlerBase.EVENT_START, "$cmds\n\n"))
                shellHandlerBase.onStart(forceStopRunnable)
                dataOutputStream.writeBytes("sleep 0.2;\n")

                ScriptEnvironment.executeShell(context, dataOutputStream, cmds, params, nodeInfo, sessionTag)
            } catch (ex: Exception) {
                process.destroy()
            }
            started = true
        }
        return process
    }
}
