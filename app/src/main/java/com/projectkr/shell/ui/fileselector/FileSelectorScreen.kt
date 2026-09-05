// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.fileselector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectkr.shell.ui.pages.FilePickerResult
import com.projectkr.shell.runtime.KrScriptRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** One row of the directory listing. */
data class DirEntry(val path: String, val name: String, val isDirectory: Boolean)

/**
 * Lists [dir] contents: plain filesystem first, then a root-shell `ls -Ap`
 * fallback — scoped storage blocks java.io.File on system/shared paths, but
 * this is a root tool so the persistent shell can always read them.
 */
internal fun listDir(dir: String): List<DirEntry> {
    // 1. Plain filesystem listing (authoritative when it succeeds).
    val files = runCatching {
        File(dir).listFiles()?.map { DirEntry(it.absolutePath, it.name, it.isDirectory) }
    }.getOrNull()
    if (files != null) {
        return files.sortedWith(
            compareByDescending<DirEntry> { it.isDirectory }.thenBy { it.name.lowercase() },
        )
    }

    // 2. Root fallback: `ls -Ap` marks directories with a trailing slash.
    if (!KrScriptRuntime.isReady) return emptyList()
    val escaped = dir.replace("'", "'\\''")
    val out = KrScriptRuntime.shell.execute("ls -Ap '$escaped' 2>/dev/null")
    if (out == "error" || out.isEmpty()) return emptyList()

    return out.split("\n").mapNotNull { raw ->
        val line = raw.trimEnd()
        if (line.isEmpty()) return@mapNotNull null
        val isDir = line.endsWith("/")
        val name = if (isDir) line.dropLast(1) else line
        if (name.isEmpty()) {
            null
        } else {
            val base = if (dir.endsWith("/")) dir else "$dir/"
            DirEntry(path = base + name, name = name, isDirectory = isDir)
        }
    }.sortedWith(
        compareByDescending<DirEntry> { it.isDirectory }.thenBy { it.name.lowercase() },
    )
}

/**
 * Custom file selector (original ActivityFileSelector): browse directories via
 * a breadcrumb path bar and hand the picked path back through [FilePickerResult].
 */
@Composable
fun FileSelectorScreen(
    startDir: String,
    extension: String = "",
    folderMode: Boolean = false,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentDir by rememberSaveable { mutableStateOf(startDir.ifEmpty { "/" }) }
    var entries by remember(currentDir) { mutableStateOf<List<DirEntry>?>(null) }
    val normalizedExtension = remember(extension) {
        extension.trim()
            .takeUnless { it == "*" || it == "*.*" }
            ?.removePrefix("*.")
            ?.removePrefix("*")
            ?.removePrefix(".")
            ?.lowercase()
            .orEmpty()
    }
    val pickerTitle = if (folderMode) "选择文件夹" else "选择文件"
    val displayedEntries = entries?.filter { entry ->
        when {
            folderMode -> entry.isDirectory
            entry.isDirectory || normalizedExtension.isEmpty() -> true
            else -> entry.name.lowercase().endsWith(".$normalizedExtension")
        }
    }

    LaunchedEffect(currentDir) {
        entries = withContext(Dispatchers.IO) { listDir(currentDir) }
    }

    // Split the current path into breadcrumb segments (root keeps its slash).
    val crumbs = remember(currentDir) {
        val parts = currentDir.split("/").filter { it.isNotEmpty() }
        buildList {
            add(BreadcrumbItem(path = "/", text = "/"))
            var acc = ""
            for (part in parts) {
                acc += "/" + part
                add(BreadcrumbItem(path = acc, text = part))
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                SmallTopAppBar(
                    title = pickerTitle,
                    navigationIcon = {
                        IconButton(onClick = {
                            // Back goes up one directory; at the root it exits.
                            val parent = File(currentDir).parent
                            if (parent != null && parent != currentDir) {
                                currentDir = parent
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(MiuixIcons.Back, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (folderMode) {
                            com.projectkr.shell.ui.common.HintedAction(
                                text = "选择当前目录",
                                icon = MiuixIcons.Ok,
                                onClick = {
                                    FilePickerResult.pendingPath = currentDir
                                    onBack()
                                },
                            )
                        }
                    },
                )
                BreadcrumbBar(
                    items = crumbs,
                    onItemClick = { index ->
                        crumbs.getOrNull(index)?.let { segment ->
                            currentDir = segment.path
                        }
                    },
                    highlightIndex = crumbs.lastIndex,
                )
            }
        },
    ) { inner ->
        Column(modifier = Modifier.padding(inner)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
            ) {
                val list = displayedEntries
                when {
                    list == null -> item { LoadingHint("正在读取目录…") }
                    list.isEmpty() && normalizedExtension.isNotEmpty() -> {
                        item { LoadingHint("当前目录没有 .$normalizedExtension 文件") }
                    }
                    list.isEmpty() -> item { LoadingHint("空目录或不可读") }
                    else -> items(list.size, key = { index -> list[index].path }) { index ->
                        val entry = list[index]
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(top = 4.dp),
                        ) {
                            BasicComponent(
                                title = entry.name + if (entry.isDirectory) "/" else "",
                                summary = when {
                                    entry.isDirectory && folderMode -> "目录 · 进入后可选择"
                                    entry.isDirectory -> "目录"
                                    normalizedExtension.isNotEmpty() -> ".${normalizedExtension} 文件"
                                    else -> null
                                },
                                onClick = {
                                    if (entry.isDirectory) {
                                        currentDir = entry.path
                                    } else {
                                        FilePickerResult.pendingPath = entry.path
                                        onBack()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingHint(text: String = "(加载中…)") {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    )
}
