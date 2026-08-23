// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.home

import java.io.File

/**
 * Device statistics collected from /proc and /sys — same sources as the
 * original CpuFrequencyUtils / CpuLoadUtils / BatteryUnit.
 */
object DeviceStats {

    data class Snapshot(
        val cpuFreqKHz: List<Int>,
        val cpuUsage: Float,          // 0..1
        val memUsedBytes: Long,
        val memTotalBytes: Long,
        val batteryLevel: Int,        // 0..100, -1 unknown
        val batteryTemp: Float,       // ℃, Float.NaN unknown
    )

    data class CoreStat(val curKHz: Int, val minKHz: Int, val maxKHz: Int, val load: Float)

    private var prevCoreBusy: LongArray? = null

    /** Per-core current frequency (kHz). */
    fun cpuFrequencies(): List<Int> =
        (0 until coreCount()).map { core -> readLong("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_cur_freq") }

    fun cpuMinMax(): Pair<List<Int>, List<Int>> {
        val mins = (0 until coreCount()).map { c -> readLong("/sys/devices/system/cpu/cpu$c/cpufreq/scaling_min_freq") }
        val maxs = (0 until coreCount()).map { c -> readLong("/sys/devices/system/cpu/cpu$c/cpufreq/scaling_max_freq") }
        return mins to maxs
    }

    /**
     * Per-core load sampled from the per-cpu lines of /proc/stat
     * (original CpuLoadUtils semantics): returns load 0..1 per core,
     * or an empty array on the first call.
     */
    fun perCoreLoad(): List<Float> {
        val lines = runCatching {
            File("/proc/stat").bufferedReader().readLines()
                .filter { it.startsWith("cpu") && it[3].isDigit() }
        }.getOrDefault(emptyList())
        if (lines.isEmpty()) return emptyList()
        val samples = lines.map { l ->
            val v = l.split(Regex("\\s+")).drop(1).take(4).map { it.toLong() }
            longArrayOf(v[0], v[1], v[2], v[3]) // user nice system idle (+iowait folded below)
        }
        val last = prevPerCoreBusy
        prevPerCoreBusy = samples.map { s -> longArrayOf(s[0] + s[1] + s[2] + s[3], s[3]) }.toTypedArray()
            .let { arr -> arr.flatMap { listOf(it[0], it[1]) } }.toLongArray()
        if (last == null || last.size != samples.size * 2) return emptyList()
        return samples.indices.map { i ->
            val busy = samples[i][0] + samples[i][1] + samples[i][2]
            val total = busy + samples[i][3]
            val lBusy = last[i * 2]
            val lTotal = last[i * 2 + 1]
            val dt = total - lTotal
            if (dt > 0) ((busy - lBusy).toFloat() / dt).coerceIn(0f, 1f) else -1f
        }
    }

    private var prevPerCoreBusy: LongArray? = null

    /** Swap total/free bytes from /proc/meminfo (original home showed zram/swap info). */
    fun swapInfo(): Pair<Long, Long> {
        var total = 0L
        var free = 0L
        runCatching {
            File("/proc/meminfo").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.startsWith("SwapTotal:") -> total = line.split(Regex("\\s+"))[1].toLong() * 1024
                        line.startsWith("SwapFree:") -> free = line.split(Regex("\\s+"))[1].toLong() * 1024
                    }
                }
            }
        }
        return free to total
    }

    /** GPU frequency (kHz) and busy percent from the standard kgsl sysfs. */
    fun gpuInfo(): Pair<Long, Int> {
        val clk = readLong("/sys/class/kgsl/kgsl-3d0/gpuclk")
        var busy = -1
        runCatching {
            val txt = File("/sys/class/kgsl/kgsl-3d0/gpubusy").readText().trim()
            val parts = txt.split(Regex("\\s+"))
            if (parts.size == 2 && parts[1].toInt() > 0) {
                busy = (parts[0].toFloat() / parts[1].toFloat() * 100).toInt()
            }
        }
        return clk to busy
    }

    fun coreCount(): Int = Runtime.getRuntime().availableProcessors()

    private var lastCpuSample: LongArray? = null

    /** Samples /proc/stat twice around a delay; call from IO on a timer loop. */
    fun cpuUsage(): Float {
        val sample = readCpuSample() ?: return -1f
        val last = lastCpuSample
        lastCpuSample = sample
        if (last == null) return -1f
        val idleDelta = (sample[3] + sample[4]) - (last[3] + last[4])
        val totalDelta = sample.sum() - last.sum()
        if (totalDelta <= 0) return -1f
        return (1f - idleDelta.toFloat() / totalDelta).coerceIn(0f, 1f)
    }

    private fun readCpuSample(): LongArray? = runCatching {
        File("/proc/stat").bufferedReader().use { reader ->
            reader.readLine()?.split(Regex("\\s+"))?.drop(1)?.take(5)
                ?.map { it.toLong() }?.toLongArray()
        }
    }.getOrNull()

    fun memoryInfo(): Pair<Long, Long> {
        var total = 0L
        var available = 0L
        runCatching {
            File("/proc/meminfo").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.startsWith("MemTotal:") -> total = line.split(Regex("\\s+"))[1].toLong() * 1024
                        line.startsWith("MemAvailable:") -> available = line.split(Regex("\\s+"))[1].toLong() * 1024
                    }
                }
            }
        }
        return (total - available) to total
    }

    fun storageInfo(): Pair<Long, Long> {
        val dataDir = File("/data")
        return (dataDir.totalSpace - dataDir.usableSpace) to dataDir.totalSpace
    }

    fun batteryLevel(): Int {
        readSysBattery("capacity")?.toIntOrNull()?.let { return it }
        return -1
    }

    /** Battery temperature in ℃ from sysfs (value reported in tenths). */
    fun batteryTemp(): Float {
        readSysBattery("temp")?.toFloatOrNull()?.let { return it / 10f }
        return Float.NaN
    }

    private fun readSysBattery(name: String): String? {
        for (dir in listOf("/sys/class/power_supply/battery", "/sys/class/power_supply/bms")) {
            val f = File("$dir/$name")
            if (f.exists()) {
                val text = runCatching { f.readText().trim() }.getOrNull()
                if (!text.isNullOrEmpty()) return text
            }
        }
        return null
    }

    private fun readLong(path: String): Long = runCatching {
        File(path).readText().trim().toLong()
    }.getOrDefault(0L)
}
