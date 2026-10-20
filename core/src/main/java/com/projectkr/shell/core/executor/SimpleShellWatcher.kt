// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.executor

import android.content.Context
import com.projectkr.shell.core.shell.ShellTranslation
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Port of `krscript/src/main/java/com/omarea/krscript/executor/SimpleShellWatcher.java` @ b90f2cc.
 *
 * Three threads per process: stdout reader → [ShellHandlerBase.EVENT_REDE] per line
 * (with `\n` appended, after [ShellTranslation.resolveRow]); stderr reader →
 * [ShellHandlerBase.EVENT_READ_ERROR]; wait thread → [ShellHandlerBase.EVENT_EXIT]
 * carrying `process.waitFor()`, then interrupts both readers and runs [onExit].
 */
class SimpleShellWatcher {

    /**
     * 设置日志处理Handler
     *
     * @param process          Runtime进程
     * @param shellHandlerBase ShellHandlerBase
     * @param onExit           进程结束后回调
     */
    fun setHandler(context: Context, process: Process, shellHandlerBase: ShellHandlerBase, onExit: Runnable?) {
        val shellTranslation = ShellTranslation(context)

        val inputStream = process.inputStream
        val errorStream = process.errorStream
        val reader = Thread {
            var line: String?
            try {
                val bufferedReader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                while (true) {
                    line = bufferedReader.readLine()
                    if (line == null) {
                        break
                    } else {
                        shellHandlerBase.sendMessage(
                            shellHandlerBase.obtainMessage(ShellHandlerBase.EVENT_REDE, shellTranslation.resolveRow(line) + "\n")
                        )
                    }
                }
            } catch (ignored: Exception) {
            }
        }
        val readerError = Thread {
            var line: String?
            try {
                val bufferedReader = BufferedReader(InputStreamReader(errorStream, "UTF-8"))
                while (true) {
                    line = bufferedReader.readLine()
                    if (line == null) {
                        break
                    } else {
                        shellHandlerBase.sendMessage(
                            shellHandlerBase.obtainMessage(ShellHandlerBase.EVENT_READ_ERROR, shellTranslation.resolveRow(line) + "\n")
                        )
                    }
                }
            } catch (ignored: Exception) {
            }
        }
        val waitExit = Thread {
            var status = -1
            try {
                status = process.waitFor()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } finally {
                shellHandlerBase.sendMessage(shellHandlerBase.obtainMessage(ShellHandlerBase.EVENT_EXIT, status))
                if (reader.isAlive) {
                    reader.interrupt()
                }
                if (readerError.isAlive) {
                    readerError.interrupt()
                }
                onExit?.run()
            }
        }

        reader.start()
        readerError.start()
        waitExit.start()
    }
}
