// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.dialogs

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectkr.krscript.core.config.PageConfigReader
import com.projectkr.krscript.core.model.ActionNode
import com.projectkr.krscript.core.model.ActionParamInfo
import com.projectkr.krscript.core.model.SelectItem
import com.projectkr.shell.ui.pages.parseOptionLines
import com.projectkr.shell.runtime.ScriptActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Action params form rendered in an OverlayDialog (page-scoped host).
 * Values are collected into a map and exported as env vars when the action runs.
 */
@Composable
fun ActionParamsDialog(
    node: ActionNode,
    show: Boolean,
    onSubmit: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    return ActionParamsDialogContent(node, show, onSubmit, onDismiss)
}

@Composable
private fun ActionParamsDialogContent(
    node: ActionNode,
    show: Boolean,
    onSubmit: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var draft by remember(node.index) { mutableStateOf<Map<String, String>>(emptyMap()) }

    // SAF picker: overlays the dialog without navigating away, so the form
    // draft survives. The picked document is copied into cache and the real
    // path is what scripts receive.
    var pendingFileParam by remember { mutableStateOf<String?>(null) }
    // Color picker runs as a sibling OverlayDialog (dialogs must not nest).
    var colorPickParam by remember(node.index) { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val name = pendingFileParam
        pendingFileParam = null
        if (uri != null && name != null) {
            val path = copyUriToCache(context, uri, name)
            if (path != null) {
                draft = draft + (name to path)
            }
        }
    }
    var selectOptions by remember(node.index) {
        mutableStateOf<Map<String, List<SelectItem>>>(emptyMap())
    }
    var ready by remember(node.index) { mutableStateOf(false) }

    // Resolve value-sh / options-sh on the IO dispatcher before showing the form.
    LaunchedEffect(node.index, show) {
        if (!show || ready) return@LaunchedEffect
        draft = withContext(Dispatchers.IO) {
            val map = LinkedHashMap<String, String>()
            node.params?.forEach { param ->
                map[param.name.orEmpty()] = param.valueShell?.takeIf { it.isNotEmpty() }?.let {
                    ScriptActions.eval(it, node)
                } ?: param.value.orEmpty()
            }
            map
        }
        selectOptions = withContext(Dispatchers.IO) {
            val map = HashMap<String, List<SelectItem>>()
            node.params?.forEach { param ->
                if (param.optionsSh.isNotEmpty()) {
                    map[param.name.orEmpty()] = parseOptionLines(ScriptActions.eval(param.optionsSh, node))
                }
            }
            map
        }
        ready = true
    }

    OverlayDialog(
        title = node.title,
        summary = node.desc?.ifEmpty { null },
        show = show && ready,
        onDismissRequest = onDismiss,
    ) {
        if (node.warning?.isNotEmpty() == true) {
            Text(
                text = node.warning,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }

        node.params?.forEach { param ->
            ParamField(
                param = param,
                value = draft[param.name.orEmpty()].orEmpty(),
                options = selectOptions[param.name.orEmpty()]?.takeIf { it.isNotEmpty() }
                    ?: param.options ?: emptyList(),
                onPickColor = { colorPickParam = param.name.orEmpty() },
                onPickClick = {
                    pendingFileParam = param.name.orEmpty()
                    filePicker.launch(arrayOf("*/*"))
                },
                onValueChange = { newValue ->
                    draft = draft + ((param.name.orEmpty()) to newValue)
                },
            )
        }

        var errorHint by remember { mutableStateOf<String?>(null) }
        errorHint?.let { msg ->
            Text(
                text = msg,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        TextButton(
            text = "确定",
            onClick = {
                // Original readParamsValue: required params block submission.
                val badColor = node.params
                    ?.filter { it.type == "color" }
                    ?.firstOrNull { param ->
                        val v = draft[param.name.orEmpty()].orEmpty()
                        v.isNotEmpty() && runCatching {
                            PageConfigReader.parseColor(v)
                        }.isFailure
                    }
                val error = validateDraft(node, draft)
                if (error == null) {
                    onSubmit(draft)
                    onDismiss()
                } else {
                    errorHint = error
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }

    // Sibling popup: opening the picker keeps the params form composed below.
    ColorPickerDialog(
        show = colorPickParam != null,
        initialHex = draft[colorPickParam].orEmpty(),
        onPicked = { hex ->
            colorPickParam?.let { name -> draft = draft + (name to hex) }
            colorPickParam = null
        },
        onDismiss = { colorPickParam = null },
    )
}

@Composable
private fun ParamField(
    param: ActionParamInfo,
    value: String,
    options: List<SelectItem>,
    onPickColor: () -> Unit,
    onPickClick: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    val title = param.title ?: param.label ?: param.name.orEmpty()
    when {
        param.type == "switch" -> {
            CheckboxPreference(
                title = title,
                summary = param.desc,
                checked = value == "1" || value == "true",
                onCheckedChange = { checked -> onValueChange(if (checked) "1" else "0") },
            )
        }
        param.type == "seekbar" -> {
            // Keep full precision while dragging; quantize the draft only when
            // the gesture finishes (doc: value mirrors onValueChange).
            var sliderValue by remember(param.name) {
                mutableFloatStateOf(value.toFloatOrNull() ?: param.min.toFloat())
            }
            LaunchedEffect(value) {
                value.toFloatOrNull()?.let { if (it != sliderValue) sliderValue = it }
            }
            SliderPreference(
                title = title,
                summary = param.desc,
                value = sliderValue,
                valueRange = param.min.toFloat()..param.max.toFloat(),
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onValueChange(sliderValue.toInt().toString()) },
                valueText = sliderValue.toInt().toString(),
            )
        }
        options.isNotEmpty() || param.type == "select" || param.type == "multiple" -> {
            val titles = options.map { it.title.orEmpty() }
            if (titles.isEmpty()) return
            val isMulti = param.multiple || param.type == "multiple"
            if (!isMulti) {
                val selectedValue = value
                val index = options.indexOfFirst { it.value == selectedValue }
                OverlayDropdownPreference(
                    title = title,
                    summary = param.desc,
                    items = titles,
                    selectedIndex = index,
                    onSelectedIndexChange = { i ->
                        if (i in options.indices) onValueChange(options[i].value.orEmpty())
                    },
                )
            } else {
                // Multi-select keeps a separator-joined draft value.
                val separator = param.separator
                val selectedSet = value.split(separator).filter { it.isNotEmpty() }.toSet()
                val entries = listOf(
                    top.yukonga.miuix.kmp.basic.DropdownEntry(
                        items = options.map { item ->
                            top.yukonga.miuix.kmp.basic.DropdownItem(
                                text = item.title.orEmpty(),
                                selected = item.value in selectedSet,
                                onClick = {
                                    val next = if (item.value in selectedSet) {
                                        selectedSet - item.value.orEmpty()
                                    } else {
                                        selectedSet + item.value.orEmpty()
                                    }
                                    onValueChange(next.joinToString(separator))
                                },
                            )
                        },
                    ),
                )
                OverlayDropdownPreference(
                    title = title,
                    summary = param.desc?.ifEmpty { "已选 ${selectedSet.size} 项" },
                    entries = entries,
                    collapseOnSelection = false,
                )
            }
        }
        param.type == "color" -> {
            ColorParamRow(
                title = title,
                value = value,
                onRequestPick = onPickColor,
                onValueChange = onValueChange,
            )
        }
        param.type == "file" -> {
            FileParamRow(param = param, value = value, onPickClick = onPickClick, onValueChange = onValueChange)
        }
        else -> {
            // text and unknown types fall back to a plain text field.
            TextField(
                label = title + if (param.placeholder.isNotEmpty()) " · ${param.placeholder}" else "" +
                    if (param.maxLength > 0) " (≤${param.maxLength})" else "",
                value = value,
                onValueChange = { newValue ->
                    onValueChange(
                        if (param.maxLength > 0) newValue.take(param.maxLength) else newValue,
                    )
                },
                enabled = !param.readonly,
            )
        }
    }
}

/** Copies a picked document into the app cache so shell scripts can read it. */
internal fun copyUriToCache(context: Context, uri: android.net.Uri, paramName: String): String? =
    runCatching {
        val resolver = context.contentResolver
        val fileName = "$paramName-${System.currentTimeMillis()}"
        val out = java.io.File(context.cacheDir, "picked/$fileName")
        out.parentFile?.mkdirs()
        resolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        out.absolutePath
    }.getOrNull()

/** Submit-time validation (original readParamsValue rules, non-throwing). */
internal fun validateDraft(node: ActionNode, draft: Map<String, String>): String? {
    val badColor = node.params
        ?.filter { it.type == "color" }
        ?.firstOrNull { param ->
            val v = draft[param.name.orEmpty()].orEmpty()
            v.isNotEmpty() && runCatching { PageConfigReader.parseColor(v) }.isFailure
        }
    if (badColor != null) {
        return "颜色格式无效：${badColor.title ?: badColor.name}（示例 #FF5722）"
    }
    val missing = node.params
        ?.filter { it.required == true }
        ?.filter { draft[it.name.orEmpty()].isNullOrEmpty() }
        ?.mapNotNull { it.title ?: it.label ?: it.name }
    if (!missing.isNullOrEmpty()) {
        return "必填项未填写：${missing.joinToString("、")}"
    }
    return null
}
