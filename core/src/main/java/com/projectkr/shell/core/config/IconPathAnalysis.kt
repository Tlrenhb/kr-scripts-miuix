// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.projectkr.shell.core.model.ClickableNode

/**
 * 图标解析（移植自 b90f2cc `krscript/.../config/IconPathAnalysis.kt`）。
 *
 * 优先级契约（§4.3）：
 * - **列表行只用 iconPath**：[loadIcon] 不读 logoPath，没有默认图标，读不到返回 null
 *   （原版列表项在返回 null 时隐藏图标视图）。
 * - **logo → icon 回退仅用于快捷方式/FAB**：[loadLogo] 先 logoPath 后 iconPath；
 *   原版在 `useDefault=true` 时最后回退 `R.drawable.kr_shortcut_logo`（FAB 场景传
 *   `useDefault=false`，由调用方再回退到 kr_fab/kr_folder）。
 *
 * 与原版差异：core 模块没有资源，`useDefault=true` 且节点本身无图标时这里返回 null，
 * 由 app 层自行替换默认快捷方式图标。另：原版 `PathAnalysis` 总是带 root 回退，重写版
 * 通过可选的构造参数 [rootShell] 注入该能力（null 则跳过 root 回退）。
 */
class IconPathAnalysis(private val rootShell: ShellRunner? = null) {

    // 获取快捷方式的图标（带默认图标语义——见类注释，core 内实际返回 null）
    fun loadLogo(context: Context, clickableNode: ClickableNode): Drawable? {
        return loadLogo(context, clickableNode, true)
    }

    // 获取快捷方式的图标
    fun loadLogo(context: Context, clickableNode: ClickableNode, useDefault: Boolean): Drawable? {
        if (clickableNode.logoPath.isNotEmpty()) {
            val inputStream =
                PathAnalysis(context, clickableNode.pageConfigDir, rootShell).parsePath(clickableNode.logoPath)
            inputStream?.run {
                return bitmap2Drawable(BitmapFactory.decodeStream(this))
            }
        }
        if (clickableNode.iconPath.isNotEmpty()) {
            val inputStream =
                PathAnalysis(context, clickableNode.pageConfigDir, rootShell).parsePath(clickableNode.iconPath)
            inputStream?.run {
                return bitmap2Drawable(BitmapFactory.decodeStream(this))
            }
        }
        // 原版：useDefault 时回退 context.getDrawable(R.drawable.kr_shortcut_logo)；
        // core 无资源，统一返回 null 交由上层处理
        return null
    }

    // 列表行图标：仅 iconPath，无 logo 回退、无默认图标
    fun loadIcon(context: Context, clickableNode: ClickableNode): Drawable? {
        if (clickableNode.iconPath.isNotEmpty()) {
            val inputStream =
                PathAnalysis(context, clickableNode.pageConfigDir, rootShell).parsePath(clickableNode.iconPath)
            inputStream?.run {
                return bitmap2Drawable(BitmapFactory.decodeStream(this))
            }
        }
        return null
    }

    // Bitmap转换成Drawable（与原版一致，使用无 Resources 的构造器）
    @Suppress("DEPRECATION")
    fun bitmap2Drawable(bitmap: Bitmap): Drawable {
        return BitmapDrawable(bitmap)
    }
}
