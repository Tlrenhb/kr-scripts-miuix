// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.projectkr.shell.core.model.ActionNode
import com.projectkr.shell.core.model.GroupNode
import com.projectkr.shell.core.model.NodeInfoBase
import com.projectkr.shell.core.model.PageNode
import com.projectkr.shell.core.model.PickerNode
import com.projectkr.shell.core.model.SelectItem
import com.projectkr.shell.core.model.SwitchNode
import com.projectkr.shell.core.model.TextNode
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun KrScriptApp() {
    val nodes = remember { sampleNodes() }
    Scaffold(
        topBar = {
            TopAppBar(title = "KrScript Miuix")
        }
    ) { padding ->
        NodeListScreen(nodes = nodes, contentPadding = padding)
    }
}

@Composable
private fun NodeListScreen(
    nodes: List<NodeInfoBase>,
    contentPadding: PaddingValues,
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
                        NodeItem(child)
                    }
                }
                else -> {
                    item(key = node.index) {
                        NodeItem(node)
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeItem(node: NodeInfoBase) {
    when (node) {
        is SwitchNode -> {
            var checked by remember { mutableStateOf(node.checked) }
            SwitchPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc },
                checked = checked,
                onCheckedChange = { checked = it }
            )
        }
        is PickerNode -> {
            val options = node.options?.map { it.toString() } ?: emptyList()
            var selectedIndex by remember { mutableStateOf(0) }
            OverlayDropdownPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc },
                items = options,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { selectedIndex = it }
            )
        }
        is PageNode -> {
            ArrowPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc },
                onClick = { /* TODO: navigate to sub page */ }
            )
        }
        is ActionNode -> {
            ArrowPreference(
                title = node.title,
                summary = node.summary.ifEmpty { node.desc },
                onClick = { /* TODO: execute action */ }
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

private fun sampleNodes(): List<NodeInfoBase> {
    val group = GroupNode("")
    group.title = "常用工具"
    group.children.add(
        SwitchNode("").apply {
            title = "示例开关"
            summary = "这是一个 Miuix SwitchPreference"
            checked = true
        }
    )
    group.children.add(
        PickerNode("").apply {
            title = "示例选择器"
            summary = "这是一个 Miuix OverlayDropdownPreference"
            options = arrayListOf(
                SelectItem().apply { title = "选项 A"; value = "a" },
                SelectItem().apply { title = "选项 B"; value = "b" }
            )
        }
    )
    return listOf(
        PageNode("").apply {
            title = "子页面"
            summary = "进入下一个页面"
        },
        group,
        ActionNode("").apply {
            title = "执行动作"
            summary = "运行 Shell 脚本"
        },
        TextNode("").apply {
            title = "说明文字"
            summary = "用于展示说明信息"
        }
    )
}
