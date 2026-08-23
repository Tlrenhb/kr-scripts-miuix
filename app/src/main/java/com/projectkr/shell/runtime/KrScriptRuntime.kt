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

    /** True once [init] has produced the script environment (observable). */
    var isReady by androidx.compose.runtime.mutableStateOf(false)
        private set

    /** Original kr-script.conf key: hide the dashboard tab when "0". */
    var allowHomePage by androidx.compose.runtime.mutableStateOf(true)
        private set

    /** Evaluator used by PageConfigReader while parsing configs (@string translated). */
    val evaluator = ScriptEvaluator { script, node ->
        ShellTranslation.resolveRow(appContext, scriptEnv.executeResult(script, node))
    }

    lateinit var appContext: Context
        private set

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

        allowHomePage = confMap.getOrDefault(
            com.projectkr.krscript.core.config.ConfReader.ALLOW_HOME_PAGE,
            com.projectkr.krscript.core.config.ConfReader.ALLOW_HOME_PAGE_DEFAULT,
        ) == "1"

        val toolkitDir = confMap[com.projectkr.krscript.core.config.ConfReader.TOOLKIT_DIR]
        val variables = buildVariables().toMutableMap()
        // TOOLKIT points at the extracted toolkit dir (original ScriptEnvironmen).
        if (!toolkitDir.isNullOrEmpty()) {
            variables["TOOLKIT"] = extractor.getExtractPath(toolkitDir)
        }
        // MAGISK_PATH: probe the well-known module roots through root shell.
        variables["MAGISK_PATH"] = detectMagiskPath()

        val ok = scriptEnv.init(
            executorRef = confMap.getOrDefault(
                com.projectkr.krscript.core.config.ConfReader.EXECUTOR_CORE,
                "file:///android_asset/kr-script/executor.sh",
            ),
            toolkitDir = toolkitDir,
            variables = variables,
        )

        // before_start_sh runs once after the engine is ready (original key).
        confMap[com.projectkr.krscript.core.config.ConfReader.BEFORE_START_SH]
            ?.takeIf { it.isNotEmpty() }
            ?.let { scriptEnv.executeResult(it, null) }

        isReady = ok
        return ok
    }

    /** Probes Magisk module roots (values from the original MagiskExtend). */
    private fun detectMagiskPath(): String {
        for (path in listOf("/data/adb/modules", "/sbin/.core/img")) {
            val check = shell.execute("[[ -d '$path' ]] && echo 1 || echo 0")
            if (check.trim() == "1") {
                return path.trimEnd('/')
            }
        }
        return ""
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
        // TOOLKIT / MAGISK_PATH are filled by init() after extraction/probing.
        map["TOOLKIT"] = ""
        map["MAGISK_PATH"] = ""
        map["START_DIR"] = appContext.filesDir.absolutePath.trimEnd('/')
        map["TEMP_DIR"] = appContext.cacheDir.absolutePath
        // Original FileOwner semantics: serial of the current user handle and
        // the u<serial>_a<appId> owner string used by chown/pm --user.
        val userSerial = runCatching {
            val um = appContext.getSystemService(Context.USER_SERVICE) as android.os.UserManager
            um.getSerialNumberForUser(android.os.Process.myUserHandle())
        }.getOrDefault(0L)
        map["ANDROID_UID"] = userSerial.toString()
        map["APP_USER_ID"] = runCatching {
            val appId = android.os.Process.myUid() % 100000 - 10000
            "u${userSerial}_a$appId"
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
