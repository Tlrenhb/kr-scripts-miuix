// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.exec

import com.projectkr.krscript.core.model.NodeInfoBase
import com.projectkr.krscript.core.runtime.AssetExtractor
import com.projectkr.krscript.core.runtime.AssetSource
import com.projectkr.krscript.core.runtime.PrivateFileStore
import com.projectkr.krscript.core.runtime.ShellRunner
import java.security.MessageDigest

/**
 * Prepares the executor script and runs KrScript snippets through the persistent
 * shell — a faithful port of the original `ScriptEnvironmen`, decoupled from
 * Android.
 *
 * Protocol (unchanged, keeps existing KrScript packages compatible):
 *  1. [init] reads `executor.sh` from assets, substitutes `$({KEY})` variables
 *     (including the special `$({EXECUTOR_PATH})`) and writes it to the private dir.
 *  2. [executeResult] caches inline scripts under `kr-script/cache/<md5>.sh`,
 *     exports the PAGE_* environment contract and invokes
 *     `<executor> "<script>"` via the persistent shell.
 *
 * Android-specific variables (ANDROID_SDK, SDCARD_PATH, PACKAGE_NAME…) are supplied
 * by the app layer through [variables].
 */
class ScriptEnvironment(
    private val shell: ShellRunner,
    private val assets: AssetSource,
    private val files: PrivateFileStore,
    private val extractor: AssetExtractor,
) {

    @Volatile
    private var inited = false
    private var environmentPath = ""

    fun isInited(): Boolean = inited

    /**
     * Installs the executor. [executorRef] is an asset ref (`file:///android_asset/…`)
     * or relative asset path; [toolkitDir] an optional asset dir extracted to disk.
     */
    fun init(executorRef: String, toolkitDir: String?, variables: Map<String, String>): Boolean {
        if (inited) return true
        return try {
            val executorRel = AssetExtractor.stripAssetPrefix(executorRef)
            val raw = assets.open(executorRel)?.use { it.readBytes() } ?: return false
            var text = raw.toString(Charsets.UTF_8).replace("\r", "")

            for ((key, value) in variables) {
                text = text.replace("$({$key})", value ?: "")
            }
            text = text.replace("\$({EXECUTOR_PATH})", files.privateFilePath(executorRel))

            inited = files.writePrivateFile(executorRel, text.toByteArray(Charsets.UTF_8))
            if (inited) {
                environmentPath = files.privateFilePath(executorRel)
                files.setExecutable(executorRel)
                if (!toolkitDir.isNullOrEmpty()) {
                    extractor.extractResources(toolkitDir)
                }
            }
            inited
        } catch (ex: Exception) {
            false
        }
    }

    /**
     * Runs [script] with the page context of [node]; returns the captured output
     * ("error" on shell failure), or "" for empty scripts.
     */
    fun executeResult(script: String?, node: NodeInfoBase?): String {
        if (!inited || script.isNullOrEmpty()) return ""

        val trimmed = script.trim()
        val scriptPath = if (trimmed.startsWith(ASSETS_PREFIX)) {
            extractScript(trimmed)
        } else {
            createShellCache(script)
        }

        val sb = StringBuilder()
        sb.append('\n')
        if (node != null && node.currentPageConfigPath.isNotEmpty()) {
            val parentDir = node.pageConfigDir
            val currentPath = node.currentPageConfigPath
            sb.append("export PAGE_CONFIG_DIR='").append(parentDir).append("'\n")
            sb.append("export PAGE_CONFIG_FILE='").append(currentPath).append("'\n")
            if (currentPath.startsWith(ASSETS_PREFIX)) {
                sb.append("export PAGE_WORK_DIR='")
                    .append(extractor.getExtractPath(parentDir)).append("'\n")
                sb.append("export PAGE_WORK_FILE='")
                    .append(extractor.getExtractPath(currentPath)).append("'\n")
            } else {
                sb.append("export PAGE_WORK_DIR='").append(parentDir).append("'\n")
                sb.append("export PAGE_WORK_FILE='").append(currentPath).append("'\n")
            }
        } else {
            sb.append("export PAGE_CONFIG_DIR=''\n")
            sb.append("export PAGE_CONFIG_FILE=''\n")
            sb.append("export PAGE_WORK_DIR=''\n")
            sb.append("export PAGE_WORK_FILE=''\n")
        }
        sb.append("\n\n")
        sb.append(environmentPath).append(" \"").append(scriptPath).append("\"")

        return shell.execute(sb.toString())
    }

    /**
     * Builds the `<executor> "<cached-script>" "<tag>"` invocation for a dedicated
     * streaming process (used by [com.projectkr.krscript.core.exec.ScriptProcessRunner]);
     * empty when not inited or the script is empty.
     */
    fun executorCommand(script: String, tag: String): String {
        if (!inited || script.isEmpty()) return ""
        val trimmed = script.trim()
        val cachePath = if (trimmed.startsWith(ASSETS_PREFIX)) {
            extractScript(trimmed)
        } else {
            createShellCache(script)
        }
        return "$environmentPath \"$cachePath\" \"$tag\""
    }

    /** Caches an inline script as `<md5>.sh`; returns its absolute path. */
    private fun createShellCache(script: String): String {
        val rel = "kr-script/cache/${md5(script)}.sh"
        if (files.exists(rel)) {
            return files.privateFilePath(rel)
        }
        val normalized = script
            .replace("\r\n", "\n")
            .replace("\r\t", "\t")
            .replace("\r", "\n")
        val bytes = "#!/system/bin/sh\n\n$normalized".toByteArray(Charsets.UTF_8)
        return if (files.writePrivateFile(rel, bytes)) {
            files.privateFilePath(rel)
        } else {
            ""
        }
    }

    /** Copies an asset script into the private dir; returns its absolute path. */
    private fun extractScript(assetRef: String): String {
        var rel = AssetExtractor.stripAssetPrefix(assetRef)
        if (rel.startsWith("/")) rel = rel.substring(1)
        val bytes = try {
            assets.open(rel)?.use { it.readBytes() } ?: return ""
        } catch (ex: Exception) {
            return ""
        }
        return if (files.writePrivateFile(rel, bytes)) {
            files.setExecutable(rel)
            files.privateFilePath(rel)
        } else {
            ""
        }
    }

    private fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val ASSETS_PREFIX = AssetExtractor.ASSETS_PREFIX
    }
}
