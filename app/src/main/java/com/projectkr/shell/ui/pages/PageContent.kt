// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.projectkr.krscript.core.model.ActionNode
import com.projectkr.krscript.core.model.NodeInfoBase
import com.projectkr.krscript.core.model.RunnableNode
import com.projectkr.shell.ui.dialogs.ActionParamsDialog
import com.projectkr.shell.ui.dialogs.ConfirmDialog
import com.projectkr.shell.ui.dialogs.LogDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Shared page-rendering state used by both the 页面 tab and pushed PageDetail
 * screens: loads nodes on IO and tracks refresh cycles.
 */
class PageContentState(
    val nodes: List<NodeInfoBase>?,
    val loading: Boolean,
    internal val scope: CoroutineScope,
)

@Composable
fun rememberPageContent(reloadKey: Int, loadNodes: () -> List<NodeInfoBase>?): PageContentState {
    var nodes by remember { mutableStateOf<List<NodeInfoBase>?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        loading = true
        nodes = withContext(Dispatchers.IO) { loadNodes() }
        loading = false
    }

    return PageContentState(nodes, loading, scope)
}

/**
 * Renders node content plus the confirm / params / log dialogs bound to one
 * [ExecutionController]. Every KrScript page screen reuses this.
 */
@Composable
fun NodeScreenBody(
    controller: ExecutionController,
    nodes: List<NodeInfoBase>?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Box(modifier) {
        when {
            // Keep all feedback states structurally stable and explicit. This
            // improves every XML-driven screen without changing its semantics.
            loading -> PageFeedbackState(
                title = "正在加载页面",
                summary = "正在读取配置与资源",
                loading = true,
            )

            // Parse failed (readConfigXml returned null).
            nodes == null -> PageFeedbackState(
                title = "页面配置解析失败",
                summary = "请检查 XML 或脚本配置后重试",
                error = true,
                actionLabel = "重试",
                onAction = onRetry,
            )

            // Config parsed but defines nothing.
            nodes.isEmpty() -> PageFeedbackState(
                title = "暂无功能",
                summary = "当前配置没有可显示的功能项",
            )

            else -> NodeListContent(
                nodes = nodes,
                callbacks = controller,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // Doc lifecycle: keep dialogs composed and bind `show` to state so the
    // hide animation runs; retain the last payload while the animation plays.
    var lastConfirm by remember { mutableStateOf<RunnableNode?>(null) }
    controller.confirmRequest?.let { lastConfirm = it }
    lastConfirm?.let { node ->
        ConfirmDialog(
            node = node,
            show = controller.confirmRequest != null,
            onConfirm = { controller.submitConfirmation() },
            onDismiss = { controller.dismissConfirmation() },
        )
    }

    var lastParams by remember { mutableStateOf<ActionNode?>(null) }
    controller.paramsAction?.let { lastParams = it }
    lastParams?.let { action ->
        ActionParamsDialog(
            node = action,
            show = controller.paramsAction != null,
            onSubmit = { values -> controller.submitParams(action, values) },
            onDismiss = { controller.dismissParams() },
        )
    }

    var lastSession by remember { mutableStateOf<com.projectkr.shell.runtime.ScriptActions.Session?>(null) }
    controller.activeSession?.let { lastSession = it }
    LogDialog(
        session = lastSession,
        show = controller.activeSession != null,
        onClose = { controller.closeSession() },
    )

    // Original DialogLogFragment.onSuccess: autoOff closes the log when done.
    LaunchedEffect(controller.activeSession?.running, controller.activeSession?.exitCode) {
        val s = controller.activeSession ?: return@LaunchedEffect
        if (!s.running && s.node.autoOff) {
            kotlinx.coroutines.delay(600)
            controller.closeSession()
        }
    }

    // Completion flags of VISIBLE sessions: reload-page / auto-finish / blocks.
    LaunchedEffect(controller.activeSession?.running, controller.activeSession?.exitCode) {
        val s = controller.activeSession ?: return@LaunchedEffect
        if (!s.running) controller.onSessionCompleted(s)
    }
}

/**
 * Shared blocking/empty feedback for every XML-driven page. The content state
 * stays explicit while Miuix cards, text styles, and semantic colors provide a
 * consistent hierarchy in light, dark, and Monet themes.
 */
@Composable
private fun PageFeedbackState(
    title: String,
    summary: String,
    loading: Boolean = false,
    error: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val contentColor = if (error) {
        MiuixTheme.colorScheme.onErrorContainer
    } else {
        MiuixTheme.colorScheme.onSurface
    }
    val summaryColor = if (error) {
        MiuixTheme.colorScheme.onErrorContainer.copy(alpha = 0.76f)
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val colors = if (error) {
        CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer,
            contentColor = contentColor,
        )
    } else {
        CardDefaults.defaultColors()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(20.dp),
            colors = colors,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    Icon(
                        imageVector = MiuixIcons.Info,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = summaryColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        text = actionLabel,
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}
