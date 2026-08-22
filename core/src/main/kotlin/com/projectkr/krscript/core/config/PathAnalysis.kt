// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.config

import com.projectkr.krscript.core.runtime.AssetExtractor
import com.projectkr.krscript.core.runtime.AssetSource
import com.projectkr.krscript.core.runtime.PrivateFileStore
import com.projectkr.krscript.core.runtime.ShellRunner
import java.io.File
import java.io.InputStream

/**
 * Resolves a config/resource reference to a stream, mirroring the original
 * `PathAnalysis` lookup order:
 *
 * 1. `file:///android_asset/...` refs open the asset directly.
 * 2. Absolute `/...` paths read from disk, with a root fallback that copies the
 *    file into the private dir via shell (`cp` + `chmod` + `chown`).
 * 3. Relative refs resolve against [parentDir] — assets-parent searches assets
 *    only; disk-parent tries disk (incl. root fallback), then the private dir.
 */
class PathAnalysis(
    private val assets: AssetSource,
    private val files: PrivateFileStore,
    private val shell: ShellRunner,
    private val extractor: AssetExtractor,
    private val parentDir: String = "",
    /** Linux owner used for root-fallback chown; null skips chown. */
    private val fileOwnerProvider: (() -> String?)? = null,
) {

    private var currentAbsPath: String = ""

    fun getCurrentAbsPath(): String = currentAbsPath

    /** A located config file. */
    class Located(val stream: InputStream, val absPath: String)

    fun parsePath(filePath: String): Located? {
        return try {
            if (filePath.startsWith(ASSETS_FILE)) {
                currentAbsPath = filePath
                assets.open(filePath.substring(ASSETS_FILE.length))
                    ?.let { Located(it, filePath) }
            } else {
                getFileByPath(filePath)?.let { Located(it, currentAbsPath) }
            }
        } catch (ex: Exception) {
            null
        }
    }

    /** Concatenates [target] onto [parent], resolving leading `../` and `./`. */
    fun pathConcat(parent: String, target: String): String {
        val isAssets = parent.startsWith(ASSETS_FILE)
        val parentDir = if (isAssets) parent.substring(ASSETS_FILE.length) else parent
        val parentSlices = ArrayList(parentDir.split("/"))
        if (target.startsWith("../") && parentSlices.isNotEmpty()) {
            val targetSlices = ArrayList(target.split("/"))
            while (true) {
                val step = targetSlices.firstOrNull()
                if (step != null && step == ".." && parentSlices.isNotEmpty()) {
                    parentSlices.removeAt(parentSlices.size - 1)
                    targetSlices.removeAt(0)
                } else {
                    break
                }
            }
            return pathConcat(
                (if (isAssets) ASSETS_FILE else "") + parentSlices.joinToString("/"),
                targetSlices.joinToString("/"),
            )
        }

        val joinedParent = when {
            parentDir.isEmpty() -> ""
            parentDir.endsWith("/") -> parentDir
            else -> "$parentDir/"
        }
        val cleanTarget = if (target.startsWith("./")) target.substring(2) else target
        return (if (isAssets) ASSETS_FILE else "") + joinedParent + cleanTarget
    }

    private fun useRootOpenFile(filePath: String): InputStream? {
        return try {
            if (!rootFileExists(filePath)) return null
            val cacheRel = "kr-script/outside_file.cache"
            val cachePath = files.privateFilePath(cacheRel)
            File(cachePath).parentFile?.mkdirs()
            val owner = fileOwnerProvider?.invoke()
            val cmd = StringBuilder().apply {
                append("cp -f \"$filePath\" \"$cachePath\"\n")
                append("chmod 777 \"$cachePath\"\n")
                if (!owner.isNullOrEmpty()) {
                    append("chown $owner:$owner \"$cachePath\"\n")
                }
            }.toString()
            shell.execute(cmd)
            File(cachePath).takeIf { it.exists() && it.canRead() }?.inputStream()
        } catch (ex: Exception) {
            null
        }
    }

    private fun rootFileExists(filePath: String): Boolean =
        shell.execute("[[ -e \"$filePath\" ]] && echo 1 || echo 0").trim() == "1"

    private fun findAssetsResource(filePath: String): InputStream? {
        val relativePath = pathConcat(parentDir, filePath)
        try {
            assets.open(relativePath.substring(ASSETS_FILE.length))?.let {
                currentAbsPath = relativePath
                return it
            }
        } catch (_: Exception) {
        }
        return try {
            assets.open(filePath)?.let {
                currentAbsPath = ASSETS_FILE + filePath
                it
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findDiskResource(filePath: String): InputStream? {
        if (parentDir.isNotEmpty()) {
            val relativePath = pathConcat(parentDir, filePath)
            File(relativePath).run {
                if (exists() && canRead()) {
                    currentAbsPath = absolutePath
                    return inputStream()
                }
            }
            useRootOpenFile(relativePath)?.let { return it }
        }

        val privatePath = File(pathConcat(files.privateDir(), filePath)).absolutePath
        File(privatePath).run {
            if (exists() && canRead()) {
                currentAbsPath = absolutePath
                return inputStream()
            }
        }
        useRootOpenFile(privatePath)?.let { return it }

        return null
    }

    private fun getFileByPath(filePath: String): InputStream? {
        return try {
            if (filePath.startsWith("/")) {
                currentAbsPath = filePath
                val f = File(filePath)
                if (f.exists() && f.canRead()) {
                    f.inputStream()
                } else {
                    useRootOpenFile(filePath)
                }
            } else if (parentDir.isNotEmpty() && parentDir.startsWith(ASSETS_FILE)) {
                findAssetsResource(filePath)
            } else {
                findDiskResource(filePath)
            }
        } catch (ex: Exception) {
            null
        }
    }

    companion object {
        const val ASSETS_FILE = "file:///android_asset/"
    }
}
