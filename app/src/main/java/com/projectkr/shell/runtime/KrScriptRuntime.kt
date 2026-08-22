// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.runtime

import android.content.Context
import android.os.Build
import android.os.Environment
import com.projectkr.krscript.core.config.ScriptEvaluator
import com.projectkr.krscript.core.exec.ScriptEnvironment

import com.projectkr.krscript.core.runtime.KeepShellRunner
import com.projectkr.krscript.core.runtime.PrivateFileStore
import com.projectkr.krscript.core.runtime.ShellRunner
import java.io.File
import java.io.InputStream

/**
 * Application-scoped bridge between the pure-JVM `:core` engine and Android.
 * Initialized once from [KrApplication.onCreate].
 */
object KrScriptRuntime {

    lateinit var shell: KeepShellRunner
        private set

    lateinit var scriptEnv: ScriptEnvironment
        private set

    lateinit var extractor: com.projectkr.krscript.core.runtime.AssetExtractor
        private set

    lateinit var assetSource: com.projectkr.krscript.core.runtime.AssetSource
        private set

    lateinit var fileStore: PrivateFileStore
        private set

    val rooted: Boolean get() = shell.rooted

    /** True once [init] has produced the script environment. */
    val isReady: Boolean get() = this::scriptEnv.isInitialized

    /** Evaluator used by PageConfigReader while parsing configs. */
    val evaluator = ScriptEvaluator { script, node ->
        scriptEnv.executeResult(script, node)
    }

    private lateinit var appContext: Context

    fun init(context: Context): Boolean {
        if (this::scriptEnv.isInitialized) return true
        appContext = context.applicationContext

        shell = KeepShellRunner.createWithFallback()

        val assets = object : com.projectkr.krscript.core.runtime.AssetSource {
            override fun open(path: String): InputStream? =
                try {
                    appContext.assets.open(path)
                } catch (ex: Exception) {
                    null
                }

            override fun list(dir: String): List<String>? =
                try {
                    appContext.assets.list(dir)?.toList()
                } catch (ex: Exception) {
                    null
                }
        }

        val files = object : PrivateFileStore {
            override fun privateDir(): String = appContext.filesDir.absolutePath

            override fun writePrivateFile(relPath: String, bytes: ByteArray): Boolean =
                try {
                    val f = File(appContext.filesDir, relPath)
                    f.parentFile?.mkdirs()
                    f.writeBytes(bytes)
                    true
                } catch (ex: Exception) {
                    false
                }

            override fun privateFilePath(relPath: String): String =
                File(appContext.filesDir, relPath).absolutePath

            override fun exists(relPath: String): Boolean =
                File(appContext.filesDir, relPath).exists()

            override fun setExecutable(relPath: String): Boolean =
                File(appContext.filesDir, relPath).setExecutable(true)
        }

        val extractorImpl = com.projectkr.krscript.core.runtime.DefaultAssetExtractor(assets, files)
        extractor = extractorImpl
        assetSource = assets
        fileStore = files
        scriptEnv = ScriptEnvironment(shell, assets, files, extractorImpl)

        // kr-script.conf drives executor/toolkit selection; fall back to defaults.
        val conf = runCatching {
            appContext.assets.open("kr-script.conf").bufferedReader().readText()
        }.getOrNull().orEmpty()

        val confMap = com.projectkr.krscript.core.config.ConfReader().parse(conf)

        return scriptEnv.init(
            executorRef = confMap.getOrDefault(
                com.projectkr.krscript.core.config.ConfReader.EXECUTOR_CORE,
                "file:///android_asset/kr-script/executor.sh",
            ),
            toolkitDir = confMap[com.projectkr.krscript.core.config.ConfReader.TOOLKIT_DIR],
            variables = buildVariables(),
        )
    }

    /**
     * Environment variables substituted into executor.sh — the same contract as
     * the original ScriptEnvironmen.
     */
    private fun buildVariables(): Map<String, String> {
        val pm = appContext.packageManager
        val packageInfo = runCatching {
            pm.getPackageInfo(appContext.packageName, 0)
        }.getOrNull()

        val map = LinkedHashMap<String, String>()
        map["TOOLKIT"] = ""
        map["MAGISK_PATH"] = ""
        map["START_DIR"] = appContext.filesDir.absolutePath.trimEnd('/')
        map["TEMP_DIR"] = appContext.cacheDir.absolutePath
        map["ANDROID_UID"] = android.os.Process.myUid().toString()
        // Best-effort linux owner id (uid:gid style), like the original FileOwner.
        map["APP_USER_ID"] = runCatching {
            val uid = android.os.Process.myUid()
            "$uid:$uid"
        }.getOrDefault("")
        map["ANDROID_SDK"] = Build.VERSION.SDK_INT.toString()
        map["ROOT_PERMISSION"] = rooted.toString()
        map["SDCARD_PATH"] =
            Environment.getExternalStorageDirectory()?.absolutePath ?: ""
        val busybox = File(appContext.filesDir, "busybox")
        map["BUSYBOX"] = if (busybox.exists()) busybox.absolutePath else "busybox"
        map["PACKAGE_NAME"] = appContext.packageName
        map["PACKAGE_VERSION_NAME"] = packageInfo?.versionName ?: ""
        map["PACKAGE_VERSION_CODE"] = packageInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                it.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                it.versionCode.toString()
            }
        } ?: ""
        return map
    }
}
