// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.shell

import android.os.Handler
import java.nio.charset.Charset

/**
 * Port of `common/src/main/java/com/omarea/common/shell/AsynSuShellUnit.kt` @ b90f2cc.
 *
 * One-shot root shell. Handler messages: what=0 (start ok/fail, obj=Boolean),
 * what=1 (stdout line), what=5 (stderr line), what=10 (exit, obj=Boolean);
 * what=-1 is delivered directly when the process could not be started.
 */
class AsynSuShellUnit(var handler: Handler) {
    var process: Process? = null

    private fun start(): AsynSuShellUnit {
        try {
            if (process == null)
                process = ShellExecutor.getSuperUserRuntime()

            Thread {
                try {
                    val reader = process!!.inputStream.bufferedReader()
                    while (true) {
                        val line = reader.readLine()?.trim()
                        if (line != null) {
                            if (line.isNotEmpty())
                                handler.sendMessage(handler.obtainMessage(1, line))
                        } else {
                            destroy()
                            break
                        }
                    }
                } catch (ex: Exception) {
                    print(ex.message)
                }
            }.start()
            Thread {
                try {
                    val reader = process!!.errorStream.bufferedReader()
                    while (true) {
                        val line = reader.readLine()?.trim() ?: break
                        if (line.isNotEmpty())
                            handler.sendMessage(handler.obtainMessage(5, line))
                    }
                } catch (ex: Exception) {
                    print(ex.message)
                }
            }.start()
            handler.sendMessage(handler.obtainMessage(0, true))
        } catch (ex: Exception) {
            handler.sendMessage(handler.obtainMessage(0, false))
        }
        return this
    }

    fun destroy() {
        try {
            if (process != null) {
                process!!.outputStream.close()
                process!!.destroy()
            }
        } catch (ex: Exception) {
        }
    }

    fun exec(cmd: String): AsynSuShellUnit {
        if (process == null) {
            start()
        }
        if (process == null) {
            handler.handleMessage(handler.obtainMessage(-1))
            return this
        }
        val outputStream = process!!.outputStream
        outputStream.write("\n\n".toByteArray(Charset.forName("UTF-8")))
        outputStream.write(cmd.toByteArray(Charset.forName("UTF-8")))
        outputStream.write("\n\n".toByteArray(Charset.forName("UTF-8")))
        outputStream.flush()
        return this
    }

    fun waitFor() {
        if (process == null)
            return

        val outputStream = process!!.outputStream
        val writer = outputStream.bufferedWriter()
        writer.write("exit\nexit\nexit\n")
        writer.write("\n\n")
        writer.flush()
        Thread {
            if (process!!.waitFor() == 0) {
                handler.sendMessage(handler.obtainMessage(10, true))
            } else {
                handler.sendMessage(handler.obtainMessage(10, false))
            }
            destroy()
        }.start()
    }

    fun waitFor(next: Runnable) {
        if (process == null)
            return

        val outputStream = process!!.outputStream
        val writer = outputStream.bufferedWriter()
        writer.write("exit\nexit\nexit\n")
        writer.write("\n\n")
        writer.flush()
        Thread {
            process!!.waitFor()
            destroy()
            handler.sendMessage(handler.obtainMessage(10, true))
            next.run()
        }.start()
    }
}
