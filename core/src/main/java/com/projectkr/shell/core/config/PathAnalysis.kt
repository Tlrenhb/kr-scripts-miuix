// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.config

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * 简化版路径解析：支持 assets 与本地文件，暂未实现 ROOT 文件读取。
 */
class PathAnalysis(
    private val context: Context,
    private var parentDir: String = "",
) {
    private val assetsPrefix = "file:///android_asset/"
    private var currentAbsPath: String = ""

    fun getCurrentAbsPath(): String = currentAbsPath

    fun parsePath(filePath: String): InputStream? {
        return try {
            if (filePath.startsWith(assetsPrefix)) {
                currentAbsPath = filePath
                context.assets.open(filePath.substring(assetsPrefix.length))
            } else if (filePath.startsWith("/")) {
                val file = File(filePath)
                if (file.exists() && file.canRead()) {
                    currentAbsPath = filePath
                    file.inputStream()
                } else {
                    null
                }
            } else {
                if (parentDir.isNotEmpty() && parentDir.startsWith(assetsPrefix)) {
                    findAssetsResource(filePath)
                } else {
                    findDiskResource(filePath)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun pathConcat(parent: String, target: String): String {
        val isAssets = parent.startsWith(assetsPrefix)
        val parentPath = if (isAssets) parent.substring(assetsPrefix.length) else parent
        val base = when {
            parentPath.isEmpty() || parentPath.endsWith("/") -> parentPath
            else -> parentPath + "/"
        }
        val cleaned = if (target.startsWith("./")) target.substring(2) else target
        return (if (isAssets) assetsPrefix else "") + base + cleaned
    }

    private fun findAssetsResource(filePath: String): InputStream? {
        val relativePath = pathConcat(parentDir, filePath)
        return try {
            context.assets.open(relativePath.substring(assetsPrefix.length)).also {
                currentAbsPath = relativePath
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun findDiskResource(filePath: String): InputStream? {
        val relativePath = if (parentDir.isNotEmpty()) pathConcat(parentDir, filePath) else filePath
        val file = File(relativePath)
        return if (file.exists() && file.canRead()) {
            currentAbsPath = file.absolutePath
            file.inputStream()
        } else {
            null
        }
    }
}
