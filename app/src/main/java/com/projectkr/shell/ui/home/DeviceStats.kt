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

    fun coreCount(): Int = Runtime.getRuntime().availableProcessors()

    fun cpuFrequencies(): List<Int> {
        val freqs = ArrayList<Int>()
        for (core in 0 until coreCount()) {
            val path =
                "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq"
            val value = readLong(path)
            freqs.add((value / 1000).toInt()) // → MHz-ish kHz→kHz keep kHz
        }
        return freqs
    }

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
