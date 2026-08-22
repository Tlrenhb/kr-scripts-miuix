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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/**
 * Custom file selector (original ActivityFileSelector): browse directories and
 * hand the picked path back through [FilePickerResult].
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

    Scaffold(
        modifier = modifier,
        topBar = {
            SmallTopAppBar(
                title = currentDir,
                navigationIcon = {
                    IconButton(onClick = {
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
