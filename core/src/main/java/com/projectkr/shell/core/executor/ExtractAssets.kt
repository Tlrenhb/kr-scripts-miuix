// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.executor

import android.content.Context

/**
 * Port of `krscript/src/main/java/com/omarea/krscript/executor/ExtractAssets.java` @ b90f2cc.
 *
 * Assets are mirrored into the app private files dir; `.sh` files go through
 * [FileWrite.writePrivateShellFile] (CR/LF normalization), everything else through
 * [FileWrite.writePrivateFile] (chmod +x/+r/+w). Extraction is memoized **per
 * process** in a static map, exactly like the original.
 */
class ExtractAssets(private val context: Context) {

    private fun extractScript(fileNameIn: String?): String? {
        if (fileNameIn == null || fileNameIn.isEmpty()) {
            return null
        }

        if (extractHistory.containsKey(fileNameIn)) {
            return extractHistory[fileNameIn]
        }

        var fileName = fileNameIn
        if (fileName.startsWith("file:///android_asset/")) {
            fileName = fileName.substring("file:///android_asset/".length)
        }

        val filePath = FileWrite.writePrivateShellFile(fileName, fileName, context)

        if (filePath != null) {
            extractHistory[fileName] = filePath
        }

        return filePath
    }

    fun extractResource(fileNameIn: String?): String? {
        if (fileNameIn == null || fileNameIn.isEmpty()) {
            return null
        }

        if (extractHistory.containsKey(fileNameIn)) {
            return extractHistory[fileNameIn]
        }

        if (fileNameIn.endsWith(".sh")) {
            return extractScript(fileNameIn)
        }
        var fileName = fileNameIn
        if (fileName.startsWith("file:///android_asset/")) {
            fileName = fileName.substring("file:///android_asset/".length)
        }
        val filePath = FileWrite.writePrivateFile(context.assets, fileName, fileName, context)

        if (filePath != null) {
            extractHistory[fileName] = filePath
        }

        return filePath
    }

    fun extractResources(dirIn: String?): String? {
        if (dirIn == null || dirIn.isEmpty()) {
            return null
        }

        if (extractHistory.containsKey(dirIn)) {
            return extractHistory[dirIn]
        }

        var dir = dirIn
        if (dir.startsWith("file:///android_asset/")) {
            dir = dir.substring("file:///android_asset/".length)
        } else if (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length - 1)
        }

        try {
            val files = context.assets.list(dir)
            if (files != null && files.isNotEmpty()) {
                for (file in files) {
                    val relativePath = "$dir/$file"
                    extractResources(relativePath)
                }
                val outputDir = getExtractPath(dir)
                extractHistory[dir] = outputDir
                return outputDir
            } else {
                return extractResource(dir)
            }
        } catch (ignored: Exception) {
        }

        return ""
    }

    fun getExtractPath(file: String): String {
        return FileWrite.getPrivateFilePath(
            context,
            if (file.startsWith("file:///android_asset/")) file.substring("file:///android_asset/".length) else file
        )
    }

    companion object {
        // 用于记录已经提取过的资源，避免重复提取浪费性能（进程级，与原版 static 一致）
        private val extractHistory = HashMap<String, String>()
    }
}
