// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.dialogs

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.projectkr.shell.runtime.KrScriptRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

private data class AppEntry(val packageName: String, val label: String, val installed: Boolean)

/**
 * Installed-app chooser for `app` / `packages` params (original
 * ParamsAppChooserRender): options restrict the candidate set; packages type
 * includes uninstalled option entries; multiple keeps a selection set.
 */
@Composable
fun AppChooserDialog(
    show: Boolean,
    filterPackages: List<String>?,
    multiple: Boolean,
    onPicked: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val apps = remember { mutableStateListOf<AppEntry>() }
    val selected = remember { mutableStateListOf<String>() }

    LaunchedEffect(show, filterPackages) {
        if (!show) return@LaunchedEffect
        selected.clear()
        apps.clear()
        apps.addAll(
            withContext(Dispatchers.IO) { loadApps(context, filterPackages) },
        )
    }

    OverlayDialog(
        title = "选择应用",
        show = show,
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            items(apps.size) { index ->
                val app = apps[index]
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    BasicComponent(
                        title = app.label,
                        summary = app.packageName + if (!app.installed) " (未安装)" else "",
                        onClick = {
                            if (multiple) {
                                if (app.packageName in selected) {
                                    selected.remove(app.packageName)
                                } else {
                                    selected.add(app.packageName)
                                }
                            } else {
                                onPicked(listOf(app.packageName))
                                onDismiss()
                            }
                        },
                    )
                }
            }
        }

        if (multiple) {
            TextButton(
                text = "确定 (${selected.size})",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                onClick = {
                    onPicked(selected.toList())
                    onDismiss()
                },
            )
        }
    }
}

private suspend fun loadApps(context: Context, filterPackages: List<String>?): List<AppEntry> {
    val pm = context.packageManager
    val result = LinkedHashMap<String, AppEntry>()

    if (filterPackages.isNullOrEmpty()) {
        val launchables = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        )
        launchables.forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg !in result) {
                result[pkg] = AppEntry(pkg, info.loadLabel(pm).toString(), installed = true)
            }
        }
    } else {
        // Restrict to the configured option values; uninstalled entries show raw.
        filterPackages.forEach { pkg ->
            val installed = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
            val label = installed?.applicationInfo?.loadLabel(pm)?.toString() ?: pkg
            result[pkg] = AppEntry(pkg, label, installed = installed != null)
        }
    }
    return result.values.sortedBy { it.label.lowercase() }
}
