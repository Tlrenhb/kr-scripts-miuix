// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.shell

import java.io.IOException

/**
 * Port of `common/src/main/java/com/omarea/common/shell/ShellExecutor.java` @ b90f2cc.
 *
 * Creates raw `su`/`sh` processes. When [setExtraEnvPath] was called, the current
 * `sh` PATH is probed once and `export PATH=<default>:<extra>` is written into every
 * new process via stdin. The original deliberately does NOT use the exec env array
 * (device crash workaround — see the commented-out block in the original source).
 */
object ShellExecutor {
    private var extraEnvPath: String = ""
    private var defaultEnvPath: String = "" // /sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin

    @JvmStatic
    fun setExtraEnvPath(extraEnvPath: String) {
        ShellExecutor.extraEnvPath = extraEnvPath
    }

    private fun getEnvPath(): String? {
        // FIXME:非root模式下，默认的 TMPDIR=/data/local/tmp 变量可能会导致某些需要写缓存的场景（例如使用source指令）脚本执行失败！
        if (extraEnvPath.isNotEmpty()) {
            if (defaultEnvPath.isEmpty()) {
                try {
                    val process = Runtime.getRuntime().exec("sh")
                    val outputStream = process.outputStream
                    outputStream.write("echo \$PATH".toByteArray())
                    outputStream.flush()
                    outputStream.close()

                    val inputStream = process.inputStream
                    val cache = ByteArray(16384)
                    val length = inputStream.read(cache)
                    inputStream.close()
                    process.destroy()

                    val path = String(cache, 0, if (length > 0) length else 0).trim()
                    if (path.isNotEmpty()) {
                        defaultEnvPath = path
                    } else {
                        throw RuntimeException("未能获取到\$PATH参数")
                    }
                } catch (ex: Exception) {
                    defaultEnvPath = "/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin"
                }
            }

            val path = defaultEnvPath
            return "PATH=$path:$extraEnvPath"
        }

        return null
    }

    @Throws(IOException::class)
    private fun getProcess(run: String): Process {
        val env = getEnvPath()
        val runtime = Runtime.getRuntime()
        /*
        // 部分机型会有Aborted错误
        if (env != null) {
            return runtime.exec(run, arrayOf(env))
        }
        */
        val process = runtime.exec(run)
        if (env != null) {
            val outputStream = process.outputStream
            outputStream.write("export ".toByteArray())
            outputStream.write(env.toByteArray())
            outputStream.write("\n".toByteArray())
            outputStream.flush()
        }
        return process
    }

    @Throws(IOException::class)
    fun getSuperUserRuntime(): Process {
        return getProcess("su")
    }

    @Throws(IOException::class)
    fun getRuntime(): Process {
        return getProcess("sh")
    }
}
