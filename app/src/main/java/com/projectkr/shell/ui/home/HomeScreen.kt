// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.projectkr.shell.core.shell.KeepShellPublic
import com.projectkr.shell.service.FloatMonitorService
import com.projectkr.shell.ui.monitor.CpuLoadSampler
import com.projectkr.shell.ui.monitor.CpuMonitor
import com.projectkr.shell.ui.monitor.GpuMonitor
import com.projectkr.shell.ui.monitor.MemoryInfo
import com.projectkr.shell.ui.monitor.MemoryMonitor
import com.projectkr.shell.ui.monitor.formatFreq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/**
 * 首页仪表盘（FRAMEWORK_PARITY §5.7，对齐原版 FragmentHome）。
 *
 * - 设备信息：型号 / Android 版本 / CPU ABI / 核心数
 * - 1 秒定时刷新的动态区：每核频率网格、CPU 负载（/proc/stat 差值）、
 *   内存 + Swap（/proc/meminfo）、GPU 频率/负载（原版 GpuUtils 常见路径）
 * - 清理内存 / 清理 Swap：原版命令（KeepShellPublic.doCmdSync，IO 线程执行）
 * - 快捷操作：电源菜单 / 文件选择器 / 性能监控 / 悬浮监控开关
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenPowerMenu: () -> Unit,
    onOpenFileSelector: () -> Unit,
    onOpenMonitor: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var coreCount by remember { mutableIntStateOf(0) }
    var coreFreqs by remember { mutableStateOf<List<Long?>>(emptyList()) }
    var coreMinFreqs by remember { mutableStateOf<List<Long?>>(emptyList()) }
    var coreMaxFreqs by remember { mutableStateOf<List<Long?>>(emptyList()) }
    var cpuLoads by remember { mutableStateOf<Map<Int, Double>>(emptyMap()) }
    var gpuFreqMhz by remember { mutableStateOf<Long?>(null) }
    var gpuLoad by remember { mutableIntStateOf(-1) }
    var memory by remember { mutableStateOf(MemoryInfo()) }
    var floatRunning by remember { mutableStateOf(FloatMonitorService.isRunning) }

    val cpuLoadSampler = remember { CpuLoadSampler() }

    // 1 秒定时刷新（IO 线程采样，主线程更新状态）——对应原版 Timer(0, 1000)
    LaunchedEffect(Unit) {
        var tick = 0
        while (isActive) {
            val snapshot = withContext(Dispatchers.IO) {
                val count = if (coreCount > 0) coreCount else CpuMonitor.coreCount()
                val freqs = (0 until count).map { CpuMonitor.currentFreqKhz(it) }
                val minFreqs = (0 until count).map { CpuMonitor.minFreqKhz(it) }
                val maxFreqs = (0 until count).map { CpuMonitor.maxFreqKhz(it) }
                HomeSnapshot(
                    coreCount = count,
                    freqs = freqs,
                    minFreqs = minFreqs,
                    maxFreqs = maxFreqs,
                    loads = cpuLoadSampler.sample(),
                    gpuFreq = GpuMonitor.freqMhz(),
                    gpuLoad = GpuMonitor.loadPercent(),
                    memory = MemoryMonitor.read(),
                )
            }
            coreCount = snapshot.coreCount
            coreFreqs = snapshot.freqs
            coreMinFreqs = snapshot.minFreqs
            coreMaxFreqs = snapshot.maxFreqs
            cpuLoads = snapshot.loads
            gpuFreqMhz = snapshot.gpuFreq
            gpuLoad = snapshot.gpuLoad
            memory = snapshot.memory
            floatRunning = FloatMonitorService.isRunning
            // 原版策略：每 5 个 tick 清一次 min/max 频率缓存
            tick++
            if (tick > 5) {
                tick = 0
                CpuMonitor.clearMinMaxCache()
            }
            delay(1000)
        }
    }

    // 悬浮监控：POST_NOTIFICATIONS（API 33+）在启动服务前请求
    val startFloatMonitor = remember(context) {
        {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatMonitorService::class.java),
            )
            floatRunning = true
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // 前台服务不依赖通知展示权限：拒绝时仅提示，服务照常启动
        if (!granted) {
            Toast.makeText(context, "未授予通知权限，悬浮窗运行通知将不显示", Toast.LENGTH_SHORT).show()
        }
        startFloatMonitor()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
        // 设备信息
        item(key = "title_device") { SmallTitle(text = "设备信息") }
        item(key = "card_device") {
            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                InfoRow("设备型号", Build.MODEL)
                InfoRow("Android 版本", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                InfoRow("CPU ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "--")
                InfoRow("核心数", if (coreCount > 0) "$coreCount 核心" else "--")
            }
        }

        // 性能状态
        item(key = "title_perf") { SmallTitle(text = "性能状态") }
        item(key = "card_perf") {
            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                val totalLoad = cpuLoads[-1]
                InfoRow(
                    "CPU 总负载",
                    if (totalLoad != null) String.format(Locale.getDefault(), "%.0f%%", totalLoad) else "--",
                )
                // 每核频率网格（原版 AdapterCpuCores：2 列网格，cur/min/max + 负载）
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    coreFreqs.indices.chunked(2).forEach { rowIndices ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowIndices.forEach { core ->
                                Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                    Text(
                                        text = "cpu$core  ${formatFreq(coreFreqs.getOrNull(core))}",
                                        style = MiuixTheme.textStyles.body1,
                                    )
                                    val minF = coreMinFreqs.getOrNull(core)
                                    val maxF = coreMaxFreqs.getOrNull(core)
                                    Text(
                                        text = buildString {
                                            if (minF != null && maxF != null) {
                                                append("${minF / 1000}~${maxF / 1000}MHz")
                                            }
                                            val load = cpuLoads[core]
                                            if (load != null) {
                                                if (isNotEmpty()) append("  ")
                                                append(String.format(Locale.getDefault(), "负载 %.0f%%", load))
                                            }
                                        },
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                            if (rowIndices.size == 1) {
                                Column(modifier = Modifier.weight(1f)) {}
                            }
                        }
                    }
                }
                InfoRow(
                    "GPU",
                    buildString {
                        append(gpuFreqMhz?.let { "$it MHz" } ?: "--")
                        if (gpuLoad >= 0) append("  负载 $gpuLoad%")
                    },
                )
            }
        }

        // 内存
        item(key = "title_mem") { SmallTitle(text = "内存") }
        item(key = "card_mem") {
            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                // 原版：home_raminfo_text = "x% (yGB)"
                val totalGb = memory.memTotalKb / 1024 / 1024 + 1
                InfoRow("RAM", "${memory.memUsedPercent}% (${totalGb}GB)")
                val swapText = if (memory.swapTotalKb > 0) {
                    if (memory.swapTotalKb / 1024 > 99) {
                        "${memory.swapUsedPercent}% (${String.format(Locale.getDefault(), "%.1f", memory.swapTotalKb / 1024f / 1024f)}GB)"
                    } else {
                        "${memory.swapUsedPercent}% (${memory.swapTotalKb / 1024}MB)"
                    }
                } else {
                    "--"
                }
                InfoRow("Swap", swapText)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "清理内存",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    // 原版命令：sync + drop_caches + compact_memory
                                    KeepShellPublic.doCmdSync(
                                        "sync\n" +
                                            "echo 3 > /proc/sys/vm/drop_caches\n" +
                                            "echo 1 > /proc/sys/vm/compact_memory",
                                    )
                                    delay(600)
                                    memory = MemoryMonitor.read()
                                }
                                Toast.makeText(context, "已回收用于缓存的RAM", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    TextButton(
                        text = "清理 Swap",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    // 原版命令：sync + compact_memory
                                    KeepShellPublic.doCmdSync(
                                        "sync\necho 1 > /proc/sys/vm/compact_memory",
                                    )
                                    delay(600)
                                    memory = MemoryMonitor.read()
                                }
                                Toast.makeText(context, "RAM碎片已整理", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }

        // 快捷操作
        item(key = "title_actions") { SmallTitle(text = "快捷操作") }
        item(key = "card_actions") {
            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                ArrowPreference(title = "电源菜单", summary = "重启 / Recovery / Bootloader / 关机", onClick = onOpenPowerMenu)
                ArrowPreference(title = "文件选择器", summary = "浏览并选择文件或目录", onClick = onOpenFileSelector)
                ArrowPreference(title = "性能监控", summary = "实时 CPU / 电池 / 内存曲线", onClick = onOpenMonitor)
                SwitchPreference(
                    title = "悬浮监控",
                    summary = "悬浮窗显示 CPU / 内存 / 电池（双击悬浮窗关闭）",
                    checked = floatRunning,
                    onCheckedChange = { checked ->
                        if (checked) {
                            when {
                                !Settings.canDrawOverlays(context) -> {
                                    Toast.makeText(context, "请授权显示悬浮窗权限！", Toast.LENGTH_LONG).show()
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                }
                                Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED -> {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                else -> startFloatMonitor()
                            }
                        } else {
                            context.stopService(Intent(context, FloatMonitorService::class.java))
                            floatRunning = false
                        }
                    },
                )
            }
        }
    }
}

private data class HomeSnapshot(
    val coreCount: Int,
    val freqs: List<Long?>,
    val minFreqs: List<Long?>,
    val maxFreqs: List<Long?>,
    val loads: Map<Int, Double>,
    val gpuFreq: Long?,
    val gpuLoad: Int,
    val memory: MemoryInfo,
)

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
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
