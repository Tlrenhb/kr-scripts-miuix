// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.config

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * 页面配置/资源路径解析（完整移植 b90f2cc `krscript/.../config/PathAnalysis.kt`）。
 *
 * 解析规则：
 * - `file:///android_asset/…` → 直接从 assets 打开，absPath 原样记录
 * - 绝对路径 `/…` → 直接 File 读取；失败时回退 [useRootOpenFile]（root `cp` 到私有目录再读）
 * - 相对路径且父目录在 assets → assets 内拼接（[pathConcat] 支持 `../`、`./`），回退 assets 根查找
 * - 相对路径且父目录在磁盘/为空 → 父目录拼接（直接读，再 root 回退）→ 应用私有目录拼接（直接读，再 root 回退）
 *
 * 与原版差异：原版 root 回退依赖 common 模块的 `KeepShellPublic`/`RootFile`（常驻 root shell），
 * 重写版通过构造参数 [rootShell] 注入等价能力；[rootShell] 为 null 时跳过 root 回退（保持旧的
 * 无 root 行为）。
 */
class PathAnalysis(
    private val context: Context,
    private val parentDir: String = "",
    private val rootShell: ShellRunner? = null,
) {
    private val assetsFile = "file:///android_asset/"

    // 解析路径时自动获得
    private var currentAbsPath: String = ""

    fun getCurrentAbsPath(): String {
        return currentAbsPath
    }

    fun parsePath(filePath: String): InputStream? {
        try {
            if (filePath.startsWith(assetsFile)) {
                currentAbsPath = filePath
                return context.assets.open(filePath.substring(assetsFile.length))
            } else {
                return getFileByPath(filePath)
            }
        } catch (ex: Exception) {
            return null
        }
    }

    // 处理 ../ 、./
    private fun pathConcat(parent: String, target: String): String {
        val isAssets = parent.startsWith(assetsFile)
        val parentPath = if (isAssets) parent.substring(assetsFile.length) else parent
        val parentSlices = ArrayList(parentPath.split("/"))
        if (target.startsWith("../") && parentSlices.size > 0) {
            val targetSlices = ArrayList(target.split("/"))
            while (true) {
                val step = targetSlices.firstOrNull()
                if (step != null && step == ".." && parentSlices.size > 0) {
                    parentSlices.removeAt(parentSlices.size - 1)
                    targetSlices.removeAt(0)
                } else {
                    break
                }
            }
            return pathConcat(
                (if (isAssets) assetsFile else "") + parentSlices.joinToString("/"),
                targetSlices.joinToString("/")
            )
        }

        return (if (isAssets) assetsFile else "") + (when {
            !(parentPath.isEmpty() || parentPath.endsWith("/")) -> parentPath + "/"
            else -> parentPath
        } + (if (target.startsWith("./")) target.substring(2) else target))
    }

    private fun useRootOpenFile(filePath: String): InputStream? {
        val shell = rootShell ?: return null
        // RootFile.fileExists：root 下探测目标文件存在
        if (shell.execute("if [[ -f \"$filePath\" ]]; then echo 1; fi;") == "1") {
            val dir = File(getPrivateFilePath("kr-script"))
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val cachePath = getPrivateFilePath("kr-script/outside_file.cache")
            val fileOwner = FileOwner(context).getFileOwner()
            shell.execute(
                "cp -f \"$filePath\" \"$cachePath\"\n" +
                    "chmod 777 \"$cachePath\"\n" +
                    "chown $fileOwner:$fileOwner \"$cachePath\"\n"
            )
            File(cachePath).run {
                if (exists() && canRead()) {
                    return inputStream()
                }
            }
        }
        return null
    }

    // 在assets里查找文件
    private fun findAssetsResource(filePath: String): InputStream? {
        // 解析成绝对路径
        val relativePath = pathConcat(parentDir, filePath)
        try {
            try {
                // 首先在assets里查找相对路径
                val simplePath = relativePath.substring(assetsFile.length)
                context.assets.open(simplePath).run {
                    currentAbsPath = relativePath
                    return this
                }
            } catch (ex: Exception) {
                // 然后再尝试在assets里查找绝对路径
                context.assets.open(filePath).run {
                    currentAbsPath = assetsFile + filePath
                    return this
                }
            }
        } catch (ex: Exception) {
            return null
        }
    }

    // 在磁盘上查找文件
    private fun findDiskResource(filePath: String): InputStream? {
        if (parentDir.isNotEmpty()) {
            // 解析成绝对路径
            val relativePath = pathConcat(parentDir, filePath)
            // 尝试使用普通权限读取文件
            File(relativePath).run {
                if (exists() && canRead()) {
                    currentAbsPath = absolutePath
                    return inputStream()
                }
            }
            useRootOpenFile(relativePath)?.run {
                return this
            }
        }

        // 路径相对于当前配置文件没找到文件的话，继续查找相对于数据文件根目录的文件
        val privatePath = File(pathConcat(getPrivateFileDir(), filePath)).absolutePath
        File(privatePath).run {
            if (exists() && canRead()) {
                currentAbsPath = absolutePath
                return inputStream()
            }
        }
        useRootOpenFile(privatePath)?.run {
            return this
        }

        return null
    }

    private fun getFileByPath(filePath: String): InputStream? {
        try {
            if (filePath.startsWith("/")) {
                currentAbsPath = filePath
                val javaFileInfo = File(filePath)
                if (javaFileInfo.exists() && javaFileInfo.canRead()) {
                    return javaFileInfo.inputStream()
                } else {
                    return useRootOpenFile(filePath)
                }
            } else {
                // 如果当前配置文件来源于 assets，则查找依赖资源时也只去assets查找
                if (parentDir.isNotEmpty() && parentDir.startsWith(assetsFile)) {
                    return findAssetsResource(filePath)
                } else {
                    return findDiskResource(filePath)
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    // FileWrite.getPrivateFileDir
    private fun getPrivateFileDir(): String {
        return context.filesDir.absolutePath + "/"
    }

    // FileWrite.getPrivateFilePath
    private fun getPrivateFilePath(outName: String): String {
        return getPrivateFileDir() + (if (outName.startsWith("/")) outName.substring(1) else outName)
    }
}
