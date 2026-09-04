// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.shortcut

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.projectkr.krscript.core.config.PathAnalysis
import com.projectkr.krscript.core.model.ClickableNode
import com.projectkr.krscript.core.runtime.DefaultAssetExtractor
import com.projectkr.shell.MainActivity
import com.projectkr.shell.runtime.KrScriptRuntime

/**
 * Pins launcher shortcuts (original ActionShortcutManager, modernized onto
 * ShortcutManager — no INSTALL_SHORTCUT broadcast needed on O+; the compat
 * fallback below O uses the permission declared in the manifest).
 *
 * The shortcut opens MainActivity which seeds [com.projectkr.shell.navigation.Route.PageDetail]
 * from extras. Icons resolve logoPath first, then iconPath (IconPathAnalysis order).
 */
object ShortcutHelper {

    fun isPinSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun pinPageShortcut(
        context: Context,
        configPath: String,
        title: String,
        node: ClickableNode? = null,
        nodeKey: String = "",
    ): Boolean {
        if (!isPinSupported(context)) return false

        // Stable identity per node (original addin_<index> scheme).
        val identity = node?.index?.takeIf { it.isNotEmpty() }
            ?: nodeKey.takeIf { it.isNotEmpty() }
            ?: configPath
        val id = "kr_" + identity.hashCode()

        val icon = node?.let { loadNodeIcon(it) }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_CONFIG, configPath)
            putExtra(EXTRA_TITLE, title)
            val key = node?.key?.takeIf { it.isNotEmpty() } ?: nodeKey
            if (key.isNotEmpty()) putExtra(EXTRA_KEY, key)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val builder = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(title.ifEmpty { "Kr Script" })
            .setIntent(intent)
        if (icon != null) builder.setIcon(icon)

        val shortcut = builder.build()

        // Already pinned with the same id → update silently (original behavior).
        val alreadyPinned = ShortcutManagerCompat.getShortcuts(
            context, ShortcutManagerCompat.FLAG_MATCH_PINNED,
        ).any { it.id == id }

        return if (alreadyPinned) {
            ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
        } else {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }
    }

    /**
     * IconPathAnalysis order: logoPath → iconPath. Resolves through the engine
     * path rules (assets/disk/root fallback) and decodes off-thread.
     */
    fun loadNodeIcon(node: ClickableNode): IconCompat? {
        val path = node.logoPath.ifEmpty { node.iconPath }.trim()
        if (path.isEmpty() || !KrScriptRuntime.isReady) return null
        return runCatching {
            val extractor = DefaultAssetExtractor(KrScriptRuntime.assetSource, KrScriptRuntime.fileStore)
            val locator = PathAnalysis(
                assets = KrScriptRuntime.assetSource,
                files = KrScriptRuntime.fileStore,
                shell = KrScriptRuntime.shell,
                extractor = extractor,
                parentDir = node.pageConfigDir,
            )
            val stream = locator.parsePath(path)?.stream ?: return null
            stream.use { input ->
                val bmp: Bitmap = BitmapFactory.decodeStream(input) ?: return null
                IconCompat.createWithBitmap(bmp)
            }
        }.getOrNull()
    }

    const val EXTRA_CONFIG = "kr_shortcut_config"
    const val EXTRA_TITLE = "kr_shortcut_title"
    const val EXTRA_KEY = "kr_shortcut_key"
}
