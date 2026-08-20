// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import top.yukonga.miuix.kmp.basic.TopAppBar
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
        )
    }
}

@Composable
private fun NodeListScreen(
    nodes: List<NodeInfoBase>,
    contentPadding: PaddingValues,
    context: Context,
    shellRunner: ShellRunner,
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
                        )
                    }
                }
                else -> {
                    item(key = node.index) {
                        NodeItem(
                            node = node,
                            context = context,
                            shellRunner = shellRunner,
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
