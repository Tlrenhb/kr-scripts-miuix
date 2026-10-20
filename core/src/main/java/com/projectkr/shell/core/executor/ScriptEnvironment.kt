// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.executor

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.UserManager
import com.projectkr.shell.core.config.ShellRunner
import com.projectkr.shell.core.model.NodeInfoBase
import com.projectkr.shell.core.shell.KeepShell
import com.projectkr.shell.core.shell.KeepShellPublic
import com.projectkr.shell.core.shell.MagiskExtend
import com.projectkr.shell.core.shell.ShellTranslation
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Full port of `krscript/src/main/java/com/omarea/krscript/executor/ScriptEnvironmen.java`
 * @ b90f2cc (§3.1/§3.2 of docs/FRAMEWORK_PARITY.md).
 *
 * The original is an all-static class; this port keeps that contract in the
 * [Companion] (static init state, `kr-script-config` SharedPreferences re-init cache)
 * while retaining the rewrite's instance facade (`ScriptEnvironment(context,
 * shellRunner)` implements [ShellRunner]) so existing app call sites keep compiling.
 *
 * Instance facade notes:
 * - [execute] initializes the environment with this instance's `executor`/`toolkitDir`
 *   parameters (defaults match the original `KrScriptConfig` defaults) and then runs
 *   the script through the executor exactly like the original
 *   `executeResultRoot(context, script, null)`.
 * - A [KeepShell] passed as `shellRunner` is used as the private persistent shell;
 *   any other [ShellRunner] is ignored (parity: the original always uses its own
 *   [KeepShell] — `KeepShellPublic.getDefaultInstance()` when rooted).
 */
class ScriptEnvironment(
    private val context: Context,
    private val shellRunner: ShellRunner? = null,
    private val executor: String = DEFAULT_EXECUTOR,
    private val toolkitDir: String = DEFAULT_TOOLKIT_DIR,
) : ShellRunner {

    /** 确保环境已初始化（使用本实例的 executor/toolkitDir 参数） */
    fun ensureInitialized(): Boolean {
        (shellRunner as? KeepShell)?.let { preferredPrivateShell = it }
        return init(context, executor, toolkitDir)
    }

    override fun execute(script: String): String {
        return execute(script, null)
    }

    /**
     * 通过执行器运行脚本并返回输出（同步）。
     * [node] 提供 PAGE_CONFIG_DIR/FILE 与 PAGE_WORK_DIR/FILE 导出（§3.2）。
     */
    fun execute(script: String, node: NodeInfoBase?): String {
        ensureInitialized()
        return executeResultRoot(context, script, node)
    }

    /** 执行器（executor.sh 写出后的私有目录绝对路径），未初始化时为 "" */
    val executorPath: String
        get() {
            ensureInitialized()
            return environmentPath
        }

    val isRooted: Boolean
        get() {
            ensureInitialized()
            return rooted
        }

    companion object {
        const val ASSETS_FILE = "file:///android_asset/"
        const val DEFAULT_EXECUTOR = "file:///android_asset/kr-script/executor.sh"
        const val DEFAULT_TOOLKIT_DIR = "file:///android_asset/kr-script/toolkit"

        @Volatile
        private var inited = false
        private var environmentPath = ""

        // 此目录将添加到PATH尾部，作为应用程序提供的拓展程序库目录，如有需要则需要在初始化executor.sh之前为该变量赋值
        private var TOOKIT_DIR = ""

        @Volatile
        private var rooted = false
        private var privateShell: KeepShell? = null
        private var shellTranslation: ShellTranslation? = null

        /** 实例门面注入的私有 shell（仅在 init 前生效） */
        private var preferredPrivateShell: KeepShell? = null

        @JvmStatic
        fun isInited(): Boolean {
            return inited
        }

        /** 已获取 root 权限（init 时通过 [KeepShellPublic.checkRoot] 真实检测） */
        fun isRooted(): Boolean = rooted

        /** executor.sh 写出后的私有目录绝对路径 */
        fun getExecutorPath(): String = environmentPath

        private fun init(context: Context): Boolean {
            val configSpf = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE)

            return init(
                context,
                configSpf.getString("executor", "kr-script/executor.sh")!!,
                configSpf.getString("toolkitDir", "kr-script/toolkit")
            )
        }

        /**
         * 初始化执行器
         *
         * @param context  Context
         * @param executor 执行器在Assets中的位置
         * @param toolkitDir 工具箱在Assets中的位置（可为 null）
         * @return 是否初始化成功
         */
        @Synchronized
        fun init(context: Context, executor: String, toolkitDir: String?): Boolean {
            if (inited) {
                return true
            }

            shellTranslation = ShellTranslation(context.applicationContext)
            rooted = KeepShellPublic.checkRoot()

            try {
                if (!toolkitDir.isNullOrEmpty()) {
                    TOOKIT_DIR = ExtractAssets(context).extractResources(toolkitDir) ?: ""
                }

                var fileName = executor
                if (fileName.startsWith(ASSETS_FILE)) {
                    fileName = fileName.substring(ASSETS_FILE.length)
                }

                val inputStream = context.assets.open(fileName)
                val bytes = ByteArray(inputStream.available())
                inputStream.read(bytes, 0, bytes.size)
                var envShell = String(bytes, charset = Charset.defaultCharset()).replace("\r", "")

                val environment = getEnvironment(context)
                for (key in environment.keys) {
                    var value = environment[key]
                    if (value == null) {
                        value = ""
                    }
                    envShell = envShell.replace("\$({$key})", value)
                }
                val outputPathAbs = FileWrite.getPrivateFilePath(context, fileName)
                envShell = envShell.replace("\$({EXECUTOR_PATH})", outputPathAbs)

                inited = FileWrite.writePrivateFile(envShell.toByteArray(Charset.defaultCharset()), fileName, context)
                if (inited) {
                    environmentPath = outputPathAbs
                }

                val configSpf = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE).edit()
                configSpf.putString("executor", executor)
                configSpf.putString("toolkitDir", toolkitDir)
                configSpf.apply()

                privateShell = preferredPrivateShell
                    ?: if (rooted) KeepShellPublic.getDefaultInstance() else KeepShell(rooted)

                return inited
            } catch (ex: Exception) {
                return false
            }
        }

        private fun md5(string: String): String {
            if (string.isEmpty()) {
                return ""
            }

            try {
                val md5 = MessageDigest.getInstance("MD5")
                val bytes = md5.digest(string.toByteArray())
                val result = StringBuilder()
                for (b in bytes) {
                    var temp = Integer.toHexString(b.toInt() and 0xff)
                    if (temp.length == 1) {
                        temp = "0$temp"
                    }
                    result.append(temp)
                }
                return result.toString()
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            }

            return ""
        }

        /**
         * 写入缓存（脚本代码存入脚本文件）
         */
        private fun createShellCache(context: Context, script: String): String {
            val md5 = md5(script)
            val outputPath = "kr-script/cache/$md5.sh"
            // 原版怪癖：检查的是相对路径（几乎永远不存在），且命中时返回相对路径
            if (File(outputPath).exists()) {
                return outputPath
            }

            val bytes = ("#!/system/bin/sh\n\n$script")
                .replace("\r\n", "\n")
                .replace("\r\t", "\t")
                .replace("\r", "\n")
                .toByteArray()
            if (FileWrite.writePrivateFile(bytes, outputPath, context)) {
                return FileWrite.getPrivateFilePath(context, outputPath)
            }
            return ""
        }

        /**
         * 执行脚本（将 assets 中的脚本提取为私有目录文件）
         */
        private fun extractScript(context: Context, fileNameIn: String): String? {
            var fileName = fileNameIn
            if (fileName.startsWith(ASSETS_FILE)) {
                fileName = fileName.substring(ASSETS_FILE.length)
            }
            return FileWrite.writePrivateShellFile(fileName, fileName, context)
        }

        fun executeResultRoot(context: Context, script: String?, nodeInfoBase: NodeInfoBase?): String {
            if (!inited) {
                init(context)
            }

            if (script.isNullOrEmpty()) {
                return ""
            }

            val script2 = script.trim()
            val path: String? = if (script2.startsWith(ASSETS_FILE)) {
                extractScript(context, script2)
            } else {
                createShellCache(context, script)
            }

            if (!inited) {
                init(context)
            }

            val stringBuilder = StringBuilder()

            stringBuilder.append("\n")
            if (nodeInfoBase != null && nodeInfoBase.currentPageConfigPath.isNotEmpty()) {
                val parentPageConfigDir = nodeInfoBase.pageConfigDir
                val currentPageConfigPath = nodeInfoBase.currentPageConfigPath
                stringBuilder.append("export PAGE_CONFIG_DIR='").append(parentPageConfigDir).append("'\n")
                stringBuilder.append("export PAGE_CONFIG_FILE='").append(currentPageConfigPath).append("'\n")

                if (currentPageConfigPath.startsWith("file:///android_asset/")) {
                    stringBuilder.append("export PAGE_WORK_DIR='").append(ExtractAssets(context).getExtractPath(parentPageConfigDir)).append("'\n")
                    stringBuilder.append("export PAGE_WORK_FILE='").append(ExtractAssets(context).getExtractPath(currentPageConfigPath)).append("'\n")
                } else {
                    stringBuilder.append("export PAGE_WORK_DIR='").append(parentPageConfigDir).append("'\n")
                    stringBuilder.append("export PAGE_WORK_FILE='").append(currentPageConfigPath).append("'\n")
                }
            } else {
                stringBuilder.append("export PAGE_CONFIG_DIR=''\n")
                stringBuilder.append("export PAGE_CONFIG_FILE=''\n")
                stringBuilder.append("export PAGE_WORK_DIR=''\n")
                // 原版怪癖：else 分支导出了两次 PAGE_WORK_DIR，从未导出 PAGE_WORK_FILE
                stringBuilder.append("export PAGE_WORK_DIR=''\n")
            }

            stringBuilder.append("\n\n")
            stringBuilder.append(environmentPath).append(" \"").append(path).append("\"")

            val shell = privateShell ?: KeepShell(rooted).also { privateShell = it }
            val result = shell.doCmdSync(stringBuilder.toString())
            return shellTranslation?.resolveRow(result) ?: result
        }

        private fun getStartPath(context: Context): String {
            val dir = FileWrite.getPrivateFileDir(context)
            return if (dir.endsWith("/")) {
                dir.substring(0, dir.length - 1)
            } else {
                dir
            }
        }

        /**
         * 获取应用的用户 Serial（如 0）——对应原版 FileOwner.getUserId()
         */
        private fun getUserSerial(context: Context): Int {
            val um = context.getSystemService(Context.USER_SERVICE) as UserManager
            val userHandle = Process.myUserHandle()

            var value = 0
            try {
                value = um.getSerialNumberForUser(userHandle).toInt()
            } catch (ignored: Exception) {
            }
            return value
        }

        /**
         * 获取框架的环境变量
         */
        private fun getEnvironment(context: Context): HashMap<String, String> {
            val params = HashMap<String, String>()

            params["TOOLKIT"] = TOOKIT_DIR
            if (MagiskExtend.moduleInstalled()) {
                val magiskPath = MagiskExtend.MAGISK_PATH
                params["MAGISK_PATH"] =
                    if (magiskPath.endsWith("/")) magiskPath.substring(0, magiskPath.length - 1) else magiskPath
            } else {
                params["MAGISK_PATH"] = ""
            }
            params["START_DIR"] = getStartPath(context)
            params["TEMP_DIR"] = context.cacheDir.absolutePath

            val androidUid = getUserSerial(context)
            params["ANDROID_UID"] = "" + androidUid

            try {
                // @ https://blog.csdn.net/Gaugamela/article/details/78689580
                // 100000 => UserHandle.PER_USER_RANGE
                params["APP_USER_ID"] =
                    "u" + androidUid + "_a" + ((Process.myUid() % 100000) - Process.FIRST_APPLICATION_UID)
            } catch (ignored: Exception) {
            }

            params["ANDROID_SDK"] = "" + Build.VERSION.SDK_INT
            params["ROOT_PERMISSION"] = if (rooted) "true" else "false"
            params["SDCARD_PATH"] = Environment.getExternalStorageDirectory().absolutePath
            val busyboxPath = FileWrite.getPrivateFilePath(context, "busybox")
            if (File(FileWrite.getPrivateFilePath(context, "busybox")).exists()) {
                params["BUSYBOX"] = busyboxPath
            } else {
                params["BUSYBOX"] = "busybox"
            }
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                params["PACKAGE_NAME"] = context.packageName
                params["PACKAGE_VERSION_NAME"] = packageInfo.versionName ?: ""
                params["PACKAGE_VERSION_CODE"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    "" + packageInfo.longVersionCode
                } else {
                    "" + packageInfo.versionCode
                }
            } catch (ex: Exception) {
            }

            return params
        }

        private fun getVariables(params: HashMap<String, String>?): ArrayList<String> {
            val envp = ArrayList<String>()

            if (params != null) {
                for (key in params.keys) {
                    var value = params[key]
                    if (value == null) {
                        value = ""
                    }
                    envp.add(key + "='" + value.replace("'", "'\\''") + "'")
                }
            }

            return envp
        }

        private fun getExecuteScript(context: Context, script: String?, tag: String): String {
            if (!inited) {
                init(context)
            }

            if (script.isNullOrEmpty()) {
                return ""
            }

            val script2 = script.trim()
            var cachePath: String? = ""
            if (script2.startsWith(ASSETS_FILE)) {
                cachePath = extractScript(context, script2)
                if (cachePath == null) {
                    cachePath = script
                }
            } else {
                cachePath = createShellCache(context, script)
            }

            return environmentPath + " \"" + cachePath + "\" \"" + tag + "\""
        }

        fun getRuntime(): Process? {
            return try {
                if (rooted) {
                    Runtime.getRuntime().exec("su")
                } else {
                    Runtime.getRuntime().exec("sh")
                }
            } catch (ex: Exception) {
                null
            }
        }

        /**
         * 使用执行器运行脚本
         *
         * @param context          Context
         * @param dataOutputStream Runtime进程的输出流
         * @param cmds             要执行的脚本
         * @param params           参数列表
         * @param nodeInfo         提供 PAGE_CONFIG_* / PAGE_WORK_* 导出的节点（可为 null）
         * @param tag              会话标签（executor.sh 的 $2）
         */
        fun executeShell(
            context: Context,
            dataOutputStream: DataOutputStream,
            cmds: String,
            params: HashMap<String, String>?,
            nodeInfo: NodeInfoBase?,
            tag: String,
        ) {
            val effectiveParams = params ?: HashMap()

            // 页面配置文件路径
            if (nodeInfo != null) {
                val parentPageConfigDir = nodeInfo.pageConfigDir
                val currentPageConfigPath = nodeInfo.currentPageConfigPath
                effectiveParams["PAGE_CONFIG_DIR"] = parentPageConfigDir
                effectiveParams["PAGE_CONFIG_FILE"] = currentPageConfigPath
                if (currentPageConfigPath.startsWith("file:///android_asset/")) {
                    effectiveParams["PAGE_WORK_DIR"] = ExtractAssets(context).getExtractPath(parentPageConfigDir)
                    effectiveParams["PAGE_WORK_FILE"] = ExtractAssets(context).getExtractPath(currentPageConfigPath)
                } else {
                    effectiveParams["PAGE_WORK_DIR"] = parentPageConfigDir
                    effectiveParams["PAGE_WORK_FILE"] = currentPageConfigPath
                }
            } else {
                effectiveParams["PAGE_CONFIG_DIR"] = ""
                effectiveParams["PAGE_CONFIG_FILE"] = ""
                effectiveParams["PAGE_WORK_DIR"] = ""
                effectiveParams["PAGE_WORK_FILE"] = ""
            }

            val envp = getVariables(effectiveParams)
            val envpCmds = StringBuilder()
            if (envp.size > 0) {
                for (param in envp) {
                    envpCmds.append("export ").append(param).append("\n")
                }
            }
            try {
                dataOutputStream.write(envpCmds.toString().toByteArray(charset("UTF-8")))

                dataOutputStream.write(getExecuteScript(context, cmds, tag).toByteArray(charset("UTF-8")))

                dataOutputStream.writeBytes("\n\n")
                dataOutputStream.writeBytes("sleep 0.2;\n")
                dataOutputStream.writeBytes("exit\n")
                dataOutputStream.writeBytes("exit\n")
                dataOutputStream.flush()
            } catch (ignored: Exception) {
            }
        }
    }
}
