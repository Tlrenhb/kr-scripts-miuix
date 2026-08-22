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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectkr.krscript.core.model.RunnableNode
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
    var memUsed by remember { mutableLongStateOf(0L) }
    var memTotal by remember { mutableLongStateOf(0L) }
    var batteryLevel by remember { mutableIntStateOf(-1) }
    var batteryTemp by remember { mutableStateOf(Float.NaN) }
    var coreFreqs by remember { mutableStateOf<List<Int>>(emptyList()) }
    val cpuSamples = remember { mutableStateListOf<Float>() }
    val memSamples = remember { mutableStateListOf<Float>() }

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

    val scope = rememberCoroutineScope()
    var powerMenu by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<ScriptActions.Session?>(null) }

    session?.let { active ->
        LogDialog(session = active, show = true, onClose = { session = null })
        LaunchedEffect(active) {
            while (active.running) delay(300)
            delay(1200)
            session = null
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = "首页",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { powerMenu = true }) {
                        Icon(MiuixIcons.Reset, contentDescription = "电源菜单")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
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
                            "CPU 使用率" + if (cpuUsage >= 0) " ${(cpuUsage * 100).toInt()}%" else "",
                        )
                        TrendChart(samples = cpuSamples.toList(), maxValue = 1f)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "内存 ${fmt(memUsed)} / ${fmt(memTotal)}",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        LinearProgressIndicator(
                            progress = if (memTotal > 0) memUsed.toFloat() / memTotal else 0f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        TrendChart(samples = memSamples.toList(), maxValue = 1f)
                    }
                }
            }

            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        SectionTitle("CPU 核心频率")
                        if (coreFreqs.isEmpty()) {
                            Text("(暂无数据)", fontSize = 13.sp)
                        } else {
                            coreFreqs.forEachIndexed { index, freq ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("CPU $index", fontSize = 13.sp)
                                    Text(if (freq > 0) "${freq / 1000} MHz" else "-", fontSize = 13.sp)
                                }
                            }
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
                        title = "选择文件",
                        summary = "浏览设备文件",
                        onClick = onOpenFileSelector,
                    )
                }
            }
        }
    }

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
                val s = withContext(Dispatchers.IO) {
                    ScriptActions.stream(node, node.setState.orEmpty())
                }
                session = s
            }
        },
    )
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
