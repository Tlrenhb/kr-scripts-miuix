// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import java.io.File
import kotlin.math.max

/**
 * 浮窗监控 — 原版 FloatMonitor 的移植：1s Timer 采样、拖动移动、双击关闭。
 * 四个圆环面板：CPU（负载+频率）、GPU（负载+频率）、RAM、电池（电量+温度）。
 */
class FloatMonitorService : Service() {

    private var windowManager: WindowManager? = null
    private var floatView: FloatMonitorView? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    private val sampler = object : Runnable {
        override fun run() {
            val load = readCpuLoad()
            val freq = readMaxFreqKHz()
            val gpu = readGpu()
            val mem = readMemory()
            val batLvl = readBatteryLevel()
            val batTemp = readBatteryTempC()

            floatView?.post {
                floatView?.refresh(
                    cpuP = if (load >= 0) (load * 100).toInt() else 0,
                    gpuP = if (gpu.second >= 0) gpu.second else 0,
                    ramP = if (mem.second > 0) (mem.first * 100 / mem.second).toInt() else 0,
                    batP = if (batLvl >= 0) batLvl else 0,
                    cpuMhz = (freq / 1000).toInt(),
                    gpuMhz = (gpu.first / 1000).toInt(),
                    ramGb = (mem.second / 1024 / 1024 / 1024).toInt() + 1,
                    tempC = batTemp,
                )
                floatView?.invalidate()
            }
            handler?.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        FloatMonitor.running = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val view = FloatMonitorView(this).apply {
            onClose = { stopSelf() }
        }
        floatView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        view.setWindowParams(params)
        windowManager?.addView(view, params)

        handlerThread = HandlerThread("float-monitor").apply { start() }
        handler = Handler(handlerThread!!.looper)
        handler?.post(sampler)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler?.removeCallbacks(sampler)
        handlerThread?.quitSafely()
        floatView?.let {
            runCatching { windowManager?.removeView(it) }
        }
        floatView = null
        FloatMonitor.running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val INTERVAL_MS = 1000L

        internal fun readMaxFreqKHz(): Long {
            var max = 0L
            for (core in 0 until Runtime.getRuntime().availableProcessors()) {
                val v = runCatching {
                    File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_cur_freq")
                        .readText().trim().toLong()
                }.getOrDefault(0L)
                if (v > max) max = v
            }
            return max
        }

        internal fun readGpu(): Pair<Long, Int> {
            val clk = runCatching {
                File("/sys/class/kgsl/kgsl-3d0/gpuclk").readText().trim().toLong()
            }.getOrDefault(0L)
            var load = -1
            runCatching {
                val txt = File("/sys/class/kgsl/kgsl-3d0/gpubusy").readText().trim()
                val parts = txt.split(Regex("\\s+"))
                if (parts.size == 2 && parts[1].toInt() > 0) {
                    load = (parts[0].toFloat() / parts[1].toFloat() * 100).toInt()
                }
            }
            return clk to load
        }

        internal fun readMemory(): Pair<Long, Long> {
            var total = 0L
            var available = 0L
            runCatching {
                File("/proc/meminfo").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        when {
                            line.startsWith("MemTotal:") -> total =
                                line.split(Regex("\\s+"))[1].toLong() * 1024
                            line.startsWith("MemAvailable:") -> available =
                                line.split(Regex("\\s+"))[1].toLong() * 1024
                        }
                    }
                }
            }
            return (total - available) to max(total, 1L)
        }

        internal fun readBatteryLevel(): Int = runCatching {
            File("/sys/class/power_supply/battery/capacity").readText().trim().toInt()
        }.getOrDefault(-1)

        internal fun readBatteryTempC(): Float = runCatching {
            File("/sys/class/power_supply/battery/temp").readText().trim().toFloat() / 10f
        }.getOrDefault(Float.NaN)
    }
}
