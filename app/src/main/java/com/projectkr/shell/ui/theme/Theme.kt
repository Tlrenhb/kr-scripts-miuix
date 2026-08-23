// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * App color modes, persisted under [PREFS]. Mirrors the original app's
 * 跟随系统 / 浅色 / 深色 (+ dynamic color) options.
 */
enum class KrColorMode {
    SYSTEM, LIGHT, DARK, MONET_SYSTEM, MONET_LIGHT, MONET_DARK;

    companion object {
        fun fromOrdinal(value: Int): KrColorMode =
            entries.getOrElse(value) { SYSTEM }
    }
}

/** Named seed colors offered in settings; index 0 means "follow wallpaper". */
val KeyColorChoices: List<Pair<String, Color?>> = listOf(
    "跟随壁纸" to null,
    "蓝" to Color(0xFF3482FF),
    "绿" to Color(0xFF36D167),
    "紫" to Color(0xFF7C4DFF),
    "黄" to Color(0xFFFFB21D),
    "橙" to Color(0xFFFF5722),
    "粉" to Color(0xFFE91E63),
    "青" to Color(0xFF00BCD4),
)

/** Palette styles offered in settings, in doc order. */
val PaletteStyleChoices: List<Pair<String, ThemePaletteStyle>> = listOf(
    "色调点 (默认)" to ThemePaletteStyle.TonalSpot,
    "中性" to ThemePaletteStyle.Neutral,
    "鲜艳" to ThemePaletteStyle.Vibrant,
    "表现力" to ThemePaletteStyle.Expressive,
    "彩虹" to ThemePaletteStyle.Rainbow,
    "水果沙拉" to ThemePaletteStyle.FruitSalad,
    "单色" to ThemePaletteStyle.Monochrome,
    "保真" to ThemePaletteStyle.Fidelity,
    "内容" to ThemePaletteStyle.Content,
)

/** Complete, persisted theme configuration. */
data class KrThemeConfig(
    val mode: KrColorMode = KrColorMode.SYSTEM,
    /** Index into [KeyColorChoices]; 0 follows the system wallpaper (Monet). */
    val keyColorIndex: Int = 0,
    /** Index into [PaletteStyleChoices]; only used by Monet modes. */
    val paletteStyleIndex: Int = 0,
    /** 0 = Spec2021, 1 = Spec2025 (TonalSpot/Neutral/Vibrant/Expressive only). */
    val colorSpecIndex: Int = 0,
)

private const val PREFS = "kr-script-config"
private const val KEY_MODE = "color_mode"
private const val KEY_SEED = "key_color_index"
private const val KEY_STYLE = "palette_style"
private const val KEY_SPEC = "color_spec"

fun loadThemeConfig(context: Context): KrThemeConfig {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return KrThemeConfig(
        mode = KrColorMode.fromOrdinal(prefs.getInt(KEY_MODE, 0)),
        keyColorIndex = prefs.getInt(KEY_SEED, 0),
        paletteStyleIndex = prefs.getInt(KEY_STYLE, 0),
        colorSpecIndex = prefs.getInt(KEY_SPEC, 0),
    )
}

fun saveThemeConfig(context: Context, config: KrThemeConfig) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_MODE, config.mode.ordinal)
        .putInt(KEY_SEED, config.keyColorIndex)
        .putInt(KEY_STYLE, config.paletteStyleIndex)
        .putInt(KEY_SPEC, config.colorSpecIndex)
        .apply()
}

/**
 * Remembers a [ThemeController] for [config]; re-created whenever any theme
 * input changes (doc: every input that affects the result must be a remember key).
 */
@Composable
fun rememberThemeController(config: KrThemeConfig): ThemeController =
    remember(config) {
        ThemeController(
            colorSchemeMode = config.mode.toSchemeMode(),
            keyColor = KeyColorChoices.getOrNull(config.keyColorIndex)?.second,
            paletteStyle = PaletteStyleChoices.getOrElse(config.paletteStyleIndex) { PaletteStyleChoices[0] }.second,
            colorSpec = if (config.colorSpecIndex == 1) ThemeColorSpec.Spec2025 else ThemeColorSpec.Spec2021,
        )
    }

private fun KrColorMode.toSchemeMode(): ColorSchemeMode = when (this) {
    KrColorMode.SYSTEM -> ColorSchemeMode.System
    KrColorMode.LIGHT -> ColorSchemeMode.Light
    KrColorMode.DARK -> ColorSchemeMode.Dark
    KrColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
    KrColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
    KrColorMode.MONET_DARK -> ColorSchemeMode.MonetDark
}
