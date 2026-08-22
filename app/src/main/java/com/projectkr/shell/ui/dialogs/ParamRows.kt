// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.projectkr.krscript.core.config.PageConfigReader
import com.projectkr.krscript.core.model.ActionParamInfo
import com.projectkr.shell.ui.pages.FilePickerResult
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/** Hex color parameter: swatch preview + picker dialog. */
@Composable
fun ColorParamRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value.ifEmpty { "#" })
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(24.dp)
                    .background(parseColorSafe(value)),
            )
        }
    }
    TextButton(text = "选择颜色", onClick = { picking = true })

    if (picking) {
        OverlayDialog(
            title = title,
            show = true,
            onDismissRequest = { picking = false },
        ) {
            ColorPickerContent(initialHex = value, onPicked = {
                onValueChange(it)
                picking = false
            })
        }
    }
}

/** File path parameter: editable text (when allowed) + picker entry button. */
@Composable
fun FileParamRow(
    param: ActionParamInfo,
    value: String,
    onPickClick: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    val title = param.title ?: param.name.orEmpty()

    // Observe a path picked by the file selector route.
    LaunchedEffect(FilePickerResult.pendingPath) {
        FilePickerResult.pendingPath?.let { picked ->
            onValueChange(picked)
            FilePickerResult.consume()
        }
    }

    Column {
        TextField(
            label = "$title${if (param.suffix.isNotEmpty()) " (.${param.suffix})" else ""}",
            value = value,
            onValueChange = onValueChange,
            enabled = param.editable,
        )
        TextButton(text = "选择文件", onClick = onPickClick)
    }
}

@Composable
private fun ColorPickerContent(initialHex: String, onPicked: (String) -> Unit) {
    var color by remember { mutableStateOf(parseColorSafe(initialHex)) }
    Column {
        ColorPicker(
            color = color,
            onColorChanged = { color = it },
        )
        TextButton(
            text = "确定",
            onClick = {
                val hex = String.format("#%08X", color.value.toInt())
                onPicked(hex)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

private fun parseColorSafe(hex: String): Color =
    try {
        Color(PageConfigReader.parseColor(hex.ifEmpty { "#FFFFFFFF" }))
    } catch (_: Exception) {
        Color.Transparent
    }
