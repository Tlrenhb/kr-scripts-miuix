// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.min

/**
 * 浮窗监控视图 — 原版 FloatMonitor + fw_monitor.xml 的移植。
 * 四个 35dp 圆环面板（CPU/GPU/RAM/电池）横排 + 频率/温度/电量文字标签，
 * 圆角深色背景 (#55000000, 10dp)，拖动移动，300ms 内双击关闭。
 *
 * 颜色阈值（原版 colors.xml）：
 *   load_low #02d98d, mid #87cb00, high #fc8a1b, veryhigh #f9592f
 *   温度: >=54 红 #FF0F00, >=49 veryhigh, >=44 high, >34 mid, else low
 */
internal class FloatMonitorView(context: Context) : View(context) {

    // ---- 原版颜色阈值（colors.xml） ----
    private val colorLow = Color.rgb(0x02, 0xd9, 0x8d)
    private val colorMid = Color.rgb(0x87, 0xcb, 0x00)
    private val colorHigh = Color.rgb(0xfc, 0x8a, 0x1b)
    private val colorVeryHigh = Color.rgb(0xf9, 0x59, 0x2f)

    // ---- 圆环绘制状态（每面板） ----
    private var cpuRatio = 0
    private var gpuRatio = 0
    private var ramRatio = 0
    private var batteryRatio = 0

    // ---- 文本标签 ----

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x55000000.toInt()
    }
    private val ringBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 8f; color = 0x22888888.toInt()
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 20f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val rect = RectF()

    // ---- 采样数据 ----
    var cpuLoadP = 0
    var gpuLoadP = 0
    var ramLoadP = 0
    var batteryP = 0
    var cpuMhz = 0
    var gpuMhz = 0
    var ramGb = 0
    var batteryLevel = -1
    var batteryTempC = -1f

    var onClose: (() -> Unit)? = null

    // ---- 拖动 / 双击关闭 ----
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartRawX = 0f
    private var touchStartRawY = 0f
    private var touchStartTime = 0L
    private var lastClickTime = 0L
    private var windowParams: WindowManager.LayoutParams? = null

    fun setWindowParams(lp: WindowManager.LayoutParams) {
        windowParams = lp
    }

    fun updateDrag(rawX: Float, rawY: Float) {
        val lp = windowParams ?: return
        lp.x = (rawX - touchStartX).toInt()
        lp.y = (rawY - touchStartY).toInt()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.updateViewLayout(this, lp)
    }

    private fun onClick() {
        if (System.currentTimeMillis() - lastClickTime < 300) {
            onClose?.invoke()
        } else {
            lastClickTime = System.currentTimeMillis()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                touchStartRawX = event.rawX
                touchStartRawY = event.rawY
                touchStartTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - touchStartRawX) > 5 ||
                    abs(event.rawY - touchStartRawY) > 5
                ) {
                    updateDrag(event.rawX, event.rawY)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (System.currentTimeMillis() - touchStartTime < 300 &&
                    abs(event.rawX - touchStartRawX) < 15 &&
                    abs(event.rawY - touchStartRawY) < 15
                ) {
                    onClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {}
        }
        return true
    }

    // ---- 颜色阈值（原版 updateViewByShell / 电池温度分档） ----
    private fun loadColor(ratio: Int): Int = when {
        ratio > 90 -> colorVeryHigh
        ratio > 75 -> colorHigh
        ratio > 20 -> colorMid
        else -> colorLow
    }

    private fun tempColor(temp: Float): Int = when {
        temp >= 54 -> Color.rgb(255, 15, 0)
        temp >= 49 -> colorVeryHigh
        temp >= 44 -> colorHigh
        temp > 34 -> colorMid
        else -> colorLow
    }

    override fun onDraw(canvas: Canvas) {
        // 圆角深色背景 (#55000000, 10dp radius)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 28f, 28f, bgPaint)

        val panelSize = min(width / 4f, height * 0.7f).coerceAtMost(100f)
        val innerR = panelSize / 2f - 16f
        val totalW = width.toFloat()
        val gap = (totalW - 4 * panelSize) / 5f
        val cy = height * 0.42f

        val labels = arrayOf("CPU", "GPU", "RAM", "BAT")
        val ratios = intArrayOf(cpuLoadP, gpuLoadP, ramLoadP, batteryP)
        val freqs = arrayOf("${cpuMhz}MHz", "${gpuMhz}MHz", "${ramP()}G", "${batteryLevel}%")
        val tempTexts = arrayOf("", "", "", "${"%.0f".format(batteryTempC)}°C")

        for (i in 0 until 4) {
            val cx = gap + i * (panelSize + gap) + panelSize / 2f
            // 背景圆环
            ringBgPaint.color = 0x22888888.toInt()
            rect.set(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
            canvas.drawArc(rect, 0f, 360f, false, ringBgPaint)

            // 数据弧
            val ratio = ratios[i].coerceIn(0, 100)
            if (ratio > 0) {
                ringPaint.color = loadColor(ratio)
                canvas.drawArc(rect, -90f, ratio * 3.6f, false, ringPaint)
            }

            // 面板标签（圆环中央）
            canvas.drawText(labels[i], cx, cy + 6f, labelPaint)

            // 下方数值
            val valueY = cy + innerR + 28f
            canvas.drawText(freqs[i], cx, valueY, labelPaint)

            // 电池温度只画在最右列
            if (i == 3 && batteryTempC > 0) {
                val tempPaint = Paint(labelPaint).apply { color = tempColor(batteryTempC) }
                canvas.drawText(tempTexts[i], cx, valueY + 20f, tempPaint)
            }
        }
    }

    private fun ramP(): Int = ramLoadP

    /** 更新 UI 文本 + 重绘（由 sampler 在 UI 线程调用）。 */
    fun refresh(
        cpuP: Int, gpuP: Int, ramP: Int, batP: Int,
        cpuMhz: Int, gpuMhz: Int, ramGb: Int,
        tempC: Float,
    ) {
        this.cpuLoadP = cpuP
        this.gpuLoadP = gpuP
        this.ramLoadP = ramP
        this.batteryP = batP
        this.cpuMhz = cpuMhz
        this.gpuMhz = gpuMhz
        this.ramGb = ramGb
        this.batteryLevel = batP
        this.batteryTempC = tempC
        invalidate()
    }

    /** 由 Service 采样后设置，用于 Canvas 里的文字显示。 */
    fun setReadouts(cpuMhz: Int, gpuMhz: Int, ramGb: Int, batP: Int, tempC: Float) {
        this.cpuMhz = cpuMhz
        this.gpuMhz = gpuMhz
        this.ramGb = ramGb
        this.batteryLevel = batP
        this.batteryTempC = tempC
    }
}
