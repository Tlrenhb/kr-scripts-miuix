// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectkr.shell.runtime.ScriptActions
import com.projectkr.shell.ui.dialogs.LogDialog
import com.projectkr.shell.ui.home.DeviceStats
import com.projectkr.shell.ui.home.TrendChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SAMPLE_INTERVAL_MS = 1500L
private const val MAX_SAMPLES = 60

/**
 * Live monitor: CPU/RAM trends, per-core frequencies and battery — the in-app
 * counterpart of [com.projectkr.shell.service.FloatMonitorService].
 */
@Composable
fun MonitorScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
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
            val usage = withContext(Dispatchers.IO) { DeviceStats.cpuUsage() }
            val freqs = withContext(Dispatchers.IO) { DeviceStats.cpuFrequencies() }
            val mem = withContext(Dispatchers.IO) { DeviceStats.memoryInfo() }

            if (usage >= 0f) {
                cpuSamples.add(usage)
                if (cpuSamples.size > MAX_SAMPLES) cpuSamples.removeAt(0)
            }
            val frac =
                if (mem.second > 0) mem.first.toFloat() / mem.second else 0f
            memSamples.add(frac)
            if (memSamples.size > MAX_SAMPLES) memSamples.removeAt(0)

            cpuUsage = usage
            coreFreqs = freqs
            memUsed = mem.first
            memTotal = mem.second
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    // Battery read on a slower cadence (sysfs values change slowly).
    LaunchedEffect(Unit) {
        while (true) {
            batteryLevel = withContext(Dispatchers.IO) { DeviceStats.batteryLevel() }
            batteryTemp = withContext(Dispatchers.IO) { DeviceStats.batteryTemp() }
            delay(5000L)
        }
    }

    var session by remember { mutableStateOf<ScriptActions.Session?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            SmallTopAppBar(
                title = "监控",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            item {
                MonitorCard("CPU 使用率" + if (cpuUsage >= 0) " ${(cpuUsage * 100).toInt()}%" else "") {
                    TrendChart(samples = cpuSamples.toList(), maxValue = 1f)
                }
            }
            item {
                MonitorCard("内存 ${(fmt(memUsed))} / ${fmt(memTotal)}") {
                    LinearProgressIndicator(
                        progress = if (memTotal > 0) memUsed.toFloat() / memTotal else 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                    TrendChart(samples = memSamples.toList(), maxValue = 1f)
                }
            }
            item {
                MonitorCard("CPU 核心频率") {
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
            item {
                MonitorCard("电池") {
                    InfoRow("电量", if (batteryLevel >= 0) "$batteryLevel%" else "-")
                    InfoRow(
                        "温度",
                        if (!batteryTemp.isNaN()) String.format("%.1f ℃", batteryTemp) else "-",
                    )
                }
            }
        }

        session?.let { active ->
            LogDialog(session = active, show = true, onClose = { session = null })
        }
    }
}

@Composable
private fun MonitorCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
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
