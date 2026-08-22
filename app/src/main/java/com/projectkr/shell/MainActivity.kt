// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.projectkr.shell.ui.KrScriptApp

class MainActivity : ComponentActivity() {

    /** Shortcut deep link pending navigation; observed by [KrScriptApp]. */
    private val shortcutLaunch = mutableStateOf<ShortcutLaunch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readShortcutIntent(intent)
        setContent {
            val shortcut by shortcutLaunch
            KrScriptApp(shortcut = shortcut)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: warm-start shortcut taps arrive here.
        readShortcutIntent(intent)
    }

    private fun readShortcutIntent(intent: Intent?) {
        val config = intent?.getStringExtra(ShortcutHelper.EXTRA_CONFIG).orEmpty()
        if (config.isNotEmpty()) {
            shortcutLaunch.value = ShortcutLaunch(
                configPath = config,
                title = intent.getStringExtra(ShortcutHelper.EXTRA_TITLE).orEmpty(),
            )
        }
    }
}

/** Plain data carrier for a shortcut-triggered page open. */
data class ShortcutLaunch(val configPath: String, val title: String)
