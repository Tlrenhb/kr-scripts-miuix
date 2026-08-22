// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.online

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.projectkr.krscript.core.model.NodeInfoBase
import com.projectkr.shell.runtime.KrScriptRuntime
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/**
 * Online html page rendered in a WebView with the `KrScriptCore` JS bridge —
 * port of the original ActionPageOnline + WebViewInjector contract:
 *
 *  - `KrScriptCore.rootCheck(): boolean`
 *  - `KrScriptCore.executeShell(script): string` (synchronous, executor.sh)
 *  - `KrScriptCore.extractAssets(assetRef): string` → absolute extracted path
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OnlinePageScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { inner ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    configureSettings(url)
                    addJavascriptInterface(JsBridge(), "KrScriptCore")
                    webChromeClient = WebChromeClient()
                    loadUrl(url)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        )
    }
}

private fun WebView.configureSettings(pageUrl: String) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.useWideViewPort = true
    settings.allowContentAccess = true
    if (pageUrl.startsWith("file:")) {
        // Local pages keep file access only when explicitly credible.
        settings.allowFileAccess = true
    }
}

/**
 * JS-facing engine bound as `window.KrScriptCore`. Methods run on a WebView
 * background thread (JavascriptInterface contract) so blocking shell calls are
 * acceptable here, matching the original implementation.
 */
class JsBridge {

    private val virtualRoot = NodeInfoBase("")

    @JavascriptInterface
    fun rootCheck(): Boolean =
        KrScriptRuntime.isReady && KrScriptRuntime.rooted

    @JavascriptInterface
    fun executeShell(script: String?): String {
        if (!KrScriptRuntime.isReady || script.isNullOrEmpty()) return ""
        return KrScriptRuntime.scriptEnv.executeResult(script, virtualRoot)
    }

    @JavascriptInterface
    fun extractAssets(assetRef: String?): String {
        if (!KrScriptRuntime.isReady || assetRef.isNullOrEmpty()) return ""
        return KrScriptRuntime.extractor.extractResource(assetRef).orEmpty()
    }
}
