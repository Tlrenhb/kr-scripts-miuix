// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.webkit.WebView
import java.io.File
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import com.projectkr.shell.core.config.PageConfigReader
import com.projectkr.shell.core.config.ShellRunner
import com.projectkr.shell.core.model.ActionNode
import com.projectkr.shell.core.model.ActionParamInfo
import com.projectkr.shell.core.model.GroupNode
import com.projectkr.shell.core.model.NodeInfoBase
import com.projectkr.shell.core.model.PageNode
import com.projectkr.shell.core.model.PickerNode
import com.projectkr.shell.core.model.SwitchNode
import com.projectkr.shell.core.model.TextNode
import com.projectkr.shell.shortcut.ShortcutHelper
import com.projectkr.shell.shell.RootShellRunner
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

private data class FavoriteItem(
    val key: String,
    val title: String,
    val summary: String,
)

@Composable
fun KrScriptApp() {
    val context = LocalContext.current
    val shellRunner = remember { RootShellRunner() }
    val reader = remember { PageConfigReader(context, shellRunner) }
    val rootNodes = remember {
        reader.readConfigXml("file:///android_asset/sample.xml") ?: emptyList()
    }
    var actionForParams by remember { mutableStateOf<ActionNode?>(null) }
    var currentNodes by remember { mutableStateOf(rootNodes) }
    var currentTitle by remember { mutableStateOf("KrScript Miuix") }
    var selectedTab by remember { mutableStateOf(0) }
    var onlineUrl by remember { mutableStateOf<String?>(null) }
    var onlineTitle by remember { mutableStateOf("在线页面") }
    var showPowerMenu by remember { mutableStateOf(false) }
    var showFileSelector by remember { mutableStateOf(false) }
    val pageStack = remember { mutableStateListOf<Pair<String, List<NodeInfoBase>>>() }
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1000)
        showSplash = false
    }
    if (showSplash) {
        SplashScreen()
        return
    }

    val favoritesPrefs = context.getSharedPreferences("kr_script_favorites", Context.MODE_PRIVATE)
    val favorites = remember {
        mutableStateListOf<FavoriteItem>().apply {
            val saved = favoritesPrefs.getStringSet("favorites", emptySet()) ?: emptySet()
            saved.forEach { line ->
                val parts = line.split("|", limit = 3)
                if (parts.size == 3) {
                    add(FavoriteItem(parts[0], parts[1], parts[2]))
                }
            }
        }
    }
    fun saveFavorites() {
        favoritesPrefs.edit()
            .putStringSet(
                "favorites",
                favorites.map { "${it.key}|${it.title}|${it.summary}" }.toSet()
            )
            .apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = when {
                    showFileSelector -> "文件选择器"
                    selectedTab == 0 -> "首页"
                    selectedTab == 1 -> if (onlineUrl != null) onlineTitle else currentTitle
                    selectedTab == 2 -> "收藏"
                    else -> "关于"
                },
                navigationIcon = {
                    if (showFileSelector || ((pageStack.isNotEmpty() || onlineUrl != null) && selectedTab == 1)) {
                        IconButton(onClick = {
                            when {
                                showFileSelector -> showFileSelector = false
                                onlineUrl != null -> onlineUrl = null
                                pageStack.isNotEmpty() -> {
                                    val previous = pageStack.removeAt(pageStack.lastIndex)
                                    currentNodes = previous.second
                                    currentTitle = previous.first
                                }
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { showPowerMenu = true }) {
                    Icon(MiuixIcons.Settings, "电源菜单")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = MiuixIcons.Home,
                    label = "首页"
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = MiuixIcons.VerticalSplit,
                    label = "页面"
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = MiuixIcons.Favorites,
                    label = "收藏"
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = MiuixIcons.Info,
                    label = "关于"
                )
            }
        }
    ) { padding ->
        if (showFileSelector) {
            FileSelectorScreen(
                onBack = { showFileSelector = false },
                onSelect = { path ->
                    Toast.makeText(context, "选中: $path", Toast.LENGTH_LONG).show()
                    showFileSelector = false
                }
            )
        } else {
        when (selectedTab) {
            0 -> HomeScreen(
                contentPadding = padding,
                context = context,
                onOpenPowerMenu = { showPowerMenu = true },
                onOpenFileSelector = { showFileSelector = true }
            )
            1 -> {
                val url = onlineUrl
                if (url != null) {
                    OnlinePageScreen(url = url)
                } else {
                    NodeListScreen(
                        nodes = currentNodes,
                        contentPadding = padding,
                        context = context,
                        shellRunner = shellRunner,
                        onActionClick = { actionForParams = it },
                        onAddFavorite = { node ->
                            val key = node.key.ifEmpty { node.title }
                            if (favorites.none { it.key == key && key.isNotEmpty() }) {
                                favorites.add(
                                    FavoriteItem(
                                        key = key,
                                        title = node.title,
                                        summary = node.summary.ifEmpty { node.desc }
                                    )
                                )
                                saveFavorites()
                                Toast.makeText(context, "已收藏", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "已在收藏中", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPageClick = { page ->
                            val path = page.pageConfigPath
                            if (path.isNotBlank()) {
                                val childNodes = reader.readConfigXml(path, page.pageConfigDir) ?: emptyList()
                                if (childNodes.isNotEmpty()) {
                                    pageStack.add(currentTitle to currentNodes)
                                    currentNodes = childNodes
                                    currentTitle = page.title
                                }
                            } else {
                                Toast.makeText(context, "此页面没有配置子页面", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    ActionParamsDialog(
                        action = actionForParams,
                        context = context,
                        shellRunner = shellRunner,
                        onDismiss = { actionForParams = null }
                    )
                }
            }
            2 -> FavoritesScreen(
                favorites = favorites,
                contentPadding = padding,
                onRemove = {
                    favorites.remove(it)
                    saveFavorites()
                },
                onCreateShortcut = { item ->
                    ShortcutHelper.addShortcut(context, item.key, item.title)
                    Toast.makeText(context, "已请求创建快捷方式", Toast.LENGTH_SHORT).show()
                }
            )
            else -> AboutScreen(contentPadding = padding)
        }
            PowerMenuDialog(
                show = showPowerMenu,
                context = context,
                shellRunner = shellRunner,
                onDismiss = { showPowerMenu = false }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    contentPadding: PaddingValues,
    context: Context,
    onOpenPowerMenu: () -> Unit,
    onOpenFileSelector: () -> Unit,
) {
    val battery = remember {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }
    val cores = remember { Runtime.getRuntime().availableProcessors() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallTitle(text = "设备信息")
        ArrowPreference(
            title = "型号",
            summary = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
        ArrowPreference(
            title = "Android 版本",
            summary = Build.VERSION.RELEASE
        )
        ArrowPreference(
            title = "CPU 核心数",
            summary = "$cores"
        )
        ArrowPreference(
            title = "电池电量",
            summary = if (battery >= 0) "$battery%" else "未知"
        )
        SmallTitle(text = "快捷操作")
        TextButton(
            text = "打开电源菜单",
            onClick = onOpenPowerMenu,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            text = "打开文件选择器",
            onClick = onOpenFileSelector,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FileSelectorScreen(
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var currentDir by remember { mutableStateOf(File("/storage/emulated/0")) }
    val entries = remember(currentDir) {
        currentDir.listFiles()?.sortedBy { !it.isDirectory } ?: emptyList()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = currentDir.absolutePath)
        LazyColumn {
            if (currentDir.parentFile != null) {
                item(key = "parent") {
                    ArrowPreference(
                        title = "..",
                        onClick = { currentDir = currentDir.parentFile!! }
                    )
                }
            }
            items(entries, key = { it.absolutePath }) { file ->
                ArrowPreference(
                    title = if (file.isDirectory) "📁 ${file.name}" else "📄 ${file.name}",
                    summary = if (file.isDirectory) "目录" else "${file.length()} bytes",
                    onClick = {
                        if (file.isDirectory) {
                            currentDir = file
                        } else {
                            onSelect(file.absolutePath)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PowerMenuDialog(
    show: Boolean,
    context: Context,
    shellRunner: ShellRunner,
    onDismiss: () -> Unit,
) {
    if (!show) return
    OverlayDialog(
        show = true,
        title = "电源菜单",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                text = "重启",
                onClick = {
                    shellRunner.execute("reboot")
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                text = "重启到 Recovery",
                onClick = {
                    shellRunner.execute("reboot recovery")
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                text = "重启到 Bootloader",
                onClick = {
                    shellRunner.execute("reboot bootloader")
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                text = "关机",
                onClick = {
                    shellRunner.execute("reboot -p")
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OnlinePageScreen(url: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                loadUrl(url)
            }
        }
    )
}

@Composable
private fun SplashScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "KrScript Miuix",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "正在加载...",
        )
    }
}

@Composable
private fun AboutScreen(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "KrScript Miuix",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "使用 Compose + Miuix 重写 kr-scripts 的示例工程。",
        )
    }
}

@Composable
private fun NodeListScreen(
    nodes: List<NodeInfoBase>,
    contentPadding: PaddingValues,
    context: Context,
    shellRunner: ShellRunner,
    onActionClick: (ActionNode) -> Unit,
    onPageClick: (PageNode) -> Unit,
    onAddFavorite: (NodeInfoBase) -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding
    ) {
        nodes.forEach { node ->
            when (node) {
                is GroupNode -> {
                    item(key = node.index) {
                        SmallTitle(text = node.title)
                    }
                    items(node.children, key = { it.index }) { child ->
                        NodeItem(
                            node = child,
                            context = context,
                            shellRunner = shellRunner,
                            onActionClick = onActionClick,
                            onPageClick = onPageClick,
                            onAddFavorite = onAddFavorite,
                        )
                    }
                }
                else -> {
                    item(key = node.index) {
                        NodeItem(
                            node = node,
                            context = context,
                            shellRunner = shellRunner,
                            onActionClick = onActionClick,
                            onPageClick = onPageClick,
                            onAddFavorite = onAddFavorite,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeItem(
    node: NodeInfoBase,
    context: Context,
    shellRunner: ShellRunner,
    onActionClick: (ActionNode) -> Unit,
    onPageClick: (PageNode) -> Unit,
    onAddFavorite: (NodeInfoBase) -> Unit,
) {
    when (node) {
        is SwitchNode -> {
            var checked by remember { mutableStateOf(node.checked) }
            val setState = node.setState ?: ""
            SwitchPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc },
                checked = checked,
                endActions = {
                    IconButton(onClick = { onAddFavorite(node) }) {
                        Icon(MiuixIcons.Favorites, "收藏")
                    }
                },
                onCheckedChange = { newValue ->
                    checked = newValue
                    if (setState.isNotBlank()) {
                        val result = shellRunner.execute(
                            setState.replace("\$state", if (newValue) "1" else "0")
                        )
                        if (result.isNotBlank()) {
                            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
        is PickerNode -> {
            val options = node.options?.map { it.toString() } ?: emptyList()
            var selectedIndex by remember { mutableStateOf(0) }
            val setState = node.setState ?: ""
            OverlayDropdownPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc },
                items = options,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { index ->
                    selectedIndex = index
                    val selectedValue = node.options?.getOrNull(index)?.value ?: ""
                    if (setState.isNotBlank()) {
                        val result = shellRunner.execute(
                            setState.replace("\$state", selectedValue)
                        )
                        if (result.isNotBlank()) {
                            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
        is PageNode -> {
            ArrowPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc },
                endActions = {
                    IconButton(onClick = { onAddFavorite(node) }) {
                        Icon(MiuixIcons.Favorites, "收藏")
                    }
                },
                onClick = {
                    val link = node.link
                    val online = node.onlineHtmlPage
                    when {
                        link.isNotBlank() && (link.startsWith("http://") || link.startsWith("https://")) -> {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                        }
                        online.isNotBlank() -> {
                            onlineUrl = online
                            onlineTitle = node.title
                        }
                        node.pageConfigPath.isNotBlank() -> onPageClick(node)
                        else -> Toast.makeText(context, "此页面没有可打开的内容", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        is ActionNode -> {
            ArrowPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc },
                endActions = {
                    IconButton(onClick = { onAddFavorite(node) }) {
                        Icon(MiuixIcons.Favorites, "收藏")
                    }
                },
                onClick = {
                    if (node.params.isNullOrEmpty()) {
                        runAction(node, context, shellRunner)
                    } else {
                        onActionClick(node)
                    }
                }
            )
        }
        is TextNode -> {
            Column {
                if (node.title.isNotBlank()) {
                    SmallTitle(text = node.title)
                }
                node.rows.forEach { row ->
                    Text(
                        text = row.text,
                        fontSize = if (row.size > 0) row.size.sp else TextUnit.Unspecified,
                        fontWeight = if (row.bold) FontWeight.Bold else null,
                        fontStyle = if (row.italic) FontStyle.Italic else null,
                        textDecoration = if (row.underline) TextDecoration.Underline else null,
                        color = if (row.color != -1) Color(row.color) else Color.Unspecified
                    )
                }
            }
        }
        else -> {
            ArrowPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc }
            )
        }
    }
}

private class ParamUiState(
    val param: ActionParamInfo,
    val text: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue(param.value ?: "")),
    val checked: MutableState<Boolean> = mutableStateOf(param.value == "1" || param.value == "true"),
    val floatValue: MutableState<Float> = mutableStateOf(param.value?.toFloatOrNull() ?: 0f),
    val selectedIndex: MutableState<Int> = mutableStateOf(0),
    val color: MutableState<Color> = mutableStateOf(parseColor(param.value)),
    val selectedOptions: MutableState<Set<Int>> = mutableStateOf(emptySet()),
)

@Composable
private fun ActionParamsDialog(
    action: ActionNode?,
    context: Context,
    shellRunner: ShellRunner,
    onDismiss: () -> Unit,
) {
    if (action == null) return
    val params = action.params ?: emptyList()
    val states = remember(action) {
        params.map { ParamUiState(it) }
    }
    var pendingFileParamIndex by remember { mutableStateOf(-1) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && pendingFileParamIndex in states.indices) {
            states[pendingFileParamIndex].text.value = TextFieldValue(uri.toString())
        }
        pendingFileParamIndex = -1
    }

    OverlayDialog(
        show = true,
        title = action.title,
        summary = action.summary.ifEmpty { action.desc },
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            params.forEachIndexed { index, param ->
                when {
                    param.type == "switch" || param.type == "boolean" || param.type == "checkbox" -> {
                        CheckboxPreference(
                            title = param.title ?: param.name ?: "",
                            summary = param.desc,
                            checked = states[index].checked.value,
                            onCheckedChange = { states[index].checked.value = it }
                        )
                    }
                    param.type == "select" || param.type == "dropdown" || param.options != null -> {
                        val options = param.options?.map { it.toString() } ?: emptyList()
                        OverlayDropdownPreference(
                            title = param.title ?: param.name ?: "",
                            summary = param.desc,
                            items = options,
                            selectedIndex = states[index].selectedIndex.value,
                            onSelectedIndexChange = { states[index].selectedIndex.value = it }
                        )
                    }
                    param.type == "seekbar" || param.type == "slider" || param.type == "range" -> {
                        val min = if (param.min == Int.MIN_VALUE) 0f else param.min.toFloat()
                        val max = if (param.max == Int.MAX_VALUE) 100f else param.max.toFloat()
                        SliderPreference(
                            title = param.title ?: param.name ?: "",
                            summary = param.desc,
                            value = states[index].floatValue.value,
                            onValueChange = { states[index].floatValue.value = it },
                            valueRange = min..max
                        )
                    }
                    param.type == "color" || param.type == "colour" -> {
                        ColorPicker(
                            color = states[index].color.value,
                            onColorChanged = { states[index].color.value = it }
                        )
                    }
                    param.type == "file" || param.type == "path" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextField(
                                value = states[index].text.value,
                                onValueChange = { states[index].text.value = it },
                                label = param.title ?: param.name ?: "",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextButton(
                                text = "选择文件",
                                onClick = {
                                    pendingFileParamIndex = index
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                }
                            )
                        }
                    }
                    param.type == "multiple" || param.type == "multi" || param.multiple -> {
                        val options = param.options ?: emptyList()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            options.forEachIndexed { optionIndex, option ->
                                CheckboxPreference(
                                    title = option.title ?: option.value ?: "",
                                    checked = optionIndex in states[index].selectedOptions.value,
                                    onCheckedChange = { checked ->
                                        val newSet = states[index].selectedOptions.value.toMutableSet()
                                        if (checked) newSet.add(optionIndex) else newSet.remove(optionIndex)
                                        states[index].selectedOptions.value = newSet
                                    }
                                )
                            }
                        }
                    }
                    else -> {
                        TextField(
                            value = states[index].text.value,
                            onValueChange = { states[index].text.value = it },
                            label = param.title ?: param.name ?: "",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            TextButton(
                text = "确定",
                onClick = {
                    val values = mutableMapOf<String, String>()
                    params.forEachIndexed { index, param ->
                        val name = param.name ?: ""
                        if (name.isEmpty()) return@forEachIndexed
                        val value = when {
                            param.type == "switch" || param.type == "boolean" || param.type == "checkbox" ->
                                if (states[index].checked.value) "1" else "0"
                            param.type == "select" || param.type == "dropdown" || param.options != null ->
                                param.options?.getOrNull(states[index].selectedIndex.value)?.value ?: ""
                            param.type == "seekbar" || param.type == "slider" || param.type == "range" ->
                                states[index].floatValue.value.toInt().toString()
                            param.type == "color" || param.type == "colour" ->
                                "#%08X".format(states[index].color.value.toArgb())
                            param.type == "multiple" || param.type == "multi" || param.multiple ->
                                states[index].selectedOptions.value
                                    .mapNotNull { param.options?.getOrNull(it)?.value }
                                    .joinToString(param.separator)
                            else -> states[index].text.value.text
                        }
                        values[name] = value
                    }
                    var script = action.setState ?: ""
                    values.forEach { (key, value) ->
                        script = script.replace("\$key", value)
                    }
                    val result = shellRunner.execute(script)
                    if (result.isNotBlank()) {
                        Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FavoritesScreen(
    favorites: List<FavoriteItem>,
    contentPadding: PaddingValues,
    onRemove: (FavoriteItem) -> Unit,
    onCreateShortcut: (FavoriteItem) -> Unit,
) {
    if (favorites.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
        ) {
            Text(text = "暂无收藏")
        }
        return
    }
    LazyColumn(contentPadding = contentPadding) {
        items(favorites, key = { it.key }) { item ->
            ArrowPreference(
                title = item.title,
                summary = item.summary,
                endActions = {
                    Row {
                        IconButton(onClick = { onCreateShortcut(item) }) {
                            Icon(MiuixIcons.Add, "创建快捷方式")
                        }
                        IconButton(onClick = { onRemove(item) }) {
                            Icon(MiuixIcons.FavoritesFill, "取消收藏")
                        }
                    }
                }
            )
        }
    }
}

private fun parseColor(value: String?): Color {
    if (value.isNullOrBlank()) return Color.Black
    return try {
        Color(android.graphics.Color.parseColor(value))
    } catch (e: Exception) {
        Color.Black
    }
}

private fun runAction(
    node: ActionNode,
    context: Context,
    shellRunner: ShellRunner,
) {
    val script = node.setState ?: ""
    if (script.isNotBlank()) {
        val result = shellRunner.execute(script)
        Toast.makeText(
            context,
            result.ifEmpty { "执行完成" },
            Toast.LENGTH_SHORT
        ).show()
    }
}
