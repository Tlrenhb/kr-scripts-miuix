// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

private const val PROJECT_URL = "https://github.com/Tlrenhb/kr-scripts-miuix"

/**
 * 关于页。
 *
 * - 版本信息（PackageManager 读取 versionName / longVersionCode）
 * - 主题模式选择（6 项 ↔ [ColorSchemeMode]，OverlayDropdownPreference）
 * - 系统通知设置入口（Settings.ACTION_APP_NOTIFICATION_SETTINGS）
 * - 开源地址入口（ACTION_VIEW）
 */
@Composable
fun AboutScreen(
    contentPadding: PaddingValues,
    themeMode: ColorSchemeMode,
    onThemeModeChange: (ColorSchemeMode) -> Unit,
) {
    val context = LocalContext.current

    val themeModes = remember {
        listOf(
            ColorSchemeMode.System,
            ColorSchemeMode.Light,
            ColorSchemeMode.Dark,
            ColorSchemeMode.MonetSystem,
            ColorSchemeMode.MonetLight,
            ColorSchemeMode.MonetDark,
        )
    }
    val themeLabels = remember {
        listOf("跟随系统", "浅色", "深色", "动态取色-跟随系统", "动态取色-浅色", "动态取色-深色")
    }

    val versionText = remember {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "${info.versionName} ($code)"
        } catch (ignored: Exception) {
            "--"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
        item(key = "title_app") { SmallTitle(text = "应用") }
        item(key = "card_app") {
            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                ArrowPreference(
                    title = "KrScript Miuix",
                    summary = "版本 $versionText\n${context.packageName}",
                )
                ArrowPreference(
                    title = "项目说明",
                    summary = "KrScript 脚本框架的 Miuix 重制版：通过 XML 配置生成页面，" +
                        "以 ROOT/SH 执行脚本，支持参数、动态状态与快捷方式。",
                )
            }
        }

        item(key = "title_settings") { SmallTitle(text = "设置") }
        item(key = "card_settings") {
            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                OverlayDropdownPreference(
                    items = themeLabels,
                    selectedIndex = themeModes.indexOf(themeMode).coerceAtLeast(0),
                    title = "主题模式",
                    onSelectedIndexChange = { index ->
                        onThemeModeChange(themeModes[index])
                    },
                )
                ArrowPreference(
                    title = "通知设置",
                    summary = "跳转系统应用通知设置（前台服务 / 后台任务通知）",
                    onClick = {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    },
                                )
                            } else {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        } catch (ignored: Exception) {
                            Toast.makeText(context, "无法打开通知设置", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }

        item(key = "title_more") { SmallTitle(text = "更多") }
        item(key = "card_more") {
            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                ArrowPreference(
                    title = "开源地址",
                    summary = PROJECT_URL,
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)),
                            )
                        } catch (ignored: Exception) {
                            Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }
}
