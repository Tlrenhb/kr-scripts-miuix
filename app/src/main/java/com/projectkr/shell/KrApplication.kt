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
        Thread {
            val ok = KrScriptRuntime.init(this@KrApplication as Context)
            if (!ok) Log.e(TAG, "KrScript runtime init failed")
        }.start()
    }

    companion object {
        private const val TAG = "KrApplication"
    }
}
