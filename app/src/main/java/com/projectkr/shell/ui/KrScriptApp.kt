// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.projectkr.shell.core.config.PageConfigReader
import com.projectkr.shell.core.config.ShellRunner
import com.projectkr.shell.core.model.ActionNode
import com.projectkr.shell.core.model.ActionParamInfo
import com.projectkr.shell.core.model.GroupNode
import com.projectkr.shell.core.model.NodeInfoBase
import com.projectkr.shell.core.model.PageNode
import com.projectkr.shell.core.model.PickerNode
import com.projectkr.shell.core.model.SwitchNode
import com.projectkr.shell.core.model.TextNode
import com.projectkr.shell.shell.RootShellRunner
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun KrScriptApp() {
    val context = LocalContext.current
    val shellRunner = remember { RootShellRunner() }
    val reader = remember { PageConfigReader(context, shellRunner) }
    val rootNodes = remember {
        reader.readConfigXml("file:///android_asset/sample.xml") ?: emptyList()
    }
    var actionForParams by remember { mutableStateOf<ActionNode?>(null) }
    var currentNodes by remember { mutableStateOf(rootNodes) }
    var currentTitle by remember { mutableStateOf("KrScript Miuix") }
    val pageStack = remember { mutableStateListOf<Pair<String, List<NodeInfoBase>>>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = currentTitle,
                navigationIcon = {
                    if (pageStack.isNotEmpty()) {
                        IconButton(onClick = {
                            val previous = pageStack.removeAt(pageStack.lastIndex)
                            currentNodes = previous.second
                            currentTitle = previous.first
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        NodeListScreen(
            nodes = currentNodes,
            contentPadding = padding,
            context = context,
            shellRunner = shellRunner,
            onActionClick = { actionForParams = it },
            onPageClick = { page ->
                val path = page.pageConfigPath
                if (path.isNotBlank()) {
                    val childNodes = reader.readConfigXml(path, page.pageConfigDir) ?: emptyList()
                    if (childNodes.isNotEmpty()) {
                        pageStack.add(currentTitle to currentNodes)
                        currentNodes = childNodes
                        currentTitle = page.title
                    }
                } else {
                    Toast.makeText(context, "此页面没有配置子页面", Toast.LENGTH_SHORT).show()
                }
            }
        )
        ActionParamsDialog(
            action = actionForParams,
            context = context,
            shellRunner = shellRunner,
            onDismiss = { actionForParams = null }
        )
    }
}

@Composable
private fun NodeListScreen(
    nodes: List<NodeInfoBase>,
    contentPadding: PaddingValues,
    context: Context,
    shellRunner: ShellRunner,
    onActionClick: (ActionNode) -> Unit,
    onPageClick: (PageNode) -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding
    ) {
        nodes.forEach { node ->
            when (node) {
                is GroupNode -> {
                    item(key = node.index) {
                        SmallTitle(text = node.title)
                    }
                    items(node.children, key = { it.index }) { child ->
                        NodeItem(
                            node = child,
                            context = context,
                            shellRunner = shellRunner,
                            onActionClick = onActionClick,
                            onPageClick = onPageClick,
                        )
                    }
                }
                else -> {
                    item(key = node.index) {
                        NodeItem(
                            node = node,
                            context = context,
                            shellRunner = shellRunner,
                            onActionClick = onActionClick,
                            onPageClick = onPageClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeItem(
    node: NodeInfoBase,
    context: Context,
    shellRunner: ShellRunner,
    onActionClick: (ActionNode) -> Unit,
    onPageClick: (PageNode) -> Unit,
) {
    when (node) {
        is SwitchNode -> {
            var checked by remember { mutableStateOf(node.checked) }
            val setState = node.setState ?: ""
            SwitchPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc },
                checked = checked,
                onCheckedChange = { newValue ->
                    checked = newValue
                    if (setState.isNotBlank()) {
                        val result = shellRunner.execute(
                            setState.replace("\$state", if (newValue) "1" else "0")
                        )
                        if (result.isNotBlank()) {
                            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
        is PickerNode -> {
            val options = node.options?.map { it.toString() } ?: emptyList()
            var selectedIndex by remember { mutableStateOf(0) }
            val setState = node.setState ?: ""
            OverlayDropdownPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc },
                items = options,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { index ->
                    selectedIndex = index
                    val selectedValue = node.options?.getOrNull(index)?.value ?: ""
                    if (setState.isNotBlank()) {
                        val result = shellRunner.execute(
                            setState.replace("\$state", selectedValue)
                        )
                        if (result.isNotBlank()) {
                            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
        is PageNode -> {
            ArrowPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc },
                onClick = { onPageClick(node) }
            )
        }
        is ActionNode -> {
            ArrowPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc },
                onClick = {
                    if (node.params.isNullOrEmpty()) {
                        runAction(node, context, shellRunner)
                    } else {
                        onActionClick(node)
                    }
                }
            )
        }
        is TextNode -> {
            SmallTitle(text = node.title)
        }
        else -> {
            ArrowPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc }
            )
        }
    }
}

private class ParamUiState(
    val param: ActionParamInfo,
    val text: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue(param.value ?: "")),
    val checked: MutableState<Boolean> = mutableStateOf(param.value == "1" || param.value == "true"),
    val floatValue: MutableState<Float> = mutableStateOf(param.value?.toFloatOrNull() ?: 0f),
    val selectedIndex: MutableState<Int> = mutableStateOf(0),
)

@Composable
private fun ActionParamsDialog(
    action: ActionNode?,
    context: Context,
    shellRunner: ShellRunner,
    onDismiss: () -> Unit,
) {
    if (action == null) return
    val params = action.params ?: emptyList()
    val states = remember(action) {
        params.map { ParamUiState(it) }
    }

    OverlayDialog(
        show = true,
        title = action.title,
        summary = action.summary.ifEmpty { action.desc },
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            params.forEachIndexed { index, param ->
                when {
                    param.type == "switch" || param.type == "boolean" || param.type == "checkbox" -> {
                        CheckboxPreference(
                            title = param.title ?: param.name ?: "",
                            summary = param.desc,
                            checked = states[index].checked.value,
                            onCheckedChange = { states[index].checked.value = it }
                        )
                    }
                    param.type == "select" || param.type == "dropdown" || param.options != null -> {
                        val options = param.options?.map { it.toString() } ?: emptyList()
                        OverlayDropdownPreference(
                            title = param.title ?: param.name ?: "",
                            summary = param.desc,
                            items = options,
                            selectedIndex = states[index].selectedIndex.value,
                            onSelectedIndexChange = { states[index].selectedIndex.value = it }
                        )
                    }
                    param.type == "seekbar" || param.type == "slider" || param.type == "range" -> {
                        val min = if (param.min == Int.MIN_VALUE) 0f else param.min.toFloat()
                        val max = if (param.max == Int.MAX_VALUE) 100f else param.max.toFloat()
                        SliderPreference(
                            title = param.title ?: param.name ?: "",
                            summary = param.desc,
                            value = states[index].floatValue.value,
                            onValueChange = { states[index].floatValue.value = it },
                            valueRange = min..max
                        )
                    }
                    else -> {
                        TextField(
                            value = states[index].text.value,
                            onValueChange = { states[index].text.value = it },
                            label = param.title ?: param.name ?: "",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            TextButton(
                text = "确定",
                onClick = {
                    val values = mutableMapOf<String, String>()
                    params.forEachIndexed { index, param ->
                        val name = param.name ?: ""
                        if (name.isEmpty()) return@forEachIndexed
                        val value = when {
                            param.type == "switch" || param.type == "boolean" || param.type == "checkbox" ->
                                if (states[index].checked.value) "1" else "0"
                            param.type == "select" || param.type == "dropdown" || param.options != null ->
                                param.options?.getOrNull(states[index].selectedIndex.value)?.value ?: ""
                            param.type == "seekbar" || param.type == "slider" || param.type == "range" ->
                                states[index].floatValue.value.toInt().toString()
                            else -> states[index].text.value.text
                        }
                        values[name] = value
                    }
                    var script = action.setState ?: ""
                    values.forEach { (key, value) ->
                        script = script.replace("\$key", value)
                    }
                    val result = shellRunner.execute(script)
                    if (result.isNotBlank()) {
                        Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun runAction(
    node: ActionNode,
    context: Context,
    shellRunner: ShellRunner,
) {
    val script = node.setState ?: ""
    if (script.isNotBlank()) {
        val result = shellRunner.execute(script)
        Toast.makeText(
            context,
            result.ifEmpty { "执行完成" },
            Toast.LENGTH_SHORT
        ).show()
    }
}
