// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell

import android.app.Application
import android.content.Context
import android.util.Log
import com.projectkr.shell.runtime.KrScriptRuntime

/**
 * Application entry: initializes the KrScript engine (executor.sh + persistent
 * shell) once, before any UI reads configs.
 */
class KrApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        com.projectkr.shell.runtime.BgTaskNotifications.ensureChannel(this)
        Thread {
            val ok = KrScriptRuntime.init(this@KrApplication as Context)
            if (!ok) Log.e(TAG, "KrScript runtime init failed")
        }.start()
    }

    /** Persists the latest crash to files/crash.txt for bug reports. */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                java.io.File(filesDir, "crash.txt").writeText(
                    "thread=" + thread.name + "\n" +
                        android.util.Log.getStackTraceString(throwable),
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "KrApplication"
    }
}
