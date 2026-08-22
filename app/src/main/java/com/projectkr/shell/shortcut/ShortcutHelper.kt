// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.projectkr.shell.MainActivity

/**
 * Pins launcher shortcuts (original ActionShortcutManager, modernized onto
 * ShortcutManager — no INSTALL_SHORTCUT permission needed).
 *
 * The shortcut opens MainActivity which seeds [Route.PageDetail] from extras.
 */
object ShortcutHelper {

    fun isPinSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun pinPageShortcut(context: Context, configPath: String, title: String): Boolean {
        if (!isPinSupported(context)) return false

        val id = "kr_${(configPath + title).hashCode()}"
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_CONFIG, configPath)
            putExtra(EXTRA_TITLE, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val shortcut = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(title.ifEmpty { "Kr Script" })
            .setIntent(intent)
            .build()

        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    const val EXTRA_CONFIG = "kr_shortcut_config"
    const val EXTRA_TITLE = "kr_shortcut_title"
}
