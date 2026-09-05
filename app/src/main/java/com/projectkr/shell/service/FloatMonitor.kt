// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Starts/stops [FloatMonitorService] and guides the user through the
 * SYSTEM_ALERT_WINDOW grant flow.
 */
object FloatMonitor {

    /** Observable so SwitchPreference stays accurate after double-tap close. */
    var running: Boolean by mutableStateOf(false)
        internal set

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun toggle(context: Context) {
        if (!canDrawOverlays(context)) {
            requestOverlayPermission(context)
            return
        }
        if (running) {
            context.stopService(Intent(context, FloatMonitorService::class.java))
        } else {
            context.startService(Intent(context, FloatMonitorService::class.java))
        }
    }
}
