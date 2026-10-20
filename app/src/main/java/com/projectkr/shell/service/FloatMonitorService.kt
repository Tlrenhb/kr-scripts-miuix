// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Choreographer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ServiceCompat
import com.projectkr.shell.R
import com.projectkr.shell.ui.monitor.BatteryMonitor
import com.projectkr.shell.ui.monitor.CpuLoadSampler
import com.projectkr.shell.ui.monitor.CpuMonitor
import com.projectkr.shell.ui.monitor.MemoryMonitor
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * 悬浮窗性能监控前台服务（FRAMEWORK_PARITY §5.8，对齐原版 FloatMonitor）。
 *
 * - WindowManager 添加可拖动悬浮 View：帧率 / CPU（负载 + 最高频率）/ 内存 / 电池
 *   （电量 + 温度，温度按原版阈值变色），1 秒刷新。
 * - 拖动更新 layoutParams；双击（两次点击 <300ms、位移 <15px、按下 <300ms）关闭。
 * - 点击穿透：start extra [EXTRA_TOUCH_THROUGH]=true 时加 FLAG_NOT_TOUCHABLE
 *   （原版不可穿透、可拖动；穿透为可选模式）。
 * - 前台服务：API 34+ 使用 specialUse 类型（manifest 已声明
 *   foregroundServiceType + PROPERTY_SPECIAL_USE_FGS_SUBTYPE）；通知 channel 低重要性。
 */
class FloatMonitorService : Service() {

    companion object {
        /** 悬浮窗是否正在显示（供 HomeScreen 开关状态同步） */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** start extra：true = 悬浮窗点击穿透（不可拖动/双击关闭，需停止服务关闭） */
        const val EXTRA_TOUCH_THROUGH = "touchThrough"

        /** onStartCommand action：停止服务并移除悬浮窗 */
        const val ACTION_STOP = "com.projectkr.shell.action.STOP_FLOAT_MONITOR"

        private const val NOTIFICATION_ID = 4100
        private const val CHANNEL_ID = "float_monitor"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val samplerExecutor = Executors.newSingleThreadExecutor()
    private val cpuLoadSampler = CpuLoadSampler()

    private var windowManager: WindowManager? = null
    private var monitorView: MonitorView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var touchThrough = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            sampleAsync()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            },
        )
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授权显示悬浮窗权限！", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        showOverlay()
        isRunning = true
        handler.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val newTouchThrough = intent?.getBooleanExtra(EXTRA_TOUCH_THROUGH, false) == true
        if (newTouchThrough != touchThrough) {
            touchThrough = newTouchThrough
            windowParams?.let { params ->
                params.flags = baseWindowFlags(touchThrough)
                monitorView?.let { view ->
                    runCatching { windowManager?.updateViewLayout(view, params) }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(tickRunnable)
        samplerExecutor.shutdownNow()
        monitorView?.let { view ->
            view.stopFpsCounter()
            runCatching { windowManager?.removeView(view) }
        }
        monitorView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- 悬浮窗 ----

    private fun baseWindowFlags(touchThrough: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (touchThrough) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val view = MonitorView(this)
        monitorView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            baseWindowFlags(touchThrough),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
        }
        windowParams = params
        wm.addView(view, params)

        // 原版交互：拖动移动；双击关闭
        view.setOnTouchListener(object : View.OnTouchListener {
            private var touchStartX = 0f
            private var touchStartY = 0f
            private var touchStartRawX = 0f
            private var touchStartRawY = 0f
            private var touchStartTime = 0L
            private var lastClickTime = 0L
            private var isTouchDown = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.x
                        touchStartY = event.y
                        touchStartRawX = event.rawX
                        touchStartRawY = event.rawY
                        touchStartTime = System.currentTimeMillis()
                        isTouchDown = true
                    }
                    MotionEvent.ACTION_MOVE -> if (isTouchDown) {
                        params.x = (event.rawX - touchStartX).toInt()
                        params.y = (event.rawY - touchStartY).toInt()
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                    MotionEvent.ACTION_UP -> {
                        val now = System.currentTimeMillis()
                        val isTap = now - touchStartTime < 300 &&
                            abs(event.rawX - touchStartRawX) < 15 &&
                            abs(event.rawY - touchStartRawY) < 15
                        if (isTap) {
                            if (now - lastClickTime < 300) {
                                stopSelf() // 双击关闭
                            } else {
                                lastClickTime = now
                            }
                        }
                        isTouchDown = false
                    }
                    MotionEvent.ACTION_OUTSIDE, MotionEvent.ACTION_CANCEL -> isTouchDown = false
                }
                return true
            }
        })
    }

    // ---- 采样（IO 线程采样，主线程刷新 View） ----

    private fun sampleAsync() {
        samplerExecutor.execute {
            val loads = cpuLoadSampler.sample()
            val cpuLoad = (loads[-1] ?: 0.0).coerceAtLeast(0.0)
            val cpuFreqKhz = CpuMonitor.maxCurrentFreqKhz()
            val memory = MemoryMonitor.read()
            val battery = BatteryMonitor.read(applicationContext)
            handler.post {
                monitorView?.update(
                    cpuLoad = cpuLoad.toFloat(),
                    cpuFreqMhz = cpuFreqKhz?.let { it / 1000 } ?: -1L,
                    ramUsedPercent = memory.memUsedPercent,
                    batteryLevel = battery.level,
                    batteryTempC = battery.temperatureC,
                )
            }
        }
    }

    // ---- 前台通知 ----

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮监控",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, FloatMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText("悬浮监控运行中（双击悬浮窗关闭）")
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    addAction(
                        Notification.Action.Builder(null, "停止", stopIntent).build(),
                    )
                }
            }
            .build()
    }
}

/**
 * 悬浮窗内容 View：纯 Canvas 绘制（圆角半透明底 + 文本行）。
 * 帧率通过 Choreographer 统计（本应用自身帧率，尽力而为）。
 */
private class MonitorView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3000000.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12 * density
    }
    private val rect = RectF()

    private var fps = -1
    private var cpuLoad = 0f
    private var cpuFreqMhz = -1L
    private var ramUsedPercent = 0
    private var batteryLevel = -1
    private var batteryTempC = -1f

    private var frameCount = 0
    private var fpsWindowStart = System.currentTimeMillis()
    private var fpsActive = true

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameCount++
            val now = System.currentTimeMillis()
            if (now - fpsWindowStart >= 1000) {
                fps = (frameCount * 1000L / (now - fpsWindowStart)).toInt()
                frameCount = 0
                fpsWindowStart = now
            }
            if (fpsActive) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    init {
        val padH = 12 * density
        val padV = 8 * density
        setPadding(padH.toInt(), padV.toInt(), padH.toInt(), padV.toInt())
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stopFpsCounter() {
        fpsActive = false
    }

    fun update(
        cpuLoad: Float,
        cpuFreqMhz: Long,
        ramUsedPercent: Int,
        batteryLevel: Int,
        batteryTempC: Float,
    ) {
        this.cpuLoad = cpuLoad
        this.cpuFreqMhz = cpuFreqMhz
        this.ramUsedPercent = ramUsedPercent
        this.batteryLevel = batteryLevel
        this.batteryTempC = batteryTempC
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (150 * density).toInt() + paddingLeft + paddingRight
        val lineH = (18 * density).toInt()
        val h = lineH * 4 + paddingTop + paddingBottom
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 12 * density, 12 * density, bgPaint)

        val lineH = 18 * density
        var y = paddingTop + textPaint.textSize
        val x = paddingLeft.toFloat()

        drawRow(canvas, x, y, "FPS", if (fps >= 0) "$fps" else "--", Color.WHITE)
        y += lineH
        drawRow(
            canvas, x, y, "CPU",
            String.format(Locale.getDefault(), "%.0f%% %s", cpuLoad, freqText()),
            Color.WHITE,
        )
        y += lineH
        drawRow(canvas, x, y, "RAM", "$ramUsedPercent%", Color.WHITE)
        y += lineH
        val batteryText = buildString {
            if (batteryLevel >= 0) append("$batteryLevel%")
            if (batteryTempC >= 0) {
                if (isNotEmpty()) append(" ")
                append(String.format(Locale.getDefault(), "%.1f°C", batteryTempC))
            }
            if (isEmpty()) append("--")
        }
        drawRow(canvas, x, y, "BAT", batteryText, batteryTempColor(batteryTempC))
    }

    private fun freqText(): String = if (cpuFreqMhz > 0) "${cpuFreqMhz}MHz" else "--"

    private fun drawRow(canvas: Canvas, x: Float, y: Float, label: String, value: String, valueColor: Int) {
        textPaint.color = 0xFF9E9E9E.toInt()
        canvas.drawText(label, x, y, textPaint)
        textPaint.color = valueColor
        canvas.drawText(value, x + 40 * density, y, textPaint)
    }

    /** 原版温度配色阈值（pio colors.xml） */
    private fun batteryTempColor(tempC: Float): Int = when {
        tempC >= 54 -> Color.rgb(255, 15, 0)
        tempC >= 49 -> Color.rgb(0xf9, 0x59, 0x2f)
        tempC >= 44 -> Color.rgb(0xfc, 0x8a, 0x1b)
        tempC > 34 -> Color.rgb(0x87, 0xcb, 0x00)
        else -> Color.rgb(0x02, 0xd9, 0x8d)
    }
}
