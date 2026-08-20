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
import androidx.compose.runtime.getValue
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
import com.projectkr.shell.core.model.GroupNode
import com.projectkr.shell.core.model.NodeInfoBase
import com.projectkr.shell.core.model.PageNode
import com.projectkr.shell.core.model.PickerNode
import com.projectkr.shell.core.model.SwitchNode
import com.projectkr.shell.core.model.TextNode
import com.projectkr.shell.shell.RootShellRunner
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun KrScriptApp() {
    val context = LocalContext.current
    val shellRunner = remember { RootShellRunner() }
    val nodes = remember {
        val reader = PageConfigReader(context, shellRunner)
        reader.readConfigXml("file:///android_asset/sample.xml") ?: emptyList()
    }
    var actionForParams by remember { mutableStateOf<ActionNode?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = "KrScript Miuix")
        }
    ) { padding ->
        NodeListScreen(
            nodes = nodes,
            contentPadding = padding,
            context = context,
            shellRunner = shellRunner,
            onActionClick = { actionForParams = it }
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
                onClick = {
                    Toast.makeText(context, "TODO: 子页面跳转", Toast.LENGTH_SHORT).show()
                }
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
        params.map { mutableStateOf(TextFieldValue(it.value ?: "")) }
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
                TextField(
                    value = states[index].value,
                    onValueChange = { states[index].value = it },
                    label = param.title ?: param.name ?: "",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            TextButton(
                text = "确定",
                onClick = {
                    val values = mutableMapOf<String, String>()
                    params.forEachIndexed { index, param ->
                        val name = param.name ?: ""
                        if (name.isNotEmpty()) {
                            values[name] = states[index].value.text
                        }
                    }
                    var script = action.setState ?: ""
                    values.forEach { (key, value) ->
                        script = script.replace("\$$key", value)
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
