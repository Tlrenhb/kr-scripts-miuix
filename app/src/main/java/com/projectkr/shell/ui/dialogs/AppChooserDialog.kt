// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.dialogs

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class AppEntry(
    val packageName: String,
    val label: String,
    val installed: Boolean,
)

/**
 * Installed-app chooser for `app` / `packages` params. The filter comes from
 * static `<option>` or `options-sh`; the initial set keeps existing selections
 * visible when a user reopens a multiple-choice parameter.
 */
@Composable
fun AppChooserDialog(
    show: Boolean,
    filterPackages: List<String>?,
    multiple: Boolean,
    initialSelection: Set<String> = emptySet(),
    onPicked: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val apps = remember { mutableStateListOf<AppEntry>() }
    val selected = remember { mutableStateListOf<String>() }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(show, filterPackages, initialSelection) {
        if (!show) return@LaunchedEffect
        query = ""
        selected.clear()
        selected.addAll(initialSelection)
        loading = true
        apps.clear()
        apps.addAll(withContext(Dispatchers.IO) { loadApps(context, filterPackages) })
        loading = false
    }

    val normalizedQuery = query.trim().lowercase()
    val visibleApps = apps.filter { app ->
        normalizedQuery.isEmpty() ||
            app.label.lowercase().contains(normalizedQuery) ||
            app.packageName.lowercase().contains(normalizedQuery)
    }

    OverlayDialog(
        title = if (multiple) "选择应用（${selected.size}）" else "选择应用",
        summary = if (filterPackages.isNullOrEmpty()) {
            "可按名称或包名搜索"
        } else {
            "已按配置限制可选应用"
        },
        show = show,
        onDismissRequest = onDismiss,
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            label = "搜索应用或包名",
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )

        if (multiple) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = "全选当前结果",
                    onClick = {
                        visibleApps.forEach { app ->
                            if (app.packageName !in selected) selected.add(app.packageName)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "清空",
                    onClick = { selected.clear() },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 360.dp),
        ) {
            when {
                loading -> item {
                    Text(
                        text = "正在读取应用…",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                visibleApps.isEmpty() -> item {
                    Text(
                        text = if (query.isEmpty()) "没有可选应用" else "没有匹配的应用",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> items(visibleApps, key = { it.packageName }) { app ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                    ) {
                        val summary = buildString {
                            append(app.packageName)
                            if (!app.installed) append("（未安装）")
                            if (app.packageName in selected) append(" · 已选择")
                        }
                        if (multiple) {
                            CheckboxPreference(
                                title = app.label,
                                summary = summary,
                                checked = app.packageName in selected,
                                checkboxLocation = CheckboxLocation.End,
                                onCheckedChange = { checked ->
                                    if (checked && app.packageName !in selected) {
                                        selected.add(app.packageName)
                                    } else if (!checked) {
                                        selected.remove(app.packageName)
                                    }
                                },
                            )
                        } else {
                            BasicComponent(
                                title = app.label,
                                summary = summary,
                                onClick = {
                                    onPicked(listOf(app.packageName))
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }

        if (multiple) {
            TextButton(
                text = "确定（${selected.size}）",
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

private fun loadApps(context: Context, filterPackages: List<String>?): List<AppEntry> {
    val pm = context.packageManager
    val result = LinkedHashMap<String, AppEntry>()

    if (filterPackages.isNullOrEmpty()) {
        // Launcher activities are package-visibility safe on modern Android.
        val launchables = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0,
        )
        launchables.forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg !in result) {
                result[pkg] = AppEntry(pkg, info.loadLabel(pm).toString(), installed = true)
            }
        }
    } else {
        // Configured filters may deliberately include an unavailable package.
        filterPackages.forEach { pkg ->
            val installed = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
            val label = installed?.applicationInfo?.loadLabel(pm)?.toString() ?: pkg
            result[pkg] = AppEntry(pkg, label, installed = installed != null)
        }
    }
    return result.values.sortedBy { it.label.lowercase() }
}
