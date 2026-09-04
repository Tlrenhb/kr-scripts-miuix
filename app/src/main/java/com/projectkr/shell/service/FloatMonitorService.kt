// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.max

/**
 * Floating CPU/RAM monitor (original FloatMonitor): a draggable classic-View
 * overlay sampling /proc every interval. Long-press toggles the close button.
 */
class FloatMonitorService : Service() {

    private var windowManager: WindowManager? = null
    private var floatView: MonitorFloatView? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private val cpuSamples = ArrayDeque<Float>()
    private val memSamples = ArrayDeque<Float>()

    private val sampler = object : Runnable {
        override fun run() {
            val usage = readCpuUsage()
            if (usage >= 0f) {
                push(cpuSamples, usage)
            }
            val (used, total) = readMemory()
            push(memSamples, if (total > 0) used.toFloat() / total else 0f)
            floatView?.post {
                floatView?.invalidate()
            }
            handler?.postDelayed(this, INTERVAL_MS)
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreate() {
        super.onCreate()
        FloatMonitor.running = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatView = MonitorFloatView(this).apply {
            onClose = { stopSelf() }
            serviceSeriesCpu = { cpuSamples.toList() }
            serviceSeriesMem = { memSamples.toList() }
            readout = {
                val freq = readCpuUsage().let { "" } // freq read below
                val maxFreqMhz = readMaxFreqMhz()
                val gpuMhz = readLong("/sys/class/kgsl/kgsl-3d0/gpuclk") / 1000
                val lvl = readBatteryLevel()
                val tmp = readBatteryTempC()
                buildString {
                    append("CPU")
                    if (maxFreqMhz > 0) append(" ${maxFreqMhz}MHz")
                    if (gpuMhz > 0) append(" · GPU ${gpuMhz}MHz")
                    if (lvl >= 0) append(" · 电池 $lvl%")
                    if (!tmp.isNaN()) append(" · ${"%.1f".format(tmp)}℃")
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
        }
        windowManager?.addView(floatView, params)

        handlerThread = HandlerThread("float-monitor").apply { start() }
        handler = Handler(handlerThread!!.looper)
        handler?.post(sampler)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        FloatMonitor.running = false
        handler?.removeCallbacks(sampler)
        handlerThread?.quitSafely()
        floatView?.let {
            runCatching { windowManager?.removeView(it) }
        }
        floatView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val INTERVAL_MS = 1000L
        private const val MAX_SAMPLES = 40

        private fun push(queue: ArrayDeque<Float>, value: Float) {
            queue.addLast(value)
            while (queue.size > MAX_SAMPLES) queue.removeFirst()
        }

        internal fun readCpuUsage(): Float = try {
            val stat = java.io.File("/proc/stat").bufferedReader().readLine()
                .split(Regex("\\s+")).drop(1).take(5).map { it.toLong() }
            // One-shot approximation: idle vs total since boot.
            val idle = stat[3] + (stat.getOrElse(4) { 0L })
            val total = stat.sum()
            if (total > 0) (1f - idle.toFloat() / total).coerceIn(0f, 1f) else -1f
        } catch (ex: Exception) {
            -1f
        }

        internal fun readMaxFreqMhz(): Int {
            var max = 0L
            for (core in 0 until Runtime.getRuntime().availableProcessors()) {
                val v = runCatching {
                    java.io.File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_cur_freq")
                        .readText().trim().toLong()
                }.getOrDefault(0L)
                if (v > max) max = v
            }
            return (max / 1000).toInt()
        }

        internal fun readBatteryLevel(): Int = runCatching {
            java.io.File("/sys/class/power_supply/battery/capacity").readText().trim().toInt()
        }.getOrDefault(-1)

        internal fun readBatteryTempC(): Float = runCatching {
            java.io.File("/sys/class/power_supply/battery/temp").readText().trim().toFloat() / 10f
        }.getOrDefault(Float.NaN)

        internal fun readLong(path: String): Long = runCatching {
            java.io.File(path).readText().trim().toLong()
        }.getOrDefault(0L)

        internal fun readMemory(): Pair<Long, Long> {
            var total = 0L
            var available = 0L
            runCatching {
                java.io.File("/proc/meminfo").bufferedReader().useLines { lines ->
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
    }
}

/**
 * Compact draggable chart bubble — a plain View so it works inside the
 * WindowManager overlay without Compose lifecycle plumbing.
 */
internal class MonitorFloatView(context: android.content.Context) :
    View(context) {

    var onClose: (() -> Unit)? = null

    /** Textual readouts updated by the service sampler. */
    var readout: (() -> String) = { "" }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 20, 20, 24)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x34, 0x82, 0xFF)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val memPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x36, 0xD1, 0x67)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
    }
    private val closePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xE5, 0x39, 0x35)
    }
    private val path = Path()

    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var showClose = false

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, 28f, 28f, bgPaint)

        val chartTop = 56f
        val chartBottom = h - 44f
        drawSeries(canvas, serviceSeriesCpu, chartTop, chartBottom, w, linePaint, "CPU")
        drawSeries(canvas, serviceSeriesMem, chartTop, chartBottom, w, memPaint, "RAM")

        canvas.drawText(readout(), 20f, 38f, textPaint)

        if (showClose) {
            val r = 30f
            val cx = w - r - 16f
            val cy = 32f
            canvas.drawCircle(cx, cy, r, closePaint)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("×", cx, cy + 10f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawSeries(
        canvas: Canvas,
        series: () -> List<Float>,
        top: Float,
        bottom: Float,
        width: Float,
        paint: Paint,
        label: String,
    ) {
        val samples = series()
        path.reset()
        samples.forEachIndexed { i, v ->
            val x = width * i / max(samples.size - 1, 1)
            val y = bottom - (bottom - top) * v.coerceIn(0f, 1f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (samples.isNotEmpty()) canvas.drawPath(path, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchRawX = event.rawX
                lastTouchRawY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastTouchRawX
                val dy = event.rawY - lastTouchRawY
                lastTouchRawX = event.rawX
                lastTouchRawY = event.rawY
                moveBy(dx.toInt(), dy.toInt())
                return true
            }
            MotionEvent.ACTION_UP -> {
                // Tap on the close circle stops the service.
                if (showClose && event.x > width - 76f && event.y < 64f) {
                    onClose?.invoke()
                    return true
                }
                showClose = !showClose
                invalidate()
                return true
            }
        }
        return super.performClick()
    }

    private fun moveBy(dx: Int, dy: Int) {
        val lp = layoutParams as? WindowManager.LayoutParams ?: return
        lp.x += dx
        lp.y += dy
        windowManagerCompat()?.updateViewLayout(this, lp)
    }

    private fun windowManagerCompat(): WindowManager? =
        context.getSystemService(Service.WINDOW_SERVICE) as? WindowManager

    /** Series accessors wired by the service after construction. */
    var serviceSeriesCpu: () -> List<Float> = { emptyList() }
    var serviceSeriesMem: () -> List<Float> = { emptyList() }
}
