// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.home

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectkr.krscript.core.model.RunnableNode
import com.projectkr.shell.runtime.KrScriptRuntime
import com.projectkr.shell.runtime.ScriptActions
import com.projectkr.shell.ui.dialogs.LogDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    var coreMins by remember { mutableStateOf<List<Int>>(emptyList()) }
    var coreMaxs by remember { mutableStateOf<List<Int>>(emptyList()) }
    var coreLoads by remember { mutableStateOf<List<Float>>(emptyList()) }
    var swapUsed by remember { mutableLongStateOf(0L) }
    var swapTotal by remember { mutableLongStateOf(0L) }
    var gpuFreqKHz by remember { mutableLongStateOf(0L) }
    var gpuLoad by remember { mutableIntStateOf(-1) }
    var floatSummary by remember {
        mutableStateOf(if (com.projectkr.shell.service.FloatMonitor.running) "运行中 · 点击停止" else "在桌面显示 CPU/RAM 悬浮窗")
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

            val mm = withContext(Dispatchers.IO) { DeviceStats.cpuMinMax() }
            coreMins = mm.first
            coreMaxs = mm.second
            coreLoads = withContext(Dispatchers.IO) { DeviceStats.perCoreLoad() }
            val swap = withContext(Dispatchers.IO) { DeviceStats.swapInfo() }
            swapUsed = swap.first
            swapTotal = swap.second
            val gpu = withContext(Dispatchers.IO) { DeviceStats.gpuInfo() }
            gpuFreqKHz = gpu.first
            gpuLoad = gpu.second
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var powerMenu by remember { mutableStateOf(false) }
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
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        SectionTitle("设备信息")
                        InfoRow("型号", Build.MODEL)
                        InfoRow("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
                        InfoRow("内核", System.getProperty("os.version").orEmpty())
                        InfoRow("ROOT", if (rooted) "已授权" else "未授权")
                    }
                }
            }

            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        SectionTitle(
                            "CPU 使用率" + if (cpuUsage >= 0) " ${(animCpuUsage * 100).toInt()}%" else "",
                        )
                        TrendChart(samples = cpuSamples.toList(), maxValue = 1f)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "内存 ${fmt(animMemUsed.toLong())} / ${fmt(memTotal)}",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        LinearProgressIndicator(
                            progress = if (memTotal > 0) memUsed.toFloat() / memTotal else 0f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        )
                        if (swapTotal > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Swap ${fmt(swapUsed)} / ${fmt(swapTotal)}",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        TrendChart(samples = memSamples.toList(), maxValue = 1f)
                        if (rooted) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                top.yukonga.miuix.kmp.basic.TextButton(
                                    text = "清理内存",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            KrScriptRuntime.shell.execute(
                                                "sync\necho 3 > /proc/sys/vm/drop_caches\necho 1 > /proc/sys/vm/compact_memory",
                                            )
                                        }
                                    },
                                )
                                top.yukonga.miuix.kmp.basic.TextButton(
                                    text = "清理 Swap",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            KrScriptRuntime.shell.execute(
                                                "sync\necho 1 > /proc/sys/vm/compact_memory",
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                com.projectkr.shell.ui.home.PermissionCard(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        SectionTitle("CPU 核心频率")
                        if (coreFreqs.isEmpty()) {
                            Text("(暂无数据)", fontSize = 13.sp)
                        } else {
                            coreFreqs.forEachIndexed { index, freq ->
                                val load = coreLoads.getOrNull(index) ?: -1f
                                val minMhz = coreMins.getOrNull(index)?.div(1000) ?: 0
                                val maxMhz = coreMaxs.getOrNull(index)?.div(1000) ?: 0
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "CPU $index" + if (load >= 0) " · ${(load * 100).toInt()}%" else "",
                                        fontSize = 13.sp,
                                    )
                                    Text(
                                        when {
                                            freq <= 0 -> "-"
                                            minMhz > 0 && maxMhz > 0 -> "${freq / 1000} MHz ($minMhz~$maxMhz)"
                                            else -> "${freq / 1000} MHz"
                                        },
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                if (gpuFreqKHz > 0) {
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            SectionTitle(
                                "GPU" + if (gpuLoad >= 0) " · $gpuLoad%" else "",
                            )
                            Text(
                                "${gpuFreqKHz / 1000000.0} MHz",
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        SectionTitle("电池")
                        InfoRow("电量", if (batteryLevel >= 0) "$batteryLevel%" else "-")
                        InfoRow(
                            "温度",
                            if (!batteryTemp.isNaN()) String.format("%.1f ℃", batteryTemp) else "-",
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    ArrowPreference(
                        title = "悬浮窗监控",
                        summary = floatSummary,
                        onClick = {
                            com.projectkr.shell.service.FloatMonitor.toggle(context)
                            floatSummary = if (com.projectkr.shell.service.FloatMonitor.running) {
                                "运行中 · 点击停止"
                            } else {
                                "在桌面显示 CPU/RAM 悬浮窗"
                            }
                        },
                    )
                    ArrowPreference(
                        title = "选择文件",
                        summary = "浏览设备文件",
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value, fontSize = 13.sp)
    }
}

private fun fmt(bytes: Long): String = when {
    bytes >= (1L shl 30) -> String.format("%.1f GB", bytes / 1073741824f)
    bytes >= (1L shl 20) -> String.format("%.0f MB", bytes / 1048576f)
    else -> "$bytes B"
}
