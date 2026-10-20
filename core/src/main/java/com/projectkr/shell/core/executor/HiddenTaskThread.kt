// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.executor

import android.content.Context
import android.text.SpannableString
import android.widget.Toast
import com.projectkr.shell.core.model.RunnableNode
import java.util.HashMap

/**
 * Port of `krscript/src/main/java/com/omarea/krscript/HiddenTaskThread.kt` @ b90f2cc
 * (§3.4 hidden-task mode of docs/FRAMEWORK_PARITY.md).
 *
 * Zero UI while running; stderr rows are collected and toasted once at exit.
 *
 * The original keeps the `hiddenTaskRunning` singleton guard in
 * `ActionListFragment`; this port moves it into the companion
 * ([hiddenTaskRunning] / [startTaskGuarded]) so the singleton semantics hold
 * app-wide. [startTask] itself stays unguarded, exactly like the original.
 */
class HiddenTaskThread(private var process: Process) : Thread() {
    override fun run() {
        try {
            process.waitFor()
        } catch (ex: Exception) {
        }
    }

    class ServiceShellHandler(private val context: Context) : ShellHandlerBase() {
        private var errorRows = ArrayList<String>()
        private var notificationMShortMsg = ""
        private var progressCurrent = 0
        private var progressTotal = 0
        private var forceStop: Runnable? = null
        private var isFinished = false

        override fun updateLog(msg: SpannableString?) {
        }

        override fun onReader(msg: Any?) {
        }

        override fun onError(msg: Any?) {
            notificationMShortMsg = "出现错误"
            synchronized(errorRows) {
                errorRows.add("" + msg?.toString())
            }
        }

        override fun onWrite(msg: Any?) {
        }

        override fun onExit(msg: Any?) {
            isFinished = true
            if (errorRows.size > 0) {
                // ShellHandlerBase 绑定主线程 Looper，Toast 安全
                Toast.makeText(
                    context,
                    "出现错误" + "\n\n" + errorRows.joinToString("\n"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun onStart(forceStop: Runnable?) {
            this.forceStop = forceStop
        }

        override fun onStart(msg: Any?) {
        }

        override fun onProgress(current: Int, total: Int) {
            progressCurrent = current
            progressTotal = total
        }
    }

    companion object {
        private var notificationCounter = 34050

        /** 是否有隐藏任务在运行中（原版位于 ActionListFragment，此处提升为进程级单例守卫） */
        @Volatile
        var hiddenTaskRunning = false
            private set

        fun startTask(
            context: Context,
            script: String,
            params: HashMap<String, String>?,
            nodeInfo: RunnableNode,
            onExit: Runnable,
            onDismiss: Runnable,
        ) {
            val applicationContext = context.applicationContext
            notificationCounter += 1

            val handler = ServiceShellHandler(applicationContext)
            ShellExecutor().execute(
                context,
                nodeInfo,
                script,
                {
                    try {
                        onExit.run()
                        onDismiss.run()
                    } catch (ex: Exception) {
                    }
                },
                params,
                handler
            )
        }

        /**
         * 带单例守卫的启动：已有隐藏任务在运行时返回 false（宿主应提示
         * `kr_hidden_task_running` —— “手速太快了，上个操作还没完成！”），否则启动并返回 true。
         */
        fun startTaskGuarded(
            context: Context,
            script: String,
            params: HashMap<String, String>?,
            nodeInfo: RunnableNode,
            onExit: Runnable,
            onDismiss: Runnable,
        ): Boolean {
            if (hiddenTaskRunning) {
                return false
            }
            hiddenTaskRunning = true
            startTask(context, script, params, nodeInfo, onExit, Runnable {
                hiddenTaskRunning = false
                onDismiss.run()
            })
            return true
        }
    }
}
