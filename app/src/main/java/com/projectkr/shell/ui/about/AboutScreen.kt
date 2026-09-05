// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectkr.shell.BuildConfig
import com.projectkr.shell.ui.theme.KrColorMode
import com.projectkr.shell.ui.theme.KeyColorChoices
import com.projectkr.shell.ui.theme.KrThemeConfig
import com.projectkr.shell.ui.theme.PaletteStyleChoices
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val MODE_LABELS = listOf(
    "跟随系统", "浅色", "深色", "动态取色 · 系统", "动态取色 · 浅色", "动态取色 · 深色",
)

private val SPEC_LABELS = listOf("Spec2021 (兼容)", "Spec2025 (新版规范)")

@Composable
fun AboutScreen(
    themeConfig: KrThemeConfig,
    onThemeConfigChange: (KrThemeConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isMonet = themeConfig.mode.ordinal >= KrColorMode.MONET_SYSTEM.ordinal
    val update = { transform: (KrThemeConfig) -> KrThemeConfig ->
        onThemeConfigChange(transform(themeConfig))
    }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = "关于", scrollBehavior = scrollBehavior)
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            // Hero header (KernelSU About pattern)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .squircleClip(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("K", fontSize = 32.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Kr Script", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    Text(
                        BuildConfig.VERSION_NAME + " (miuix)",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            item { SmallTitle(text = "外观") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    OverlayDropdownPreference(
                        title = "主题模式",
                        summary = MODE_LABELS.getOrElse(themeConfig.mode.ordinal) { MODE_LABELS[0] },
                        items = MODE_LABELS,
                        selectedIndex = themeConfig.mode.ordinal,
                        onSelectedIndexChange = { index ->
                            update { it.copy(mode = KrColorMode.fromOrdinal(index)) }
                        },
                    )
                    androidx.compose.animation.AnimatedVisibility(visible = isMonet) {
                        Column {
                            OverlayDropdownPreference(
                                title = "主题颜色",
                                summary = "动态取色的种子颜色",
                                items = KeyColorChoices.map { it.first },
                                selectedIndex = themeConfig.keyColorIndex,
                                onSelectedIndexChange = { index ->
                                    update { it.copy(keyColorIndex = index) }
                                },
                            )
                            OverlayDropdownPreference(
                                title = "调色板风格",
                                summary = PaletteStyleChoices.getOrElse(themeConfig.paletteStyleIndex) { PaletteStyleChoices[0] }.first,
                                items = PaletteStyleChoices.map { it.first },
                                selectedIndex = themeConfig.paletteStyleIndex,
                                onSelectedIndexChange = { index ->
                                    update { it.copy(paletteStyleIndex = index) }
                                },
                            )
                            OverlayDropdownPreference(
                                title = "颜色规范",
                                summary = "Spec2025 仅部分风格支持，其余自动回退",
                                items = SPEC_LABELS,
                                selectedIndex = themeConfig.colorSpecIndex,
                                onSelectedIndexChange = { index ->
                                    update { it.copy(colorSpecIndex = index) }
                                },
                            )
                        }
                    }
                }
            }

            item { SmallTitle(text = "项目") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    ArrowPreference(
                        title = "GitHub 仓库",
                        summary = "Tlrenhb/kr-scripts-miuix",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Link,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                        },
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
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.WorldClock,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                        },
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
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.File,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                        },
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/helloklf/kr-scripts")),
                                )
                            }
                        },
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    Text(
                        text = "Compose + Miuix 重写版 · GPL-3.0\n原版 kr-scripts © helloklf",
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
}
