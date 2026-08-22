// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectkr.shell.BuildConfig
import com.projectkr.shell.ui.theme.KrColorMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val MODE_LABELS = listOf(
    "跟随系统", "浅色", "深色", "动态取色 · 系统", "动态取色 · 浅色", "动态取色 · 深色",
)

/**
 * 关于 tab: theme settings, project links and version info.
 */
@Composable
fun AboutScreen(
    colorMode: KrColorMode,
    onColorModeChange: (KrColorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = { SmallTopAppBar(title = "关于") },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            SmallTitle(text = "外观")
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                OverlayDropdownPreference(
                    title = "主题模式",
                    summary = MODE_LABELS.getOrElse(colorMode.ordinal) { MODE_LABELS[0] },
                    items = MODE_LABELS,
                    selectedIndex = colorMode.ordinal,
                    onSelectedIndexChange = { index ->
                        KrColorMode.fromOrdinal(index).let(onColorModeChange)
                    },
                )
            }

            SmallTitle(text = "项目")
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                ArrowPreference(
                    title = "GitHub 仓库",
                    summary = "Tlrenhb/kr-scripts-miuix",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Tlrenhb/kr-scripts-miuix")),
                            )
                        }
                    },
                )
                ArrowPreference(
                    title = "Miuix 组件库",
                    summary = "compose-miuix-ui/miuix",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/compose-miuix-ui/miuix")),
                            )
                        }
                    },
                )
                ArrowPreference(
                    title = "KrScript 文档",
                    summary = "XML 配置格式说明",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/helloklf/kr-scripts")),
                            )
                        }
                    },
                )
            }

            SmallTitle(text = "版本")
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                Text(
                    text = "Kr Script ${BuildConfig.VERSION_NAME} (miuix)\n" +
                        "Compose + Miuix 重写版 · GPL-3.0\n" +
                        "原版 kr-scripts © helloklf",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}
