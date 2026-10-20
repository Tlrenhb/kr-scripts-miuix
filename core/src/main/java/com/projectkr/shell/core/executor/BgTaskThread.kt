// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.executor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.text.SpannableString
import android.widget.RemoteViews
import com.projectkr.shell.core.model.RunnableNode
import java.util.HashMap

/**
 * Port of `krscript/src/main/java/com/omarea/krscript/BgTaskThread.kt` @ b90f2cc
 * (§3.4 bg-task mode of docs/FRAMEWORK_PARITY.md).
 *
 * Contract preserved: notification channel `kr_script_task_notification`, ids from
 * 34050+, `FLAG_NO_CLEAR | FLAG_ONGOING_EVENT` while running, last ≤8 log rows with
 * `……` elision, progress bar (hidden when `total == current`, indeterminate when
 * `total < 0`), stop action visible iff `interruptable && forceStop != null &&
 * !isFinished`, stop broadcast `<package>.TaskStop.N<id>` with int extra `"id"`.
 *
 * Deviations forced by the rewrite's module layout (core has no resources):
 * - The custom big view (`kr_task_notification` layout) is delegated to an optional
 *   [BgTaskNotificationCustomizer]; without one a standard notification
 *   (title + last log line + progress + stop action) is posted.
 * - User-facing strings default to the original Chinese literals instead of
 *   krscript string resources (override them via the customizer).
 * - `PendingIntent` uses `FLAG_IMMUTABLE` and the stop receiver is registered
 *   `RECEIVER_NOT_EXPORTED` on API 33+ (both mandatory on modern targetSdk).
 */
class BgTaskThread(private var process: Process) : Thread() {
    override fun run() {
        try {
            process.waitFor()
        } catch (ex: Exception) {
        }
    }

    /** 展开视图状态快照（供 [BgTaskNotificationCustomizer.createExpandedView] 使用） */
    class BgTaskViewState(
        val notificationId: Int,
        val title: String,
        val logText: String,
        val shortMessage: String,
        val progressCurrent: Int,
        val progressTotal: Int,
        val isFinished: Boolean,
        val stopVisible: Boolean,
        val stopIntent: PendingIntent?,
    )

    /** 通知 UI 定制钩子（字符串/小图标/展开视图），默认实现给出原版的中文字面量 */
    interface BgTaskNotificationCustomizer {
        fun channelName(): String = "后台任务通知"
        fun smallIcon(): Int = android.R.drawable.ic_dialog_info
        fun textTaskRunning(): String = "开始运行"
        fun textTaskHasError(): String = "出现错误"
        fun textTaskFinished(): String = "任务结束"
        fun textShellCompleted(): String = "脚本运行结束"
        fun textShellFinishError(): String = "运行结束，但出现错误"

        /** 返回自定义展开视图（对应原版 kr_task_notification 布局）；null → 使用标准通知 */
        fun createExpandedView(state: BgTaskViewState): RemoteViews? = null
    }

    class ServiceShellHandler(
        private val context: Context,
        private val runnableNode: RunnableNode,
        private val notificationID: Int,
        private val customizer: BgTaskNotificationCustomizer? = null,
    ) : ShellHandlerBase() {
        private val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        private val notificationTitle = runnableNode.title
        private var notificationMessageRows = ArrayList<String>()
        private var notificationMShortMsg = ""
        private var progressCurrent = 0
        private var progressTotal = 0
        private var someIgnored = false
        private var forceStop: Runnable? = null
        private var isFinished = false
        private val STOP_CLICK_ACTION_NAME = context.packageName + ".TaskStop." + "N" + notificationID
        private val stopIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(STOP_CLICK_ACTION_NAME).apply {
                putExtra("id", notificationID)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // customizer 缺省时使用接口默认值（原版中文字面量）
        private val effectiveCustomizer = customizer ?: object : BgTaskNotificationCustomizer {}
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null && intent.hasExtra("id")) {
                    if (intent.getIntExtra("id", 0) == notificationID) {
                        forceStop?.run()
                    }
                }
            }
        }

        private fun updateNotification() {
            if (notificationMessageRows.size > 8) {
                synchronized(notificationMessageRows) {
                    notificationMessageRows.remove(notificationMessageRows.first())
                    someIgnored = true
                }
            }

            val logText = notificationMessageRows
                .joinToString("", if (someIgnored) "……\n" else "")
                .trim()
            val stopVisible = (runnableNode.interruptable && forceStop != null) && !isFinished

            val expandView = effectiveCustomizer.createExpandedView(
                BgTaskViewState(
                    notificationID,
                    notificationTitle,
                    logText,
                    notificationMShortMsg,
                    progressCurrent,
                    progressTotal,
                    isFinished,
                    stopVisible,
                    if (stopVisible) stopIntent else null,
                )
            )

            val notificationBuilder = Notification.Builder(context)
                .setContentTitle("$notificationTitle($notificationID)")
                .setContentText("$notificationMShortMsg >> ${notificationMessageRows.lastOrNull()}")
                .setSmallIcon(effectiveCustomizer.smallIcon())
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
            if (progressTotal != progressCurrent) {
                notificationBuilder.setProgress(progressTotal, progressCurrent, progressTotal < 0)
            }

            if (stopVisible) {
                notificationBuilder.addAction(
                    Notification.Action.Builder(null, "✕", stopIntent).build()
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (expandView != null) {
                    notificationBuilder.setCustomBigContentView(expandView)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!channelCreated) {
                    val channel = NotificationChannel(
                        channelId,
                        effectiveCustomizer.channelName(),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    channel.enableLights(false)
                    channel.enableVibration(false)
                    channel.setSound(null, null)
                    notificationManager.createNotificationChannel(channel)
                }
                channelCreated = true
                notificationBuilder.setChannelId(channelId)
            } else {
                notificationBuilder.setSound(null)
                notificationBuilder.setVibrate(null)
            }

            val notification = notificationBuilder.build()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                if (expandView != null) {
                    notification.bigContentView = expandView
                }
            }

            if (!isFinished) {
                notification.flags = Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
            }

            notificationManager.notify(notificationID, notification) // 发送通知
        }

        override fun updateLog(msg: SpannableString?) {
        }

        override fun onReader(msg: Any?) {
            synchronized(notificationMessageRows) {
                notificationMessageRows.add("" + msg?.toString())
                updateNotification()
            }
        }

        override fun onError(msg: Any?) {
            notificationMShortMsg = effectiveCustomizer.textTaskHasError()
            synchronized(notificationMessageRows) {
                notificationMessageRows.add("" + msg?.toString())
                updateNotification()
            }
        }

        override fun onWrite(msg: Any?) {
        }

        override fun onExit(msg: Any?) {
            try {
                // context.unregisterReceiver(receiver)（原版即被注释，保留注释状态）
            } catch (ex: Exception) {
            }
            isFinished = true
            notificationMShortMsg = effectiveCustomizer.textTaskFinished()
            synchronized(notificationMessageRows) {
                if (msg == 0) {
                    notificationMessageRows.add("\n" + effectiveCustomizer.textShellCompleted())
                } else {
                    notificationMessageRows.add("\n" + effectiveCustomizer.textShellFinishError() + " " + msg?.toString())
                }
                updateNotification()
            }
        }

        override fun onStart(forceStop: Runnable?) {
            this.forceStop = forceStop
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, IntentFilter(STOP_CLICK_ACTION_NAME), Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, IntentFilter(STOP_CLICK_ACTION_NAME))
            }

            updateNotification()
        }

        override fun onStart(msg: Any?) {
            notificationMShortMsg = effectiveCustomizer.textTaskRunning()
        }

        override fun onProgress(current: Int, total: Int) {
            progressCurrent = current
            progressTotal = total
            updateNotification()
        }
    }

    companion object {
        private var channelCreated = false
        private const val channelId = "kr_script_task_notification"
        private var notificationCounter = 34050

        fun startTask(
            context: Context,
            script: String,
            params: HashMap<String, String>?,
            nodeInfo: RunnableNode,
            onExit: Runnable,
            onDismiss: Runnable,
            customizer: BgTaskNotificationCustomizer? = null,
            onStarted: (() -> Unit)? = null,
        ) {
            val applicationContext = context.applicationContext
            notificationCounter += 1

            val handler = ServiceShellHandler(applicationContext, nodeInfo, notificationCounter, customizer)
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

            // 原版在此弹出「后台任务开始运行」帮助对话框（DialogHelper.helpInfo）；
            // core 无对话框实现，交由宿主通过 onStarted 展示
            onStarted?.invoke()
        }
    }
}
