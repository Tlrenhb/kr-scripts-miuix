// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.fileselector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.util.Locale

/** 选择模式：FILE = 选择文件（按后缀过滤）；FOLDER = 选择目录 */
enum class FileSelectorMode { FILE, FOLDER }

private sealed interface ListState {
    data object Loading : ListState
    data class Entries(val dirs: List<File>, val files: List<File>) : ListState
    data object Empty : ListState
    data object NoPermission : ListState
}

/**
 * 文件/目录选择器（FRAMEWORK_PARITY §4.5，对齐原版 ActivityFileSelector）。
 *
 * - FOLDER 模式只列目录，长按目录确认选择；FILE 模式列出目录 + 按
 *   [extensionFilters] 后缀过滤的文件（不区分大小写），点击文件确认选择。
 * - 目录优先，名称不区分大小写排序；顶部路径面包屑可点击跳级；`..` 返回上级。
 * - 空目录 / 无权限目录有提示；读取失败时按原版约定提示可改用系统 SAF 选择器。
 * - 选中后回调 [onSelect]（绝对路径）。
 */
@Composable
fun FileSelectorScreen(
    contentPadding: PaddingValues,
    mode: FileSelectorMode = FileSelectorMode.FILE,
    extensionFilters: List<String> = emptyList(),
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current

    // 原版："extension" 自动补 "." 前缀；过滤不区分大小写
    val extensions = remember(extensionFilters) {
        extensionFilters
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith(".")) it else ".$it" }
    }

    var currentPath by rememberSaveable {
        mutableStateOf(Environment.getExternalStorageDirectory().absolutePath)
    }
    var listState by remember { mutableStateOf<ListState>(ListState.Loading) }
    var storageGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT > 28 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var confirmTarget by remember { mutableStateOf<File?>(null) }
    var safHintShown by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        storageGranted = granted
        if (!granted) {
            Toast.makeText(context, "没有读取文件的权限！", Toast.LENGTH_LONG).show()
        }
    }

    // 目录列表加载（IO 线程）；Android 10+ 读取共享存储自有可见部分无需权限
    LaunchedEffect(currentPath, mode, extensions, storageGranted) {
        if (!storageGranted) {
            listState = ListState.NoPermission
            return@LaunchedEffect
        }
        listState = ListState.Loading
        listState = withContext(Dispatchers.IO) {
            val dir = File(currentPath)
            val list = try {
                dir.listFiles()
            } catch (ignored: Exception) {
                null
            }
            when {
                list == null -> ListState.NoPermission
                list.isEmpty() -> ListState.Empty
                else -> {
                    val sorted = list.sortedWith(
                        compareBy<File> { !it.isDirectory }
                            .thenBy { it.name.lowercase(Locale.ROOT) },
                    )
                    val dirs = sorted.filter { it.isDirectory }
                    val files = if (mode == FileSelectorMode.FOLDER) {
                        emptyList()
                    } else {
                        sorted.filter { file ->
                            file.isFile &&
                                (extensions.isEmpty() ||
                                    extensions.any { ext -> file.name.lowercase(Locale.ROOT).endsWith(ext) })
                        }
                    }
                    if (dirs.isEmpty() && files.isEmpty()) {
                        ListState.Empty
                    } else {
                        ListState.Entries(dirs, files)
                    }
                }
            }
        }
        val state = listState
        if (state is ListState.NoPermission && !safHintShown) {
            safHintShown = true
            Toast.makeText(
                context,
                "无法读取该目录（无权限）。可改用系统文件选择器（SAF）选择文件",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val currentFile = File(currentPath)
    val parentPath = currentFile.parent

    // 返回键：能返回上级时先返回上级（原版 onKeyDown 行为）
    BackHandler(enabled = parentPath != null) {
        parentPath?.let { currentPath = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        // 路径面包屑（可点击跳级）
        Breadcrumb(
            path = currentPath,
            onNavigate = { currentPath = it },
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (parentPath != null) {
                item(key = "..") {
                    FileRow(
                        title = "..",
                        summary = "返回上级目录",
                        onClick = { currentPath = parentPath },
                        onLongClick = null,
                    )
                }
            }
            when (val state = listState) {
                ListState.Loading -> item(key = "loading") {
                    HintText("请稍等…")
                }
                ListState.Empty -> item(key = "empty") {
                    HintText(
                        if (mode == FileSelectorMode.FOLDER) "空目录（没有子目录）" else "空目录（没有匹配的文件）",
                    )
                }
                ListState.NoPermission -> item(key = "no_permission") {
                    Column {
                        HintText("无法读取该目录（无权限）")
                        if (Build.VERSION.SDK_INT <= 28 && !storageGranted) {
                            TextButton(
                                text = "授予存储权限",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                },
                            )
                        }
                    }
                }
                is ListState.Entries -> {
                    items(state.dirs, key = { "d_" + it.absolutePath }) { dir ->
                        FileRow(
                            title = "📁 ${dir.name}",
                            summary = null,
                            onClick = { currentPath = dir.absolutePath },
                            // 原版 FOLDER 模式：长按目录 → 确认选择
                            onLongClick = if (mode == FileSelectorMode.FOLDER) {
                                { confirmTarget = dir }
                            } else {
                                null
                            },
                        )
                    }
                    items(state.files, key = { "f_" + it.absolutePath }) { file ->
                        FileRow(
                            title = "📄 ${file.name}",
                            summary = formatSize(file.length()),
                            // 原版 FILE 模式：点击文件 → 确认选择
                            onClick = { confirmTarget = file },
                            onLongClick = null,
                        )
                    }
                }
            }
        }

        // FOLDER 模式：允许直接选择当前目录（补充原版长按选择的交互）
        if (mode == FileSelectorMode.FOLDER) {
            TextButton(
                text = "选择当前目录",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                onClick = { confirmTarget = currentFile },
            )
        }
    }

    // 选择确认对话框（原版：tap/long-press + confirm）
    val target = confirmTarget
    OverlayDialog(
        show = target != null,
        title = if (mode == FileSelectorMode.FOLDER) "选择该目录？" else "选择该文件？",
        summary = target?.absolutePath,
        onDismissRequest = { confirmTarget = null },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                text = "取消",
                modifier = Modifier.weight(1f),
                onClick = { confirmTarget = null },
            )
            TextButton(
                text = "确定",
                modifier = Modifier.weight(1f),
                onClick = {
                    target?.let { onSelect(it.absolutePath) }
                    confirmTarget = null
                },
            )
        }
    }
}

@Composable
private fun Breadcrumb(path: String, onNavigate: (String) -> Unit) {
    val segments = remember(path) {
        val parts = path.split("/").filter { it.isNotEmpty() }
        var acc = ""
        parts.map { part ->
            acc += "/$part"
            part to acc
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "/",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onNavigate("/") },
        )
        segments.forEach { (name, fullPath) ->
            Text(
                text = "$name/",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable { onNavigate(fullPath) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = title, style = MiuixTheme.textStyles.body1)
        if (summary != null) {
            Text(
                text = summary,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f GB", bytes / 1024f / 1024f / 1024f)
    bytes >= 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024f / 1024f)
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}
