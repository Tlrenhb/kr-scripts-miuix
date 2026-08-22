// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * App routes. [MainTabs] hosts the four-tab shell; everything else pushes on top.
 */
@Serializable
sealed interface Route : NavKey {

    /** Splash entry shown briefly at launch. */
    @Serializable
    data object Splash : Route

    /** Home / Pages / Favorites / About tab shell. */
    @Serializable
    data object MainTabs : Route

    /** A KrScript page (sub page list rendered from its config). */
    @Serializable
    data class PageDetail(
        val configPath: String,
        val title: String,
        val nodeKey: String,
    ) : Route

    /** An online html page rendered in a WebView. */
    @Serializable
    data class OnlinePage(
        val url: String,
        val title: String,
    ) : Route

    /** Live CPU / battery monitor. */
    @Serializable
    data object Monitor : Route

    /** Custom file selector rooted at [startDir]. */
    @Serializable
    data class FileSelector(val startDir: String) : Route
}
