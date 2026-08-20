// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.executor

import android.content.Context
import android.os.Build
import android.os.Environment
import com.projectkr.shell.core.config.ShellRunner
import java.io.File
import java.security.MessageDigest

/**
 * 简化版 ScriptEnvironment：把 assets 中的 executor.sh 写入私有目录，
 * 并通过它执行脚本，尽量贴近原版 kr-scripts 的环境变量机制。
 */
class ScriptEnvironment(
    private val context: Context,
    private val shellRunner: ShellRunner,
) : ShellRunner {

    @Volatile
    private var executorPath: String? = null

    override fun execute(script: String): String {
        if (script.isBlank()) return ""
        ensureInitialized()
        val cacheFile = writeCache(script) ?: return ""
        val executor = executorPath ?: return ""
        return shellRunner.execute("sh \"$executor\" \"$cacheFile\"")
    }

    @Synchronized
    private fun ensureInitialized() {
        if (executorPath != null) return
        try {
            val raw = context.assets.open("kr-script/executor.sh")
                .bufferedReader()
                .readText()
                .replace("\r", "")
            val target = File(context.filesDir, "kr-script/executor.sh")
            target.parentFile?.mkdirs()
            executorPath = target.absolutePath
            val env = buildEnvironment()
            var content = raw
            env.forEach { (key, value) ->
                content = content.replace("\$({$key})", value)
            }
            target.writeText(content)
            target.setExecutable(true)
        } catch (e: Exception) {
            executorPath = ""
        }
    }

    private fun buildEnvironment(): Map<String, String> {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        return mapOf(
            "EXECUTOR_PATH" to (executorPath ?: ""),
            "START_DIR" to context.filesDir.absolutePath,
            "TEMP_DIR" to context.cacheDir.absolutePath,
            "ANDROID_UID" to context.applicationInfo.uid.toString(),
            "ANDROID_SDK" to Build.VERSION.SDK_INT.toString(),
            "SDCARD_PATH" to Environment.getExternalStorageDirectory().absolutePath,
            "BUSYBOX" to "",
            "MAGISK_PATH" to "",
            "PACKAGE_NAME" to context.packageName,
            "PACKAGE_VERSION_NAME" to (packageInfo?.versionName ?: ""),
            "PACKAGE_VERSION_CODE" to (packageInfo?.versionCode?.toString() ?: "0"),
            "APP_USER_ID" to context.applicationInfo.uid.toString(),
            "ROOT_PERMISSION" to "true",
            "TOOLKIT" to "",
        )
    }

    private fun writeCache(script: String): String? {
        return try {
            val fileName = "kr-script/cache/${md5(script)}.sh"
            val target = File(context.filesDir, fileName)
            target.parentFile?.mkdirs()
            target.writeText("#!/system/bin/sh\n\n$script")
            target.setExecutable(true)
            target.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun md5(value: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
