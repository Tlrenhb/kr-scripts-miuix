// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectkr.shell.BuildConfig
import com.projectkr.shell.ui.theme.KeyColorChoices
import com.projectkr.shell.ui.theme.KrColorMode
import com.projectkr.shell.ui.theme.KrThemeConfig
import com.projectkr.shell.ui.theme.PaletteStyleChoices
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.WorldClock
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val MODE_LABELS = listOf(
    "跟随系统", "浅色", "深色", "动态取色 · 系统", "动态取色 · 浅色", "动态取色 · 深色",
)

/**
 * Settings tab: application appearance, framework links, and version/license.
 * Uses the target Miuix 0.9.4 Preference family rather than KernelSU's older
 * `Super*` APIs, while preserving the same HyperOS-style grouped hierarchy.
 */
@Composable
fun AboutScreen(
    themeConfig: KrThemeConfig,
    onThemeConfigChange: (KrThemeConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isMonet = themeConfig.mode.isMonet
    val update = { transform: (KrThemeConfig) -> KrThemeConfig ->
        onThemeConfigChange(transform(themeConfig))
    }
    var showThemeResetConfirmation by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = "设置", scrollBehavior = scrollBehavior) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            // KernelSU-style identity header, rendered with semantic Miuix colors.
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
                            .squircleSurface(
                                color = MiuixTheme.colorScheme.primaryContainer,
                                cornerRadius = 20.dp,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "KR",
                            style = MiuixTheme.textStyles.title1,
                            color = MiuixTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Kr Script",
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${BuildConfig.VERSION_NAME} · Miuix 版",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            item { SettingsSectionTitle("外观") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    OverlayDropdownPreference(
                        title = "主题模式",
                        summary = MODE_LABELS.getOrElse(themeConfig.mode.ordinal) { MODE_LABELS.first() },
                        items = MODE_LABELS,
                        selectedIndex = themeConfig.mode.ordinal,
                        startAction = { SettingsPreferenceIcon(MiuixIcons.Settings) },
                        onSelectedIndexChange = { index ->
                            update { it.copy(mode = KrColorMode.fromOrdinal(index)) }
                        },
                    )
                    ArrowPreference(
                        title = "恢复默认外观",
                        summary = "跟随系统，并恢复默认动态配色",
                        startAction = { SettingsPreferenceIcon(MiuixIcons.Reset) },
                        onClick = { showThemeResetConfirmation = true },
                    )
                }
            }

            // Dependent configuration gets its own semantic section rather than
            // appearing as unexplained rows under non-dynamic themes.
            item {
                AnimatedVisibility(visible = isMonet) {
                    Column {
                        SettingsSectionTitle("动态配色")
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            OverlayDropdownPreference(
                                title = "主题颜色",
                                summary = "动态取色的种子颜色",
                                items = KeyColorChoices.map { it.first },
                                selectedIndex = themeConfig.keyColorIndex,
                                startAction = { SettingsPreferenceIcon(MiuixIcons.Theme) },
                                onSelectedIndexChange = { index ->
                                    update { it.copy(keyColorIndex = index) }
                                },
                            )
                            OverlayDropdownPreference(
                                title = "调色板风格",
                                summary = PaletteStyleChoices
                                    .getOrElse(themeConfig.paletteStyleIndex) { PaletteStyleChoices.first() }
                                    .first,
                                items = PaletteStyleChoices.map { it.first },
                                selectedIndex = themeConfig.paletteStyleIndex,
                                startAction = { SettingsPreferenceIcon(MiuixIcons.Background) },
                                onSelectedIndexChange = { index ->
                                    update { it.copy(paletteStyleIndex = index) }
                                },
                            )
                        }
                    }
                }
            }

            item { SettingsSectionTitle("关于") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    ArrowPreference(
                        title = "GitHub 仓库",
                        summary = "Tlrenhb/kr-scripts-miuix",
                        startAction = { SettingsPreferenceIcon(MiuixIcons.Link) },
                        onClick = {
                            openUrl(context, "https://github.com/Tlrenhb/kr-scripts-miuix")
                        },
                    )
                    ArrowPreference(
                        title = "Miuix 组件库",
                        summary = "compose-miuix-ui/miuix",
                        startAction = { SettingsPreferenceIcon(MiuixIcons.WorldClock) },
                        onClick = {
                            openUrl(context, "https://github.com/compose-miuix-ui/miuix")
                        },
                    )
                    ArrowPreference(
                        title = "KrScript 文档",
                        summary = "XML 配置格式说明",
                        startAction = { SettingsPreferenceIcon(MiuixIcons.File) },
                        onClick = {
                            openUrl(context, "https://github.com/helloklf/kr-scripts")
                        },
                    )
                }
            }

            item { SettingsSectionTitle("版本与许可") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    Text(
                        text = "Compose + Miuix 重写版 · GPL-3.0\n原版 kr-scripts © helloklf",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }
        }

        // OverlayDialog is page-scoped and this Scaffold provides its popup host.
        OverlayDialog(
            title = "恢复默认外观",
            summary = "这会切换为跟随系统，并恢复默认主题颜色和调色板。",
            show = showThemeResetConfirmation,
            onDismissRequest = { showThemeResetConfirmation = false },
        ) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    text = "取消",
                    onClick = { showThemeResetConfirmation = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "恢复",
                    onClick = {
                        onThemeConfigChange(KrThemeConfig())
                        showThemeResetConfirmation = false
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    SmallTitle(
        text = text,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * KernelSU settings use a uniform leading-icon rail. In Miuix 0.9.4 the
 * equivalent slot is `startAction` (not the reference app's old leftAction).
 */
@Composable
private fun SettingsPreferenceIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MiuixTheme.colorScheme.onBackground,
        modifier = Modifier.padding(end = 16.dp),
    )
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
