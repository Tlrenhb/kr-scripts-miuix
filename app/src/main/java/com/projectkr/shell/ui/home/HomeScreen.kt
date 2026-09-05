// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.home

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectkr.krscript.core.model.RunnableNode
import com.projectkr.shell.runtime.ScriptActions
import com.projectkr.shell.ui.dialogs.LogDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val SAMPLE_INTERVAL_MS = 1500L
private const val MAX_SAMPLES = 60

private data class StatsSample(
    val usage: Float,
    val freqs: List<Int>,
    val memUsed: Long,
    val memTotal: Long,
    val level: Int,
    val temp: Float,
)

/**
 * 首页: device info, CPU trend + per-core frequencies, memory usage, battery
 * status and the power menu — mirroring the original FragmentHome.
 */
@Composable
fun HomeScreen(
    rooted: Boolean,
    onOpenFileSelector: () -> Unit,
    onOpenMonitor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var cpuUsage by remember { mutableFloatStateOf(-1f) }
    val animCpuUsage by androidx.compose.animation.core.animateFloatAsState(
        targetValue = cpuUsage.coerceAtLeast(0f),
        animationSpec = androidx.compose.animation.core.tween(700),
        label = "cpu-usage",
    )
    var memUsed by remember { mutableLongStateOf(0L) }
    var memTotal by remember { mutableLongStateOf(0L) }
    var batteryLevel by remember { mutableIntStateOf(-1) }
    var batteryTemp by remember { mutableStateOf(Float.NaN) }
    var coreFreqs by remember { mutableStateOf<List<Int>>(emptyList()) }
    // FloatMonitor.running is Compose state, so the row also updates when the
    // overlay is closed by its original double-tap gesture.
    val floatSummary = if (com.projectkr.shell.service.FloatMonitor.running) {
        "运行中 · 关闭开关即停止"
    } else {
        "在桌面显示 CPU/GPU/RAM/电池监控"
    }
    val cpuSamples = remember { mutableStateListOf<Float>() }
    val memSamples = remember { mutableStateListOf<Float>() }
    val animMemUsed by androidx.compose.animation.core.animateFloatAsState(
        targetValue = memUsed.toFloat(),
        animationSpec = androidx.compose.animation.core.tween(700),
        label = "mem-used",
    )

    LaunchedEffect(Unit) {
        while (true) {
            val sample = withContext(Dispatchers.IO) {
                StatsSample(
                    usage = DeviceStats.cpuUsage(),
                    freqs = DeviceStats.cpuFrequencies(),
                    memUsed = DeviceStats.memoryInfo().first,
                    memTotal = DeviceStats.memoryInfo().second,
                    level = DeviceStats.batteryLevel(),
                    temp = DeviceStats.batteryTemp(),
                )
            }
            if (sample.usage >= 0f) {
                cpuSamples.add(sample.usage)
                if (cpuSamples.size > MAX_SAMPLES) cpuSamples.removeAt(0)
            }
            val frac =
                if (sample.memTotal > 0) sample.memUsed.toFloat() / sample.memTotal else 0f
            memSamples.add(frac)
            if (memSamples.size > MAX_SAMPLES) memSamples.removeAt(0)

            cpuUsage = sample.usage
            coreFreqs = sample.freqs
            memUsed = sample.memUsed
            memTotal = sample.memTotal
            batteryLevel = sample.level
            batteryTemp = sample.temp
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var powerMenu by remember { mutableStateOf(false) }

    // Original CheckRootStatus: a non-dismissable retry/skip gate on first entry.
    var showRootGuide by remember {
        mutableStateOf(com.projectkr.shell.runtime.KrScriptRuntime.isReady &&
            !com.projectkr.shell.runtime.KrScriptRuntime.rooted)
    }
    if (showRootGuide && !rooted) {
        top.yukonga.miuix.kmp.overlay.OverlayDialog(
            title = "未检测到 ROOT 权限",
            summary = "本应用的功能大多需要 ROOT。你可以重试授权、跳过继续使用非 ROOT 功能，或退出应用。",
            show = true,
            onDismissRequest = { showRootGuide = false },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = "重试",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showRootGuide = false
                        scope.launch(Dispatchers.IO) {
                            val ok = com.projectkr.shell.runtime.KrScriptRuntime.retry()
                            if (ok && com.projectkr.shell.runtime.KrScriptRuntime.rooted) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "ROOT 已授权", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                )
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = "跳过",
                    modifier = Modifier.weight(1f),
                    onClick = { showRootGuide = false },
                )
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = "退出",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        (context as? Activity)?.finishAffinity()
                    },
                )
            }
        }
    }
    var session by remember { mutableStateOf<ScriptActions.Session?>(null) }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = "首页",
                scrollBehavior = scrollBehavior,
                actions = {
                    com.projectkr.shell.ui.common.HintedAction(
                        text = "详细监控",
                        icon = MiuixIcons.Stopwatch,
                        onClick = onOpenMonitor,
                    )
                    com.projectkr.shell.ui.common.HintedAction(
                        text = "电源菜单",
                        icon = MiuixIcons.Reset,
                        onClick = { powerMenu = true },
                    )
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            item {
                // KernelSU-style status surface. Use semantic Miuix containers
                // so this remains correct for light, dark, and Monet palettes.
                val statusColor = if (rooted) {
                    MiuixTheme.colorScheme.primaryContainer
                } else {
                    MiuixTheme.colorScheme.errorContainer
                }
                val statusContentColor = if (rooted) {
                    MiuixTheme.colorScheme.onPrimaryContainer
                } else {
                    MiuixTheme.colorScheme.onErrorContainer
                }
                val statusAccent = if (rooted) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.error
                }
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.defaultColors(
                        color = statusColor,
                        contentColor = statusContentColor,
                    ),
                    onClick = onOpenMonitor,
                    showIndication = true,
                    pressFeedbackType = PressFeedbackType.Sink,
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = if (rooted) MiuixIcons.Ok else MiuixIcons.Unlock,
                            contentDescription = null,
                            tint = statusAccent.copy(alpha = 0.16f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 38.dp, y = 45.dp)
                                .size(170.dp),
                        )
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                text = if (rooted) "ROOT 已授权" else "ROOT 未授权",
                                style = MiuixTheme.textStyles.title3,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
                                style = MiuixTheme.textStyles.body2,
                                color = statusContentColor.copy(alpha = 0.72f),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "轻触查看实时监控",
                                style = MiuixTheme.textStyles.footnote1,
                                color = statusContentColor.copy(alpha = 0.72f),
                            )
                        }
                    }
                }
            }

            item {
                // Live metrics as tactile cards, following KernelSU's dashboard hierarchy.
                Row(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatMiniCard(
                        label = "CPU",
                        value = if (cpuUsage >= 0) "${(cpuUsage * 100).toInt()}%" else "-",
                        summary = "实时负载",
                        onClick = onOpenMonitor,
                        modifier = Modifier.weight(1f),
                    )
                    StatMiniCard(
                        label = "内存",
                        value = if (memTotal > 0) fmt(memUsed) else "-",
                        summary = if (memTotal > 0) "共 ${fmt(memTotal)}" else "正在读取",
                        onClick = onOpenMonitor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item { SmallTitle(text = "设备信息") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    InfoRow("型号", Build.MODEL)
                    InfoRow("Android", "${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
                    InfoRow("内核", System.getProperty("os.version").orEmpty(), bottomPadding = 0.dp)
                }
            }

            item { SmallTitle(text = "实时状态") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Text(
                        text = "CPU 使用率" + if (cpuUsage >= 0) " ${(animCpuUsage * 100).toInt()}%" else "",
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    TrendChart(samples = cpuSamples.toList(), maxValue = 1f)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "内存 ${fmt(animMemUsed.toLong())} / ${fmt(memTotal)}",
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Medium,
                    )
                    LinearProgressIndicator(
                        progress = if (memTotal > 0) memUsed.toFloat() / memTotal else 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    TrendChart(samples = memSamples.toList(), maxValue = 1f)
                }
            }

            item { SmallTitle(text = "权限") }
            item {
                com.projectkr.shell.ui.home.PermissionCard(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                )
            }

            item { SmallTitle(text = "CPU 核心频率") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    if (coreFreqs.isEmpty()) {
                        Text(
                            text = "暂无数据",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        coreFreqs.forEachIndexed { index, freq ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "CPU $index",
                                    style = MiuixTheme.textStyles.footnote1,
                                )
                                Text(
                                    text = if (freq > 0) "${freq / 1000} MHz" else "-",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }

            item { SmallTitle(text = "电池") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    InfoRow("电量", if (batteryLevel >= 0) "$batteryLevel%" else "-")
                    InfoRow(
                        label = "温度",
                        value = if (!batteryTemp.isNaN()) String.format("%.1f ℃", batteryTemp) else "-",
                        bottomPadding = 0.dp,
                    )
                }
            }

            item {
                SmallTitle(text = "工具")
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    SwitchPreference(
                        title = "悬浮窗监控",
                        summary = floatSummary,
                        checked = com.projectkr.shell.service.FloatMonitor.running,
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Layers,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                        },
                        onCheckedChange = {
                            com.projectkr.shell.service.FloatMonitor.toggle(context)
                        },
                    )
                    ArrowPreference(
                        title = "选择文件",
                        summary = "浏览设备文件",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.File,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                        },
                        onClick = onOpenFileSelector,
                    )
                }
            }
        }

        // Overlay dialogs must stay inside the Scaffold popup host, composed
        // with `show` bound to state so the hide animation runs.
        var logVisible by remember { mutableStateOf(false) }
        LaunchedEffect(session) {
            if (session != null) {
                logVisible = true
            }
        }
        LaunchedEffect(session?.running, session?.exitCode) {
            val done = session != null && !session!!.running
            if (done && session?.node?.autoOff == true) {
                delay(600)
                logVisible = false
            }
        }
        LogDialog(
            session = session,
            show = logVisible,
            onClose = {
                logVisible = false
                session = null
            },
        )

        PowerMenuDialog(
            show = powerMenu,
            rooted = rooted,
            onDismiss = { powerMenu = false },
            onStartAction = { action ->
                val node = RunnableNode("").apply {
                    this.title = action.label
                    this.setState = action.command
                }
                scope.launch {
                    val psession = withContext(Dispatchers.IO) {
                        ScriptActions.stream(node, node.setState.orEmpty())
                    }
                    session = psession
                }
            },
        )
    }
}

@Composable
private fun StatMiniCard(
    label: String,
    value: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
        showIndication = true,
        // Sink preserves Miuix feedback without reintroducing the removed
        // card-tilt / gesture-physics treatment.
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = summary,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, bottomPadding: androidx.compose.ui.unit.Dp = 16.dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun fmt(bytes: Long): String = when {
    bytes >= (1L shl 30) -> String.format("%.1f GB", bytes / 1073741824f)
    bytes >= (1L shl 20) -> String.format("%.0f MB", bytes / 1048576f)
    else -> "$bytes B"
}
