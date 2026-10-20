// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.shell

import android.util.Log
import com.projectkr.shell.core.config.ShellRunner
import java.io.BufferedReader
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

/**
 * Port of `common/src/main/java/com/omarea/common/shell/KeepShell.kt` @ b90f2cc.
 *
 * Persistent `su`/`sh` process with a marker-separated command protocol:
 * every command is wrapped between `echo '|SH>>|'` / `echo '|<<SH|'` and the
 * captured stdout between the markers is returned (trimmed), `"error"` on failure.
 *
 * Threading: the original uses one [ReentrantLock] (NOT a single global
 * `@Synchronized`); when the lock has been held longer than [LOCK_TIMEOUT] the next
 * caller force-exits the wedged shell instead of blocking forever. Stdin writes are
 * dispatched on a background executor (the original used `Dispatchers.IO`; this port
 * uses a dedicated single-thread executor so consecutive commands keep their order —
 * the core module has no coroutines dependency).
 */
class KeepShell(private var rootMode: Boolean = true) : ShellRunner {
    private var p: Process? = null
    private var out: OutputStream? = null
    private var reader: BufferedReader? = null

    @Volatile
    private var currentIsIdle = true // 是否处于闲置状态

    val isIdle: Boolean
        get() = currentIsIdle

    // 后台写出线程（替代原版的 GlobalScope.launch(Dispatchers.IO)，保证同一实例上命令按序写入）
    private val writeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KeepShell-Writer").apply { isDaemon = true }
    }

    /** 尝试退出命令行程序 */
    fun tryExit() {
        try {
            out?.close()
            reader?.close()
        } catch (ex: Exception) {
        }
        try {
            p?.destroy()
        } catch (ex: Exception) {
        }
        enterLockTime = 0L
        out = null
        reader = null
        p = null
        currentIsIdle = true
    }

    // 获取ROOT超时时间（原版声明该常量，实际 spawn 等待使用 join(10000)）
    private val GET_ROOT_TIMEOUT = 20000L
    private val mLock = ReentrantLock()
    private val LOCK_TIMEOUT = 10000L

    @Volatile
    private var enterLockTime = 0L

    private val checkRootState =
        // "if [[ \$(id -u 2>&1) == '0' ]] || [[ \$(\$UID) == '0' ]] || [[ \$(whoami 2>&1) == 'root' ]] || [[ \$(\$USER_ID) == '0' ]]; then\n" +
        "if [[ \$(id -u 2>&1) == '0' ]] || [[ \$(\$UID) == '0' ]] || [[ \$(whoami 2>&1) == 'root' ]] || [[ \$(set | grep 'USER_ID=0') == 'USER_ID=0' ]]; then\n" +
                "  echo 'success'\n" +
                "else\n" +
                "if [[ -d /cache ]]; then\n" +
                "  echo 1 > /cache/vtools_root\n" +
                "  if [[ -f /cache/vtools_root ]] && [[ \$(cat /cache/vtools_root) == '1' ]]; then\n" +
                "    echo 'success'\n" +
                "    rm -rf /cache/vtools_root\n" +
                "    return\n" +
                "  fi\n" +
                "fi\n" +
                "exit 1\n" +
                "exit 1\n" +
                "fi\n"

    fun checkRoot(): Boolean {
        val r = doCmdSync(checkRootState).lowercase(Locale.getDefault())
        return if (r == "error" || r.contains("permission denied") || r.contains("not allowed") || r == "not found") {
            if (rootMode) {
                tryExit()
            }
            false
        } else if (r.contains("success")) {
            true
        } else {
            if (rootMode) {
                tryExit()
            }
            false
        }
    }

    private fun getRuntimeShell() {
        if (p != null) return
        val getSu = Thread {
            try {
                mLock.lockInterruptibly()
                enterLockTime = System.currentTimeMillis()
                p = if (rootMode) ShellExecutor.getSuperUserRuntime() else ShellExecutor.getRuntime()
                out = p!!.outputStream
                reader = p!!.inputStream.bufferedReader()
                if (rootMode) {
                    out?.run {
                        write(checkRootState.toByteArray(Charset.defaultCharset()))
                        flush()
                    }
                }
                Thread {
                    try {
                        val errorReader = p!!.errorStream.bufferedReader()
                        while (true) {
                            val line = errorReader.readLine() ?: break
                            Log.e("KeepShellPublic", line)
                        }
                    } catch (ex: Exception) {
                        Log.e("c", "" + ex.message)
                    }
                }.start()
            } catch (ex: Exception) {
                Log.e("getRuntime", "" + ex.message)
            } finally {
                enterLockTime = 0L
                if (mLock.isHeldByCurrentThread) {
                    mLock.unlock()
                }
            }
        }
        getSu.start()
        getSu.join(10000)
        if (p == null && getSu.state != Thread.State.TERMINATED) {
            enterLockTime = 0L
            getSu.interrupt()
        }
    }

    private val shellOutputCache = StringBuilder()
    private val startTag = "|SH>>|"
    private val endTag = "|<<SH|"
    private val startTagBytes = "\necho '$startTag'\n".toByteArray(Charset.defaultCharset())
    private val endTagBytes = "\necho '$endTag'\n".toByteArray(Charset.defaultCharset())

    /** ShellRunner facade — identical to [doCmdSync]. */
    override fun execute(script: String): String {
        return doCmdSync(script)
    }

    /** 执行脚本 */
    fun doCmdSync(cmd: String): String {
        if (mLock.isLocked && enterLockTime > 0 && System.currentTimeMillis() - enterLockTime > LOCK_TIMEOUT) {
            tryExit()
            Log.e("doCmdSync-Lock", "线程等待超时${System.currentTimeMillis()} - $enterLockTime > $LOCK_TIMEOUT")
        }
        getRuntimeShell()

        try {
            mLock.lockInterruptibly()
            currentIsIdle = false

            out?.run {
                writeExecutor.execute {
                    try {
                        write(startTagBytes)
                        write(cmd.toByteArray(Charset.defaultCharset()))
                        write(endTagBytes)
                        flush()
                    } catch (ex: Exception) {
                        // 流可能已被 tryExit 关闭
                    }
                }
            }

            var unstart = true
            while (reader != null) {
                val line = reader!!.readLine() ?: break
                if (line.contains(endTag)) {
                    shellOutputCache.append(line.substring(0, line.indexOf(endTag)))
                    break
                } else if (line.contains(startTag)) {
                    shellOutputCache.clear()
                    shellOutputCache.append(line.substring(line.indexOf(startTag) + startTag.length))
                    unstart = false
                } else if (!unstart) {
                    shellOutputCache.append(line)
                    shellOutputCache.append("\n")
                }
            }
            // Log.e("shell-unlock", cmd)
            // Log.d("Shell", cmd + "\n" + "Result:" + shellOutputCache.toString().trim())
            return shellOutputCache.toString().trim()
        } catch (e: Exception) {
            tryExit()
            Log.e("KeepShellAsync", "" + e.message)
            return "error"
        } finally {
            enterLockTime = 0L
            if (mLock.isHeldByCurrentThread) {
                mLock.unlock()
            }
            currentIsIdle = true
        }
    }

    /** 执行脚本，并对结果进行ResourceID翻译 */
    fun doCmdSync(shellCommand: String, shellTranslation: ShellTranslation): String {
        val rows = doCmdSync(shellCommand).split("\n")
        return if (rows.isNotEmpty()) {
            shellTranslation.resolveRows(rows)
        } else {
            ""
        }
    }
}
