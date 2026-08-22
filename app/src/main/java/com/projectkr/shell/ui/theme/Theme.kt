// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * App color modes, persisted inSharedPreferences under [PREFS]/[KEY].
 * Mirrors the original app's 跟随系统 / 浅色 / 深色 (+ dynamic color) options.
 */
enum class KrColorMode {
    SYSTEM, LIGHT, DARK, MONET_SYSTEM, MONET_LIGHT, MONET_DARK;

    companion object {
        fun fromOrdinal(value: Int): KrColorMode =
            entries.getOrElse(value) { SYSTEM }
    }
}

private const val PREFS = "kr-script-config"
private const val KEY = "color_mode"

fun loadColorMode(context: Context): KrColorMode =
    KrColorMode.fromOrdinal(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0),
    )

fun saveColorMode(context: Context, mode: KrColorMode) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putInt(KEY, mode.ordinal).apply()
}

fun KrColorMode.toSchemeMode(): ColorSchemeMode = when (this) {
    KrColorMode.SYSTEM -> ColorSchemeMode.System
    KrColorMode.LIGHT -> ColorSchemeMode.Light
    KrColorMode.DARK -> ColorSchemeMode.Dark
    KrColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
    KrColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
    KrColorMode.MONET_DARK -> ColorSchemeMode.MonetDark
}

/**
 * Remembers a [ThemeController] for [mode]; re-created automatically when the
 * mode changes.
 */
@Composable
fun rememberThemeController(mode: KrColorMode): ThemeController =
    remember(mode) { ThemeController(mode.toSchemeMode()) }
