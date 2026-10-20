// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.monitor

import android.content.Context
import android.os.BatteryManager
import com.projectkr.shell.core.shell.KeepShellPublic
import java.io.File

/**
 * 系统性能数据采样器（纯 Android/Kotlin，不依赖 Compose，
 * 供 HomeScreen / MonitorScreen / FloatMonitorService 共用）。
 *
 * 数据来源与原版（b90f2cc）对齐（FRAMEWORK_PARITY §5.7/§5.8/§5.13）：
 * - CPU 频率：/sys/devices/system/cpu/cpuN/cpufreq/ 下的 scaling_cur_freq /
 *   scaling_min_freq / scaling_max_freq（原版 `Constants.scaling_cur_freq` 实际指向
 *   cpuinfo_cur_freq，这里按任务约定直接读 scaling_cur_freq）。
 * - CPU 负载：/proc/stat 两次采样差值（移植原版 CpuLoadUtils 的算法）。
 * - GPU：优先 /sys/kernel/gpu/*，其次高通 /sys/class/kgsl/kgsl-3d0/*（原版 GpuUtils）。
 * - 内存（含 Swap）：/proc/meminfo。
 * - 电池：/sys/class/power_supply 尽力而为，电量回退 BatteryManager。
 *
 * 读取策略：先直接读文件（/proc 与部分 /sys 节点应用可读）；失败时回退到
 * [KeepShellPublic.doCmdSync] 的 root shell `cat`（与原版 KernelProrp 行为一致）。
 * 所有方法都可能阻塞（root 回退），调用方必须切到 Dispatchers.IO。
 */
internal object MonitorSysfs {

    /** 读取文本节点；直接读取失败时回退 root `cat`，再失败返回 null */
    fun readText(path: String): String? {
        try {
            val text = File(path).readText().trim()
            if (text.isNotEmpty()) return text
        } catch (ignored: Exception) {
        }
        return try {
            val out = KeepShellPublic.doCmdSync("cat '$path'")
            if (out == "error") null else out.trim().ifEmpty { null }
        } catch (ignored: Exception) {
            null
        }
    }

    /** 读取长整型节点（kHz / kB / µA 等原始值） */
    fun readLong(path: String): Long? = readText(path)?.toLongOrNull()

    /** 节点是否存在（先直接判断，再 root `test -f`） */
    fun exists(path: String): Boolean {
        if (File(path).exists()) return true
        return try {
            KeepShellPublic.doCmdSync("test -f '$path' && echo 1").trim() == "1"
        } catch (ignored: Exception) {
            false
        }
    }
}

/**
 * CPU 频率采样（每核 cur/min/max，min/max 带缓存——对应原版 FragmentHome
 * 每 5 个 tick 清空一次缓存的策略由调用方自行决定，这里提供 [clearMinMaxCache]）。
 */
internal object CpuMonitor {
    private const val CPU_DIR = "/sys/devices/system/cpu"
    private const val CPUFREQ = "cpufreq"

    private val minFreqCache = HashMap<Int, Long>()
    private val maxFreqCache = HashMap<Int, Long>()

    /** 核心数：优先数 /sys/devices/system/cpu/cpuN 目录，回退 availableProcessors */
    fun coreCount(): Int {
        try {
            val count = File(CPU_DIR).listFiles { file ->
                file.isDirectory && file.name.matches(Regex("cpu[0-9]+"))
            }?.size ?: 0
            if (count > 0) return count
        } catch (ignored: Exception) {
        }
        return Runtime.getRuntime().availableProcessors()
    }

    /** 当前频率（kHz），离线/读取失败返回 null */
    fun currentFreqKhz(core: Int): Long? =
        MonitorSysfs.readLong("$CPU_DIR/cpu$core/$CPUFREQ/scaling_cur_freq")

    fun minFreqKhz(core: Int): Long? = minFreqCache.getOrPutNullable(core) {
        MonitorSysfs.readLong("$CPU_DIR/cpu$core/$CPUFREQ/scaling_min_freq")
    }

    fun maxFreqKhz(core: Int): Long? = maxFreqCache.getOrPutNullable(core) {
        MonitorSysfs.readLong("$CPU_DIR/cpu$core/$CPUFREQ/scaling_max_freq")
    }

    /** 核心是否在线（cpu0 恒在线） */
    fun isOnline(core: Int): Boolean =
        core == 0 || MonitorSysfs.readText("$CPU_DIR/cpu$core/online") != "0"

    /** 全核最高当前频率（kHz）——原版 FloatMonitor 显示策略 */
    fun maxCurrentFreqKhz(): Long? {
        var max: Long? = null
        for (core in 0 until coreCount()) {
            val freq = currentFreqKhz(core) ?: continue
            if (max == null || freq > max) max = freq
        }
        return max
    }

    fun clearMinMaxCache() {
        minFreqCache.clear()
        maxFreqCache.clear()
    }

    private inline fun <K, V> HashMap<K, V>.getOrPutNullable(key: K, loader: () -> V?): V? {
        if (!containsKey(key)) {
            val value = loader() ?: return null
            put(key, value)
        }
        return get(key)
    }
}

/**
 * CPU 负载采样（移植原版 CpuLoadUtils：/proc/stat 两次快照的差值百分比）。
 * 首次调用建立基线并立即二次采样（内部 sleep 100ms，与原版一致）。
 * 返回 Map：key -1 = 总负载，0..N-1 = 每核负载；采样失败返回空 Map。
 */
internal class CpuLoadSampler {
    private var lastCpuState: String = ""

    private fun readCpuTimes(): String {
        return try {
            File("/proc/stat").readLines()
                .filter { it.startsWith("cpu") }
                .joinToString("\n")
        } catch (ignored: Exception) {
            ""
        }
    }

    private fun cpuIndex(cols: List<String>): Int =
        if (cols[0] == "cpu") -1 else cols[0].substring(3).toIntOrNull() ?: -2

    private fun totalTime(cols: List<String>): Long {
        var total = 0L
        for (i in 1 until cols.size) total += cols[i].toLongOrNull() ?: 0L
        return total
    }

    private fun idleTime(cols: List<String>): Long = cols.getOrNull(4)?.toLongOrNull() ?: 0L

    @Synchronized
    fun sample(): Map<Int, Double> {
        val loads = HashMap<Int, Double>()
        val times = readCpuTimes()
        if (times.isEmpty() || !times.startsWith("cpu")) return loads
        try {
            if (lastCpuState.isEmpty()) {
                lastCpuState = times
                Thread.sleep(100)
                return sample()
            }
            val curTick = times.split("\n")
            val prevTick = lastCpuState.split("\n")
            for (cpuCurrentTime in curTick) {
                val cols1 = cpuCurrentTime.replace(Regex(" {2}"), " ").split(" ")
                var cols0: List<String>? = null
                for (cpu in prevTick) {
                    // startsWith 必须带空格，避免 "cpu" 匹配到 "cpu0"
                    if (cpu.startsWith(cols1[0] + " ")) {
                        cols0 = cpu.replace(Regex(" {2}"), " ").split(" ")
                        break
                    }
                }
                val index = cpuIndex(cols1)
                if (index == -2) continue
                if (cols0 != null && cols0.isNotEmpty()) {
                    val total1 = totalTime(cols1)
                    val idle1 = idleTime(cols1)
                    val total0 = totalTime(cols0)
                    val idle0 = idleTime(cols0)
                    val timePoor = total1 - total0
                    if (timePoor == 0L) {
                        loads[index] = 0.0
                    } else {
                        val idlePoor = idle1 - idle0
                        loads[index] = if (idlePoor < 1) 100.0 else 100 - idlePoor * 100.0 / timePoor
                    }
                } else {
                    loads[index] = 0.0
                }
            }
            lastCpuState = times
        } catch (ignored: Exception) {
        }
        return loads
    }
}

/** GPU 频率/负载（原版 GpuUtils 的常见路径，尽力而为；首次使用时探测并缓存路径） */
internal object GpuMonitor {
    private var freqPath: String? = null
    private var loadPath: String? = null
    private var probed = false

    private fun probe() {
        if (probed) return
        probed = true
        freqPath = listOf(
            "/sys/kernel/gpu/gpu_clock",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
        ).firstOrNull { MonitorSysfs.exists(it) } ?: ""
        loadPath = listOf(
            "/sys/kernel/gpu/gpu_busy",
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        ).firstOrNull { MonitorSysfs.exists(it) } ?: ""
    }

    /** GPU 频率（MHz）；不支持时返回 null。原版对 Hz 值截掉末 6 位换算 MHz */
    fun freqMhz(): Long? {
        probe()
        val path = freqPath?.takeIf { it.isNotEmpty() } ?: return null
        val raw = MonitorSysfs.readText(path) ?: return null
        return if (raw.length > 6) {
            raw.substring(0, raw.length - 6).toLongOrNull()
        } else {
            raw.toLongOrNull()
        }
    }

    /** GPU 负载（%）；不支持时返回 -1（原版契约） */
    fun loadPercent(): Int {
        probe()
        val path = loadPath?.takeIf { it.isNotEmpty() } ?: return -1
        val raw = MonitorSysfs.readText(path) ?: return -1
        return raw.replace("%", "").trim().toIntOrNull() ?: -1
    }
}

/** 内存/Swap 信息（/proc/meminfo，单位 kB） */
internal data class MemoryInfo(
    val memTotalKb: Long = 0,
    val memAvailableKb: Long = 0,
    val swapTotalKb: Long = 0,
    val swapFreeKb: Long = 0,
) {
    val memUsedKb: Long get() = (memTotalKb - memAvailableKb).coerceAtLeast(0)
    val swapUsedKb: Long get() = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
    val memUsedPercent: Int get() = if (memTotalKb > 0) (memUsedKb * 100 / memTotalKb).toInt() else 0
    val swapUsedPercent: Int get() = if (swapTotalKb > 0) (swapUsedKb * 100 / swapTotalKb).toInt() else 0
}

internal object MemoryMonitor {
    fun read(): MemoryInfo {
        val values = HashMap<String, Long>()
        try {
            File("/proc/meminfo").forEachLine { line ->
                val key = line.substringBefore(':').trim()
                if (key == "MemTotal" || key == "MemAvailable" || key == "SwapTotal" || key == "SwapFree") {
                    val value = line.substringAfter(':').trim()
                        .removeSuffix("kB").trim().toLongOrNull()
                    if (value != null) values[key] = value
                }
            }
        } catch (ignored: Exception) {
        }
        return MemoryInfo(
            memTotalKb = values["MemTotal"] ?: 0,
            memAvailableKb = values["MemAvailable"] ?: 0,
            swapTotalKb = values["SwapTotal"] ?: 0,
            swapFreeKb = values["SwapFree"] ?: 0,
        )
    }
}

/** 电池信息；读不到的字段为 -1 */
internal data class BatteryInfo(
    val level: Int = -1,
    val temperatureC: Float = -1f,
    val currentMa: Int = -1,
)

internal object BatteryMonitor {
    private val supplyDirs = listOf("battery", "bms", "Battery", "main_battery")

    private fun readSupplyLong(name: String): Long? {
        for (dir in supplyDirs) {
            val v = MonitorSysfs.readLong("/sys/class/power_supply/$dir/$name")
            if (v != null) return v
        }
        return null
    }

    fun read(context: Context? = null): BatteryInfo {
        // 电量：sysfs capacity → BatteryManager 回退
        var level = readSupplyLong("capacity")?.toInt() ?: -1
        if (level < 0 && context != null) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        }

        // 温度：sysfs temp（0.1°C）→ bms uevent POWER_SUPPLY_TEMP
        var temp = readSupplyLong("temp")?.let { it / 10f } ?: -1f
        if (temp < 0) {
            val uevent = MonitorSysfs.readText("/sys/class/power_supply/bms/uevent")
            uevent?.lineSequence()?.forEach { line ->
                if (line.startsWith("POWER_SUPPLY_TEMP=")) {
                    temp = line.substringAfter('=').toFloatOrNull()?.let { it / 10f } ?: -1f
                }
            }
        }

        // 电流：current_now（µA）→ current_avg 回退
        var currentMa = readSupplyLong("current_now")?.let { (it / 1000).toInt() } ?: -1
        if (currentMa == -1) {
            currentMa = readSupplyLong("current_avg")?.let { (it / 1000).toInt() } ?: -1
        }

        return BatteryInfo(level = level, temperatureC = temp, currentMa = currentMa)
    }
}

/** kHz → "2.40 GHz" / "800 MHz" */
internal fun formatFreq(khz: Long?): String {
    if (khz == null || khz < 0) return "--"
    return if (khz >= 1_000_000) {
        String.format("%.2f GHz", khz / 1_000_000f)
    } else {
        "${khz / 1000} MHz"
    }
}
