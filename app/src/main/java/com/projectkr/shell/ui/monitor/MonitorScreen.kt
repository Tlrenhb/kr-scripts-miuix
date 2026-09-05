// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectkr.shell.ui.home.DeviceStats
import com.projectkr.shell.ui.home.TrendChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val SAMPLE_INTERVAL_MS = 1500L
private const val MAX_SAMPLES = 60

/**
 * Full monitoring detail for the dashboard. It intentionally has no control
 * state: all values are read-only telemetry and it shares the Home-screen data
 * source/refresh cadence.
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
            val fraction = if (mem.second > 0) mem.first.toFloat() / mem.second else 0f
            memSamples.add(fraction)
            if (memSamples.size > MAX_SAMPLES) memSamples.removeAt(0)

            cpuUsage = usage
            coreFreqs = freqs
            memUsed = mem.first
            memTotal = mem.second
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            batteryLevel = withContext(Dispatchers.IO) { DeviceStats.batteryLevel() }
            batteryTemp = withContext(Dispatchers.IO) { DeviceStats.batteryTemp() }
            delay(5000L)
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = "实时监控",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
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
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { SmallTitle(text = "CPU") }
            item {
                MonitorCard {
                    TelemetryHeading(
                        "使用率" + if (cpuUsage >= 0) " ${(cpuUsage * 100).toInt()}%" else "",
                    )
                    Spacer(Modifier.height(8.dp))
                    TrendChart(samples = cpuSamples.toList(), maxValue = 1f)
                }
            }

            item { SmallTitle(text = "内存") }
            item {
                MonitorCard {
                    TelemetryHeading("${fmt(memUsed)} / ${fmt(memTotal)}")
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

            item { SmallTitle(text = "CPU 核心频率") }
            item {
                MonitorCard {
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
                MonitorCard {
                    TelemetryInfo(
                        label = "电量",
                        value = if (batteryLevel >= 0) "$batteryLevel%" else "-",
                    )
                    TelemetryInfo(
                        label = "温度",
                        value = if (!batteryTemp.isNaN()) String.format("%.1f ℃", batteryTemp) else "-",
                        bottomPadding = 0.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        insideMargin = PaddingValues(16.dp),
    ) {
        content()
    }
}

@Composable
private fun TelemetryHeading(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.headline1,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun TelemetryInfo(
    label: String,
    value: String,
    bottomPadding: androidx.compose.ui.unit.Dp = 16.dp,
) {
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
