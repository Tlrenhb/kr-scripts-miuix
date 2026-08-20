// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.app.ActivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.projectkr.shell.core.config.PageConfigReader
import com.projectkr.shell.core.config.ShellRunner
import com.projectkr.shell.core.executor.ScriptEnvironment
import com.projectkr.shell.core.model.ActionNode
import com.projectkr.shell.core.model.ActionParamInfo
import com.projectkr.shell.core.model.GroupNode
import com.projectkr.shell.core.model.NodeInfoBase
import com.projectkr.shell.core.model.PageMenuOption
import com.projectkr.shell.core.model.PageNode
import com.projectkr.shell.core.model.PickerNode
import com.projectkr.shell.core.model.SwitchNode
import com.projectkr.shell.core.model.TextNode
import com.projectkr.shell.shortcut.ShortcutHelper
import com.projectkr.shell.service.FloatMonitorService
import com.projectkr.shell.shell.KeepShellRunner
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private data class FavoriteItem(
    val key: String,
    val title: String,
    val summary: String,
)

@Composable
fun KrScriptApp() {
    val context = LocalContext.current
    val appScope = rememberCoroutineScope()
    val themePrefs = context.getSharedPreferences("kr_script_theme", Context.MODE_PRIVATE)
    var themeMode by remember { mutableStateOf(loadThemeMode(themePrefs)) }
    val themeController = remember(themeMode) { ThemeController(themeMode) }
    fun updateThemeMode(mode: ColorSchemeMode) {
        themeMode = mode
        themePrefs.edit().putString("mode", mode.name).apply()
    }

    val shellRunner = remember { ScriptEnvironment(context, KeepShellRunner()) }
    val reader = remember { PageConfigReader(context, shellRunner) }
    val rootNodes = remember {
        reader.readConfigXml("file:///android_asset/sample.xml") ?: emptyList()
    }
    var actionForParams by remember { mutableStateOf<ActionNode?>(null) }
    var currentNodes by remember { mutableStateOf(rootNodes) }
    var currentTitle by remember { mutableStateOf("KrScript Miuix") }
    var currentMenuOptions by remember { mutableStateOf<List<PageMenuOption>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) }
    var onlineUrl by remember { mutableStateOf<String?>(null) }
    var onlineTitle by remember { mutableStateOf("在线页面") }
    var showPowerMenu by remember { mutableStateOf(false) }
    var showFileSelector by remember { mutableStateOf(false) }
    var showMonitor by remember { mutableStateOf(false) }
    var fileSelectorParamName by remember { mutableStateOf<String?>(null) }
    val fileParamValues = remember { mutableStateMapOf<String, String>() }
    val pageStack = remember { mutableStateListOf<Triple<String, List<NodeInfoBase>, List<PageMenuOption>>>() }
    var isRefreshing by remember { mutableStateOf(false) }
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1000)
        showSplash = false
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

    MiuixTheme(controller = themeController) {
        if (showSplash) {
            SplashScreen()
        } else {
        Scaffold(
        topBar = {
            TopAppBar(
                title = when {
                    showMonitor -> "性能监控"
                    showFileSelector || fileSelectorParamName != null -> "文件选择器"
                    selectedTab == 0 -> "首页"
                    selectedTab == 1 -> if (onlineUrl != null) onlineTitle else currentTitle
                    selectedTab == 2 -> "收藏"
                    else -> "关于"
                },
                actions = {
                    if (selectedTab == 1 && onlineUrl == null) {
                        currentMenuOptions.forEach { option ->
                            TextButton(
                                text = option.title,
                                onClick = {
                                    val script = option.setState ?: ""
                                    runShellAsync(appScope, shellRunner, script) { result ->
                                        if (result.isNotBlank()) {
                                            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showMonitor || showFileSelector || fileSelectorParamName != null || ((pageStack.isNotEmpty() || onlineUrl != null) && selectedTab == 1)) {
                        IconButton(onClick = {
                            when {
                                showMonitor -> showMonitor = false
                                showFileSelector -> showFileSelector = false
                                fileSelectorParamName != null -> fileSelectorParamName = null
                                onlineUrl != null -> onlineUrl = null
                                pageStack.isNotEmpty() -> {
                                    val previous = pageStack.removeAt(pageStack.lastIndex)
                                    currentNodes = previous.second
                                    currentTitle = previous.first
                                    currentMenuOptions = previous.third
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
        if (showMonitor) {
            MonitorScreen(
                onBack = { showMonitor = false },
                contentPadding = padding
            )
        } else if (showFileSelector || fileSelectorParamName != null) {
            FileSelectorScreen(
                contentPadding = padding,
                onBack = {
                    showFileSelector = false
                    fileSelectorParamName = null
                },
                onSelect = { path ->
                    val paramName = fileSelectorParamName
                    if (paramName != null) {
                        fileParamValues[paramName] = path
                        fileSelectorParamName = null
                    } else {
                        Toast.makeText(context, "选中: $path", Toast.LENGTH_LONG).show()
                        showFileSelector = false
                    }
                }
            )
        } else {
        when (selectedTab) {
            0 -> HomeScreen(
                contentPadding = padding,
                context = context,
                onOpenPowerMenu = { showPowerMenu = true },
                onOpenFileSelector = { showFileSelector = true },
                onOpenMonitor = { showMonitor = true }
            )
            1 -> {
                val url = onlineUrl
                if (url != null) {
                    OnlinePageScreen(
                        url = url,
                        contentPadding = padding
                    )
                } else {
                    NodeListScreen(
                        nodes = currentNodes,
                        contentPadding = padding,
                        context = context,
                        shellRunner = shellRunner,
                        isRefreshing = isRefreshing,
                        onOpenOnline = { url, title ->
                            onlineUrl = url
                            onlineTitle = title
                        },
                        onRefresh = {
                            isRefreshing = true
                            val refreshed = reader.readConfigXml("file:///android_asset/sample.xml") ?: emptyList()
                            currentNodes = refreshed
                            currentTitle = "KrScript Miuix"
                            currentMenuOptions = emptyList()
                            pageStack.clear()
                            isRefreshing = false
                        },
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
                                    pageStack.add(
                                        Triple(currentTitle, currentNodes, currentMenuOptions)
                                    )
                                    currentNodes = childNodes
                                    currentTitle = page.title
                                    currentMenuOptions = page.pageMenuOptions ?: emptyList()
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
                        fileValues = fileParamValues,
                        onOpenFileSelector = { name ->
                            fileSelectorParamName = name
                        },
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
            else -> AboutScreen(
                contentPadding = padding,
                themeMode = themeMode,
                onThemeModeChange = ::updateThemeMode
            )
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
    }
}

@Composable
private fun HomeScreen(
    contentPadding: PaddingValues,
    context: Context,
    onOpenPowerMenu: () -> Unit,
    onOpenFileSelector: () -> Unit,
    onOpenMonitor: () -> Unit,
) {
    val battery = remember {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }
    val cores = remember { Runtime.getRuntime().availableProcessors() }
    val cpuFreq = remember {
        try {
            val freq = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
                .readText().trim().toLongOrNull()
            if (freq != null) "${freq / 1000} MHz" else "未知"
        } catch (e: Exception) {
            "未知"
        }
    }
    val coreFreqs = remember {
        (0 until cores).mapNotNull { index ->
            try {
                val freq = File("/sys/devices/system/cpu/cpu$index/cpufreq/scaling_cur_freq")
                    .readText().trim().toLongOrNull()
                if (freq != null) "Core $index: ${freq / 1000} MHz" else null
            } catch (e: Exception) {
                null
            }
        }
    }
    val memInfo = remember {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager?.getMemoryInfo(info)
        info
    }
    val totalRam = memInfo.totalMem
    val availRam = memInfo.availMem
    val ramRatio = if (totalRam > 0) (totalRam - availRam).toFloat() / totalRam else 0f
    val storageInfo = remember {
        try {
            StatFs(Environment.getDataDirectory().path)
        } catch (e: Exception) {
            null
        }
    }
    val totalStorage = storageInfo?.totalBytes ?: 0L
    val availStorage = storageInfo?.availableBytes ?: 0L
    val storageRatio = if (totalStorage > 0) (totalStorage - availStorage).toFloat() / totalStorage else 0f
    val batteryTemp = remember {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        if (temp >= 0) "${temp / 10.0}°C" else "未知"
    }
    val uptime = remember {
        val millis = SystemClock.elapsedRealtime()
        val seconds = millis / 1000
        "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
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
            title = "CPU 频率",
            summary = cpuFreq
        )
        coreFreqs.forEach { core ->
            ArrowPreference(
                title = "核心频率",
                summary = core
            )
        }
        ArrowPreference(
            title = "运行内存",
            summary = if (totalRam > 0) "${totalRam / 1024 / 1024 / 1024} GB" else "未知"
        )
        ArrowPreference(
            title = "存储空间",
            summary = if (totalStorage > 0) "${totalStorage / 1024 / 1024 / 1024} GB" else "未知"
        )
        ArrowPreference(
            title = "电池电量",
            summary = if (battery >= 0) "$battery%" else "未知"
        )
        ArrowPreference(
            title = "电池温度",
            summary = batteryTemp
        )
        ArrowPreference(
            title = "已运行时间",
            summary = uptime
        )
        SmallTitle(text = "使用率")
        LinearProgressIndicator(
            progress = if (battery >= 0) battery / 100f else 0f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Text(text = if (battery >= 0) "电池 $battery%" else "电池 未知")
        LinearProgressIndicator(
            progress = ramRatio,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Text(text = "内存 ${(ramRatio * 100).toInt()}%")
        LinearProgressIndicator(
            progress = storageRatio,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Text(text = "存储 ${(storageRatio * 100).toInt()}%")
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
        TextButton(
            text = "打开性能监控",
            onClick = onOpenMonitor,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            text = "开启悬浮监控",
            onClick = {
                if (Settings.canDrawOverlays(context)) {
                    context.startForegroundService(
                        Intent(context, FloatMonitorService::class.java)
                    )
                } else {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            text = "关闭悬浮监控",
            onClick = {
                context.stopService(Intent(context, FloatMonitorService::class.java))
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MonitorScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    var cpuFreq by remember { mutableStateOf("读取中...") }
    var battery by remember { mutableStateOf(-1) }
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            cpuFreq = try {
                val freq = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
                    .readText().trim().toLongOrNull()
                if (freq != null) {
                    history.add(freq / 1000f)
                    if (history.size > 60) history.removeAt(0)
                    "${freq / 1000} MHz"
                } else {
                    "未知"
                }
            } catch (e: Exception) {
                "未知"
            }
            battery = (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            delay(1000)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallTitle(text = "性能监控")
        ArrowPreference(
            title = "CPU 频率",
            summary = cpuFreq
        )
        ArrowPreference(
            title = "电池电量",
            summary = if (battery >= 0) "$battery%" else "未知"
        )
        SmallTitle(text = "CPU 频率趋势")
        CpuChart(values = history)
        TextButton(
            text = "返回",
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CpuChart(values: List<Float>, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        if (values.isEmpty()) return@Canvas
        val max = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val step = size.width / values.size
        values.forEachIndexed { index, value ->
            val barHeight = (value / max) * size.height
            drawRect(
                color = MiuixTheme.colorScheme.primary,
                topLeft = Offset(index * step, size.height - barHeight),
                size = Size(step * 0.7f, barHeight)
            )
        }
    }
}

@Composable
private fun FileSelectorScreen(
    contentPadding: PaddingValues,
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
            .padding(contentPadding)
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
    val scope = rememberCoroutineScope()
    OverlayDialog(
        show = show,
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
                    runShellAsync(scope, shellRunner, "reboot") { onDismiss() }
                },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                text = "重启到 Recovery",
                onClick = {
                    runShellAsync(scope, shellRunner, "reboot recovery") { onDismiss() }
                },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                text = "重启到 Bootloader",
                onClick = {
                    runShellAsync(scope, shellRunner, "reboot bootloader") { onDismiss() }
                },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                text = "关机",
                onClick = {
                    runShellAsync(scope, shellRunner, "reboot -p") { onDismiss() }
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
private fun OnlinePageScreen(
    url: String,
    contentPadding: PaddingValues,
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
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
private fun AboutScreen(
    contentPadding: PaddingValues,
    themeMode: ColorSchemeMode,
    onThemeModeChange: (ColorSchemeMode) -> Unit,
) {
    val modes = ColorSchemeMode.entries
    val labels = listOf("跟随系统", "浅色", "深色", "动态取色-跟随系统", "动态取色-浅色", "动态取色-深色")
    val selectedIndex = modes.indexOf(themeMode).coerceAtLeast(0)
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
        SmallTitle(text = "主题")
        OverlayDropdownPreference(
            title = "主题模式",
            summary = labels.getOrElse(selectedIndex) { "跟随系统" },
            items = labels,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = { index ->
                modes.getOrNull(index)?.let(onThemeModeChange)
            }
        )
    }
}

@Composable
private fun NodeListScreen(
    nodes: List<NodeInfoBase>,
    contentPadding: PaddingValues,
    context: Context,
    shellRunner: ShellRunner,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenOnline: (String, String) -> Unit,
    onActionClick: (ActionNode) -> Unit,
    onPageClick: (PageNode) -> Unit,
    onAddFavorite: (NodeInfoBase) -> Unit,
) {
    var searchText by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val filteredNodes = remember(nodes, searchText) {
        filterNodes(nodes, searchText)
    }

    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        pullToRefreshState = rememberPullToRefreshState()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        SearchBar(
            inputField = {
                InputField(
                    query = searchText,
                    onQueryChange = { searchText = it },
                    onSearch = { searchExpanded = false },
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it }
                )
            },
            expanded = searchExpanded,
            onExpandedChange = { searchExpanded = it }
        ) {
        }
        LazyColumn {
            filteredNodes.forEach { node ->
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
                                onOpenOnline = onOpenOnline,
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
                                onOpenOnline = onOpenOnline,
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
    }
}

private fun filterNodes(nodes: List<NodeInfoBase>, query: String): List<NodeInfoBase> {
    if (query.isBlank()) return nodes
    val q = query.trim().lowercase()
    return nodes.mapNotNull { node ->
        when (node) {
            is GroupNode -> {
                val children = filterNodes(node.children, query)
                if (children.isNotEmpty() || node.title.lowercase().contains(q) || node.summary.lowercase().contains(q)) {
                    GroupNode(node.currentPageConfigPath).apply {
                        this.title = node.title
                        this.summary = node.summary
                        this.desc = node.desc
                        this.children.addAll(children)
                    }
                } else {
                    null
                }
            }
            else -> {
                if (node.title.lowercase().contains(q) ||
                    node.summary.lowercase().contains(q) ||
                    node.desc.lowercase().contains(q)
                ) node else null
            }
        }
    }
}

@Composable
private fun NodeItem(
    node: NodeInfoBase,
    context: Context,
    shellRunner: ShellRunner,
    onOpenOnline: (String, String) -> Unit,
    onActionClick: (ActionNode) -> Unit,
    onPageClick: (PageNode) -> Unit,
    onAddFavorite: (NodeInfoBase) -> Unit,
) {
    val scope = rememberCoroutineScope()
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
                    val script = setState.replace("\$state", if (newValue) "1" else "0")
                    runShellAsync(scope, shellRunner, script) { result ->
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
                    val script = setState.replace("\$state", selectedValue)
                    runShellAsync(scope, shellRunner, script) { result ->
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
                        node.activity.isNotBlank() -> {
                            try {
                                val intent = Intent(context, Class.forName(node.activity))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开 Activity: ${node.activity}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        online.isNotBlank() -> {
                            onOpenOnline(online, node.title)
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
                        val script = node.setState ?: ""
                        runShellAsync(scope, shellRunner, script) { result ->
                            Toast.makeText(
                                context,
                                result.ifEmpty { "执行完成" },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
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
    val selectedIndex: MutableState<Int> = mutableStateOf(
        param.options?.indexOfFirst { it.value == param.value }?.coerceAtLeast(0) ?: 0
    ),
    val color: MutableState<Color> = mutableStateOf(parseColor(param.value)),
    val selectedOptions: MutableState<Set<Int>> = mutableStateOf(
        if (param.multiple && !param.value.isNullOrBlank()) {
            val selectedValues = param.value!!.split(param.separator).map { it.trim() }.toSet()
            param.options?.mapIndexedNotNull { index, option ->
                if (option.value in selectedValues) index else null
            }?.toSet() ?: emptySet()
        } else {
            emptySet()
        }
    ),
)

@Composable
private fun ActionParamsDialog(
    action: ActionNode?,
    context: Context,
    shellRunner: ShellRunner,
    fileValues: MutableMap<String, String>,
    onOpenFileSelector: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val params = action?.params ?: emptyList()
    val states = remember(action) {
        params.map { ParamUiState(it) }
    }
    OverlayDialog(
        show = action != null,
        title = action?.title,
        summary = action?.summary?.ifEmpty { action.desc } ?: "",
        onDismissRequest = onDismiss
    ) {
        if (action != null) {
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
                        val name = param.name ?: ""
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextField(
                                value = TextFieldValue(fileValues[name] ?: ""),
                                onValueChange = { fileValues[name] = it.text },
                                label = param.title ?: param.name ?: "",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextButton(
                                text = "选择文件",
                                onClick = {
                                    onOpenFileSelector(name)
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
                            param.type == "file" || param.type == "path" ->
                                fileValues[param.name ?: ""] ?: ""
                            else -> states[index].text.value.text
                        }
                        values[name] = value
                    }
                    var script = action.setState ?: ""
                    values.forEach { (key, value) ->
                        script = script.replace("\$key", value)
                    }
                    runShellAsync(scope, shellRunner, script) { result ->
                        if (result.isNotBlank()) {
                            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
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

private fun loadThemeMode(prefs: SharedPreferences): ColorSchemeMode {
    val name = prefs.getString("mode", ColorSchemeMode.System.name)
        ?: ColorSchemeMode.System.name
    return try {
        ColorSchemeMode.valueOf(name)
    } catch (e: Exception) {
        ColorSchemeMode.System
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

private fun runShellAsync(
    scope: CoroutineScope,
    shellRunner: ShellRunner,
    script: String,
    onResult: (String) -> Unit,
) {
    if (script.isBlank()) {
        onResult("")
        return
    }
    scope.launch(Dispatchers.IO) {
        val result = shellRunner.execute(script)
        withContext(Dispatchers.Main) {
            onResult(result)
        }
    }
}

