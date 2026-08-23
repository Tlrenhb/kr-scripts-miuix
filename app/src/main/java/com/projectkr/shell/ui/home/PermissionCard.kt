// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.projectkr.shell.service.FloatMonitor
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 权限卡片：三项关键授权的状态与直达入口。离开系统设置页返回时自动刷新状态。
 */
@Composable
fun PermissionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-read grant states whenever the user comes back from Settings.
    var refreshKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // States are read per composition; refreshKey invalidates after Settings.
    @Suppress("UNUSED_EXPRESSION")
    refreshKey

    Card(modifier = modifier) {
        Column(
            Modifier
                .padding(16.dp)
                .padding(bottom = 4.dp),
        ) {
            Text(
                text = "权限",
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // 所有文件访问 (Android 11+ special access; legacy below 30).
            PermRow(
                label = "所有文件访问",
                granted = hasAllFilesAccess(context),
                summary = "脚本与文件选择器读写任意文件",
                onGrant = { openAllFilesSettings(context) },
            )

            // 悬浮窗.
            PermRow(
                label = "显示悬浮窗",
                granted = FloatMonitor.canDrawOverlays(context),
                summary = "桌面悬浮窗监控需要此权限",
                onGrant = { openOverlaySettings(context) },
            )

            // 通知 (Android 13+ runtime).
            PermRow(
                label = "通知",
                granted = notificationsEnabled(context),
                summary = "后台任务完成通知",
                onGrant = { openNotificationSettings(context) },
            )
        }
    }
}

@Composable
private fun PermRow(
    label: String,
    granted: Boolean,
    summary: String,
    onGrant: () -> Unit,
) {
    ArrowPreference(
        title = label + if (granted) " · 已授权" else " · 未授权",
        summary = summary,
        onClick = if (granted) null else onGrant,
    )
}

fun hasAllFilesAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

fun openAllFilesSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    } else {
        // Legacy devices: runtime dialog for the classic storage permission.
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
    runCatching {
        (context as? Activity)?.startActivity(intent)
            ?: context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
