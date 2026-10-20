// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

private const val HISTORY_SIZE = 60

/**
 * 性能监控页（FRAMEWORK_PARITY §5.7 延展）。
 *
 * 实时 CPU 总负载曲线（Canvas，主题 primary 色）+ 每核频率/负载 + 电池
 * （电量/温度/电流，/sys/class/power_supply 尽力而为）+ 内存（含 Swap）。
 * 1 秒定时刷新，采样在 Dispatchers.IO。
 */
@Composable
fun MonitorScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current

    val loadHistory = remember { mutableStateListOf<Float>() }
    var coreCount by remember { mutableIntStateOf(0) }
    var coreFreqs by remember { mutableStateOf<List<Long?>>(emptyList()) }
    var cpuLoads by remember { mutableStateOf<Map<Int, Double>>(emptyMap()) }
    var battery by remember { mutableStateOf(BatteryInfo()) }
    var memory by remember { mutableStateOf(MemoryInfo()) }

    val cpuLoadSampler = remember { CpuLoadSampler() }

    LaunchedEffect(Unit) {
        while (isActive) {
            val snapshot = withContext(Dispatchers.IO) {
                val count = if (coreCount > 0) coreCount else CpuMonitor.coreCount()
                MonitorSnapshot(
                    coreCount = count,
                    freqs = (0 until count).map { CpuMonitor.currentFreqKhz(it) },
                    loads = cpuLoadSampler.sample(),
                    battery = BatteryMonitor.read(context.applicationContext),
                    memory = MemoryMonitor.read(),
                )
            }
            coreCount = snapshot.coreCount
            coreFreqs = snapshot.freqs
            cpuLoads = snapshot.loads
            battery = snapshot.battery
            memory = snapshot.memory
            val totalLoad = snapshot.loads[-1]?.toFloat()?.coerceIn(0f, 100f)
            if (totalLoad != null) {
                loadHistory.add(totalLoad)
                while (loadHistory.size > HISTORY_SIZE) loadHistory.removeAt(0)
            }
            delay(1000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
        // CPU 总负载曲线
        item(key = "title_cpu") { SmallTitle(text = "CPU") }
        item(key = "card_cpu") {
            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                val totalLoad = cpuLoads[-1]
                Text(
                    text = if (totalLoad != null) {
                        String.format(Locale.getDefault(), "总负载 %.0f%%", totalLoad)
                    } else {
                        "总负载 --"
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MiuixTheme.textStyles.body1,
                )
                LoadChart(
                    samples = loadHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    coreFreqs.indices.chunked(2).forEach { rowIndices ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowIndices.forEach { core ->
                                val load = cpuLoads[core]
                                Text(
                                    text = buildString {
                                        append("cpu$core  ${formatFreq(coreFreqs.getOrNull(core))}")
                                        if (load != null) {
                                            append(String.format(Locale.getDefault(), "  %.0f%%", load))
                                        }
                                    },
                                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                                    style = MiuixTheme.textStyles.body2,
                                )
                            }
                            if (rowIndices.size == 1) {
                                Column(modifier = Modifier.weight(1f)) {}
                            }
                        }
                    }
                }
            }
        }

        // 电池
        item(key = "title_battery") { SmallTitle(text = "电池") }
        item(key = "card_battery") {
            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                MonitorRow("电量", if (battery.level >= 0) "${battery.level}%" else "--")
                MonitorRow(
                    "温度",
                    if (battery.temperatureC >= 0) {
                        String.format(Locale.getDefault(), "%.1f°C", battery.temperatureC)
                    } else {
                        "--"
                    },
                )
                MonitorRow("电流", if (battery.currentMa != -1) "${battery.currentMa} mA" else "--")
            }
        }

        // 内存
        item(key = "title_mem") { SmallTitle(text = "内存") }
        item(key = "card_mem") {
            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                MonitorRow(
                    "RAM",
                    if (memory.memTotalKb > 0) {
                        "${memory.memUsedPercent}% (${memory.memUsedKb / 1024}MB / ${memory.memTotalKb / 1024}MB)"
                    } else {
                        "--"
                    },
                )
                MonitorRow(
                    "Swap",
                    if (memory.swapTotalKb > 0) {
                        "${memory.swapUsedPercent}% (${memory.swapUsedKb / 1024}MB / ${memory.swapTotalKb / 1024}MB)"
                    } else {
                        "--"
                    },
                )
            }
        }
    }
}

private data class MonitorSnapshot(
    val coreCount: Int,
    val freqs: List<Long?>,
    val loads: Map<Int, Double>,
    val battery: BatteryInfo,
    val memory: MemoryInfo,
)

/** CPU 总负载折线图（主题 primary 色描边 + 淡填充基线网格） */
@Composable
private fun LoadChart(samples: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MiuixTheme.colorScheme.primary
    val gridColor = MiuixTheme.colorScheme.outline
    Canvas(modifier = modifier) {
        // 25/50/75% 参考线
        for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
            val y = size.height * (1f - fraction)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
        }
        if (samples.size >= 2) {
            val stepX = size.width / (HISTORY_SIZE - 1)
            val path = Path()
            samples.forEachIndexed { index, value ->
                // 采样从右侧对齐：不足一屏时曲线靠右
                val x = size.width - (samples.size - 1 - index) * stepX
                val y = size.height * (1f - value / 100f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = lineColor, style = Stroke(width = 4f))
        }
    }
}

@Composable
private fun MonitorRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body1,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
