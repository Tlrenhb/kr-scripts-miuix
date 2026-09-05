// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Simple trend line chart drawn with Canvas in the Miuix primary color.
 */
@Composable
fun TrendChart(
    samples: List<Float>,
    modifier: Modifier = Modifier,
    maxValue: Float = 1f,
) {
    val lineColor = MiuixTheme.colorScheme.primary
    val fillColor = remember(lineColor) { lineColor.copy(alpha = 0.18f) }
    val gridColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)

    Canvas(modifier = modifier.fillMaxWidth().height(96.dp)) {
        if (samples.size < 2) return@Canvas
        val stepX = size.width / (samples.size - 1)
        fun y(v: Float): Float =
            size.height * (1f - (v / maxValue).coerceIn(0f, 1f))

        // grid lines at 25/50/75%
        for (frac in listOf(0.25f, 0.5f, 0.75f)) {
            drawLine(
                color = gridColor,
                start = Offset(0f, size.height * frac),
                end = Offset(size.width, size.height * frac),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val path = Path()
        val fillPath = Path()
        samples.forEachIndexed { i, v ->
            val x = i * stepX
            val yv = y(v)
            if (i == 0) {
                path.moveTo(x, yv)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, yv)
            } else {
                path.lineTo(x, yv)
                fillPath.lineTo(x, yv)
            }
        }
        fillPath.lineTo((samples.size - 1) * stepX, size.height)
        fillPath.close()
        drawPath(fillPath, fillColor)
        drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    }
}
