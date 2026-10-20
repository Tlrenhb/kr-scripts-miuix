// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.executor

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan

/**
 * Port of `krscript/src/main/java/com/omarea/krscript/model/ShellHandlerBase.java` @ b90f2cc.
 *
 * Log-streaming protocol (§3.4 of docs/FRAMEWORK_PARITY.md):
 * - Producers send [EVENT_START] (twice: `"shell@android:\n"`, then the script text),
 *   [EVENT_REDE] per stdout line (with trailing `\n`), [EVENT_READ_ERROR] per stderr
 *   line, and [EVENT_EXIT] carrying `process.waitFor()`. [EVENT_WRITE] has a handler
 *   but no live producer in the original.
 * - The only special stdout directive is `progress:[<current>/<total>]`, consumed by
 *   [onReaderMsg] and routed to [onProgress] instead of the log.
 *
 * Deviation from the original: the original extends `Handler()` (binding to the
 * constructor thread's looper — always the main thread in practice); this port binds
 * explicitly to the main looper so handlers can safely be created off the UI thread.
 * A second constructor allows passing a custom [Looper] if ever needed.
 */
abstract class ShellHandlerBase : Handler {
    constructor() : super(Looper.getMainLooper())
    constructor(looper: Looper) : super(looper)

    companion object {
        /** 处理启动信息 */
        const val EVENT_START = 0

        /** 命令行输出内容 */
        const val EVENT_REDE = 2

        /** 命令行错误输出 */
        const val EVENT_READ_ERROR = 4

        /** 脚本写入日志 */
        const val EVENT_WRITE = 6

        /** 处理Exitvalue */
        const val EVENT_EXIT = -2
    }

    protected abstract fun onProgress(current: Int, total: Int)

    protected abstract fun onStart(msg: Any?)

    abstract fun onStart(forceStop: Runnable?)

    protected abstract fun onExit(msg: Any?)

    /**
     * 输出格式化内容
     */
    protected abstract fun updateLog(msg: SpannableString?)

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        when (msg.what) {
            EVENT_EXIT -> onExit(msg.obj)
            EVENT_START -> {
                onStart(msg.obj)
            }

            EVENT_REDE -> onReaderMsg(msg.obj)
            EVENT_READ_ERROR -> onError(msg.obj)
            EVENT_WRITE -> {
                onWrite(msg.obj)
            }
        }
    }

    protected open fun onReaderMsg(msg: Any?) {
        if (msg != null) {
            val log = msg.toString().trim()
            if (progressPattern.matches(log)) {
                // 原版在此处直接 parseInt（progress:[\/5] 这类畸形输入会崩溃）；
                // 解析失败时降级为普通日志行
                val parsed = runCatching {
                    val values = log.substring("progress:[".length, log.indexOf("]")).split("/")
                    values[0].toInt() to values[1].toInt()
                }.getOrNull()
                if (parsed != null) {
                    onProgress(parsed.first, parsed.second)
                } else {
                    onReader(msg)
                }
            } else {
                onReader(msg)
            }
        }
    }

    protected open fun onReader(msg: Any?) {
        updateLog(msg, "#00cc55")
    }

    protected open fun onWrite(msg: Any?) {
        updateLog(msg, "#808080")
    }

    protected open fun onError(msg: Any?) {
        updateLog(msg, "#ff0000")
    }

    /**
     * 输出指定颜色的内容
     */
    protected fun updateLog(msg: Any?, color: String) {
        if (msg != null) {
            val msgStr = msg.toString()
            val spannableString = SpannableString(msgStr)
            spannableString.setSpan(
                ForegroundColorSpan(Color.parseColor(color)),
                0,
                msgStr.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            updateLog(spannableString)
        }
    }

    protected fun updateLog(msg: Any?, color: Int) {
        if (msg != null) {
            val msgStr = msg.toString()
            val spannableString = SpannableString(msgStr)
            spannableString.setSpan(ForegroundColorSpan(color), 0, msgStr.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            updateLog(spannableString)
        }
    }

    private companion object {
        // 原版正则：^progress:\[[\-0-9\\]{1,}/[0-9\\]{1,}\]$
        val progressPattern = Regex("^progress:\\[[\\-0-9\\\\]{1,}/[0-9\\\\]{1,}\\]\$")
    }
}
