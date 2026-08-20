// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.projectkr.shell.MainActivity

object ShortcutHelper {
    fun addShortcut(context: Context, id: String, title: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("shortcut_title", title)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(title)
            .setLongLabel(title)
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
