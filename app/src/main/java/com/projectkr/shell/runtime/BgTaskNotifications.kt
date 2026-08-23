// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * Completion notifications for background (`bg-task`) script runs.
 */
object BgTaskNotifications {

    const val CHANNEL_ID = "kr_bgtask"
    private const val NOTIFICATION_TAG = "kr_bgtask"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "后台任务",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "bg-task 脚本执行完成通知"
            },
        )
    }

    /** Posts a run-finished notification; skipped when notifications are blocked. */
    fun notifyDone(context: Context, title: String, success: Boolean) {
        if (!canNotify(context)) return
        val text = if (success) "执行完成" else "执行失败"
        val builder = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_TAG, System.currentTimeMillis().toInt(), builder.build())
    }

    fun canNotify(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
}
