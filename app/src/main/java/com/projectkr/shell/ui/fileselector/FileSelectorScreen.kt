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
import androidx.compose.ui.unit.sp
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentDir by rememberSaveable { mutableStateOf(startDir.ifEmpty { "/" }) }
    var entries by remember(currentDir) { mutableStateOf<List<DirEntry>?>(null) }

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
                    title = "选择文件",
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                val list = entries
                if (list == null) {
                    item { LoadingHint() }
                } else if (list.isEmpty()) {
                    item { LoadingHint("(空目录或不可读)") }
                } else {
                    items(list.size) { index ->
                        val entry = list[index]
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            BasicComponent(
                                title = entry.name + if (entry.isDirectory) "/" else "",
                                summary = if (entry.isDirectory) "目录" else null,
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
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    )
}
