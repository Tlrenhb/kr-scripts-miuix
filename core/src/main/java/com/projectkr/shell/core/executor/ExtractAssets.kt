// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.executor

import android.content.Context
import java.io.File
import java.util.HashMap

/**
 * 简化版资源解压：把 assets 中的脚本/资源复制到应用私有目录。
 */
class ExtractAssets(private val context: Context) {
    private val extractHistory = HashMap<String, String>()

    fun extractResource(fileName: String): String? {
        if (fileName.isBlank()) return null
        extractHistory[fileName]?.let { return it }

        val normalized = if (fileName.startsWith(ASSETS_PREFIX)) {
            fileName.substring(ASSETS_PREFIX.length)
        } else {
            fileName
        }

        val target = File(context.filesDir, normalized)
        target.parentFile?.mkdirs()
        return try {
            context.assets.open(normalized).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            target.absolutePath.also {
                extractHistory[fileName] = it
            }
        } catch (e: Exception) {
            null
        }
    }

    fun extractResources(dir: String): String? {
        if (dir.isBlank()) return null
        extractHistory[dir]?.let { return it }

        val normalized = when {
            dir.startsWith(ASSETS_PREFIX) -> dir.substring(ASSETS_PREFIX.length)
            dir.endsWith("/") -> dir.dropLast(1)
            else -> dir
        }

        return try {
            val children = context.assets.list(normalized)
            if (children != null && children.isNotEmpty()) {
                children.forEach { child ->
                    extractResource("$normalized/$child")
                }
                val path = getExtractPath(normalized)
                extractHistory[dir] = path
                path
            } else {
                extractResource(normalized)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getExtractPath(file: String): String {
        val normalized = if (file.startsWith(ASSETS_PREFIX)) {
            file.substring(ASSETS_PREFIX.length)
        } else {
            file
        }
        return File(context.filesDir, normalized).absolutePath
    }

    private companion object {
        const val ASSETS_PREFIX = "file:///android_asset/"
    }
}
