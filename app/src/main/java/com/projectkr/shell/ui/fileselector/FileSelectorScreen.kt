// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.fileselector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectkr.shell.ui.pages.FilePickerResult
import java.io.File
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

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
    val entries = remember(currentDir) {
        runCatching {
            File(currentDir).listFiles()?.sortedWith(
                compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
            ).orEmpty()
        }.getOrDefault(emptyList())
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
                items(entries.size) { index ->
                    val entry = entries[index]
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        BasicComponent(
                            title = entry.name + if (entry.isDirectory) "/" else "",
                            summary = if (entry.isDirectory) "目录" else null,
                            onClick = {
                                if (entry.isDirectory) {
                                    currentDir = entry.absolutePath
                                } else {
                                    FilePickerResult.pendingPath = entry.absolutePath
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
