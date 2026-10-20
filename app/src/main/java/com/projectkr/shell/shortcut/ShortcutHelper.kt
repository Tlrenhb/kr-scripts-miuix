// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.shortcut

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.projectkr.shell.MainActivity
import com.projectkr.shell.R
import com.projectkr.shell.core.config.IconPathAnalysis
import com.projectkr.shell.core.model.ClickableNode
import com.projectkr.shell.core.model.PageNode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * 桌面快捷方式（FRAMEWORK_PARITY §4.4，对齐原版 ActionShortcutManager）。
 *
 * 协议（intent extras）：
 * - `"page"`：PageNode（Serializable）。因 PinnedShortcut 无法携带 Serializable extra，
 *   创建时替换为 `"shortcutId"`（PageNode 经 [saveShortcutTarget] 持久化到私有目录）。
 * - `"shortcutId"`：String，取回 PageNode 的 key（[getShortcutTarget]）。
 * - `"autoRunItemId"`：String，RunnableNode 的 key，页面加载完成后自动触发该节点。
 * - 目标 activity = [MainActivity]；intent 带
 *   `FLAG_ACTIVITY_NO_HISTORY | FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS`。
 * - 快捷方式 id = `"addin_" + node.index`；label = node.title；
 *   图标 = IconPathAnalysis logo 链（logo → icon → 默认 launcher icon）。
 * - API 26+：ShortcutManager.requestPinShortcut（已存在同 id 的固定快捷方式则
 *   updateShortcuts）；更低版本：广播 `com.android.launcher.action.INSTALL_SHORTCUT`。
 */
object ShortcutHelper {
    const val EXTRA_PAGE = "page"
    const val EXTRA_SHORTCUT_ID = "shortcutId"
    const val EXTRA_AUTO_RUN_ITEM_ID = "autoRunItemId"
    const val SHORTCUT_ID_PREFIX = "addin_"

    private const val TAG = "ShortcutHelper"
    private const val OBJECTS_DIR = "objects"

    /**
     * 完整协议：为 [node]（页面或功能项）创建固定快捷方式。
     *
     * @param pageNode 该快捷方式指向的页面（[node] 本身是 PageNode 时传它；
     *   否则传宿主页面配置）
     * @param autoRunItemId RunnableNode 的 key（页面节点传 null）
     */
    fun addShortcut(
        context: Context,
        node: ClickableNode,
        pageNode: PageNode,
        autoRunItemId: String? = null,
    ): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(context.applicationContext, MainActivity::class.java.name)
            flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            // Serializable extra 无法固定 → 持久化 PageNode，仅携带 shortcutId
            putExtra(EXTRA_SHORTCUT_ID, saveShortcutTarget(context, pageNode))
            if (!autoRunItemId.isNullOrEmpty()) {
                putExtra(EXTRA_AUTO_RUN_ITEM_ID, autoRunItemId)
            }
        }
        val drawable = loadShortcutIcon(context, node)
        return requestPin(context, SHORTCUT_ID_PREFIX + node.index, node.title, intent, drawable)
    }

    /**
     * 兼容旧签名（便捷重载，内部走新协议）：只有 key + title 时构造一个仅携带
     * [EXTRA_AUTO_RUN_ITEM_ID] 的快捷方式（不携带 page，宿主侧按 key 处理）。
     */
    fun addShortcut(context: Context, key: String, title: String) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(context.applicationContext, MainActivity::class.java.name)
            flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra(EXTRA_AUTO_RUN_ITEM_ID, key)
        }
        requestPin(
            context,
            SHORTCUT_ID_PREFIX + key,
            title,
            intent,
            defaultLauncherIcon(context),
        )
    }

    /** 按 logo → icon → 默认 launcher icon 链解析快捷方式图标 */
    private fun loadShortcutIcon(context: Context, node: ClickableNode): Drawable {
        return IconPathAnalysis().loadLogo(context, node) ?: defaultLauncherIcon(context)
    }

    private fun defaultLauncherIcon(context: Context): Drawable {
        return try {
            ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                ?: context.packageManager.getApplicationIcon(context.packageName)
        } catch (ignored: Exception) {
            context.packageManager.getApplicationIcon(context.packageName)
        }
    }

    // ---- 固定 / 更新 / 回退广播 ----

    private fun requestPin(
        context: Context,
        id: String,
        title: String,
        intent: Intent,
        icon: Drawable,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestPinOreo(context, id, title, intent, icon)
        } else {
            requestPinLegacy(context, id, title, intent, icon)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun requestPinOreo(
        context: Context,
        id: String,
        title: String,
        intent: Intent,
        icon: Drawable,
    ): Boolean {
        return try {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
            if (!shortcutManager.isRequestPinShortcutSupported) return false

            val info = ShortcutInfo.Builder(context, id)
                .setIcon(Icon.createWithBitmap(drawableToBitmap(icon)))
                .setShortLabel(title)
                .setIntent(intent)
                .build()

            // 已固定的同 id 快捷方式 → 更新而不是重复请求
            val pinned = try {
                shortcutManager.pinnedShortcuts
            } catch (ignored: Exception) {
                emptyList<ShortcutInfo>()
            }
            if (pinned.any { it.id == id }) {
                shortcutManager.updateShortcuts(listOf(info))
                return true
            }

            val callback = PendingIntent.getBroadcast(
                context,
                0,
                Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            shortcutManager.requestPinShortcut(info, callback.intentSender)
            true
        } catch (ex: Exception) {
            Log.e(TAG, "requestPinOreo failed", ex)
            false
        }
    }

    private fun requestPinLegacy(
        context: Context,
        id: String,
        title: String,
        intent: Intent,
        icon: Drawable,
    ): Boolean {
        return try {
            val broadcast = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_NAME, title)
                putExtra("duplicate", false)
                putExtra(Intent.EXTRA_SHORTCUT_ICON, drawableToBitmap(icon))
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent)
            }
            context.sendBroadcast(broadcast)
            true
        } catch (ex: Exception) {
            Log.e(TAG, "requestPinLegacy failed", ex)
            false
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        (drawable as? BitmapDrawable)?.bitmap?.let { return it }
        val width = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(512)
        val height = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(512)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    // ---- PageNode 持久化（原版 common ObjectStorage 协议：filesDir/objects/<id>） ----

    private fun saveShortcutTarget(context: Context, pageNode: PageNode): String {
        val id = System.currentTimeMillis().toString()
        val file = File(context.filesDir, "$OBJECTS_DIR/$id")
        runCatching {
            file.parentFile?.mkdirs()
            ObjectOutputStream(FileOutputStream(file)).use { it.writeObject(pageNode) }
        }.onFailure { Log.e(TAG, "saveShortcutTarget failed", it) }
        return id
    }

    /** 由 `"shortcutId"` 取回持久化的 PageNode（不存在/反序列化失败返回 null） */
    fun getShortcutTarget(context: Context, shortcutId: String): PageNode? {
        val file = File(context.filesDir, "$OBJECTS_DIR/$shortcutId")
        if (!file.exists()) return null
        return runCatching {
            ObjectInputStream(FileInputStream(file)).use { it.readObject() as? PageNode }
        }.getOrNull()
    }

    fun removeShortcutTarget(context: Context, shortcutId: String) {
        runCatching { File(context.filesDir, "$OBJECTS_DIR/$shortcutId").delete() }
    }
}
