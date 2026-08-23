// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.online

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.projectkr.krscript.core.exec.ScriptProcessRunner
import com.projectkr.krscript.core.model.NodeInfoBase
import com.projectkr.shell.runtime.KrScriptRuntime
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import java.io.File

/**
 * Online html page rendered in a WebView with the complete `KrScriptCore` JS
 * bridge — port of ActionPageOnline + WebViewInjector:
 *
 *  - rootCheck():boolean
 *  - executeShell(script):String            (synchronous, persistent shell)
 *  - executeShellAsync(script, callbackFunction):boolean — streams
 *    {type:"read"|"readError"|"exit", message} JSON events into the callback
 *  - extractAssets(ref):String
 *  - fileChooser(callbackFunction):boolean  ({absPath} posted back)
 *
 * Downloads confirm-free enqueue into the system DownloadManager; external
 * schemes hand off to the system; Back traverses in-page history first.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OnlinePageScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var progress by remember { mutableIntStateOf(-1) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var jsFileChooserCallback by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val callback = jsFileChooserCallback
        jsFileChooserCallback = null
        val view = webViewRef ?: return@rememberLauncherForActivityResult
        val payload = if (uri != null) {
            val copied = copyUriToCacheForWeb(context, uri)
            if (copied != null) "{\"absPath\":\"${jsonEscape(copied)}\"}" else "null"
        } else {
            "null"
        }
        view.post { view.evaluateJavascript("$callback($payload)", null) }
    }

    // The JS interface thread cannot launch ActivityResult APIs; route the
    // request through state and let this composable drive the picker.
    LaunchedEffect(jsFileChooserCallback) {
        jsFileChooserCallback?.let {
            filePicker.launch(arrayOf("*/*"))
        }
    }

    // In-page back: traverse WebView history before popping the route.
    BackHandler(enabled = webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = {
                        val view = webViewRef
                        if (view != null && view.canGoBack()) view.goBack() else onBack()
                    }) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize()) {
            if (progress in 0..99) {
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        configureSettings(pageUrl = url)
                        addJavascriptInterface(
                            JsBridgeImpl(this, onFileChooser = { callbackName ->
                                jsFileChooserCallback = callbackName
                            }),
                            "KrScriptCore",
                        )
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val scheme = request.url.scheme?.lowercase()
                                if (scheme != "http" && scheme != "https") {
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                                    }
                                    return true
                                }
                                return false
                            }
                        }
                        setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
                            runCatching {
                                val fileName =
                                    URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                                val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                                    setTitle(fileName)
                                    setMimeType(mimeType)
                                    setNotificationVisibility(
                                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                                    )
                                    setDestinationInExternalPublicDir(
                                        Environment.DIRECTORY_DOWNLOADS,
                                        fileName,
                                    )
                                }
                                ctx.getSystemService(DownloadManager::class.java)
                                    ?.enqueue(request)
                                Toast.makeText(ctx, "已开始下载", Toast.LENGTH_SHORT).show()
                            }
                        }
                        loadUrl(url)
                    }
                },
                onRelease = { it.destroy() },
                update = { view ->
                    // Keep the reference fresh for the bridge and back handling.
                    webViewRef = view
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )
        }
    }
}

private fun WebView.configureSettings(pageUrl: String) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.useWideViewPort = true
    settings.allowContentAccess = true
    // Original credible gate: only local asset pages receive full file access.
    val credible = pageUrl.startsWith("file:///android_asset/")
    settings.allowFileAccess = credible
    @Suppress("DEPRECATION")
    settings.allowFileAccessFromFileURLs = credible
    @Suppress("DEPRECATION")
    settings.allowUniversalAccessFromFileURLs = credible
}

/**
 * JS-facing engine bound as `window.KrScriptCore`. Methods run on a WebView
 * background thread (JavascriptInterface contract), so blocking shell calls
 * are acceptable — matching the original implementation.
 */
private class JsBridgeImpl(
    private val webView: WebView,
    private val onFileChooser: (String) -> Unit,
) {

    private val virtualRoot = NodeInfoBase("")

    /** Live probe through the persistent shell (original checked su each time). */
    @JavascriptInterface
    fun rootCheck(): Boolean {
        if (!KrScriptRuntime.isReady) return false
        return KrScriptRuntime.shell.execute("id -u").trim() == "0"
    }

    @JavascriptInterface
    fun executeShell(script: String?): String {
        if (!KrScriptRuntime.isReady || script.isNullOrEmpty()) return ""
        return KrScriptRuntime.scriptEnv.executeResult(script, virtualRoot)
    }

    /**
     * Streams read/read-error/exit JSON events into [callbackFunction] while
     * [script] runs in a dedicated process (original executeShellAsync).
     */
    @JavascriptInterface
    fun executeShellAsync(script: String?, callbackFunction: String?): Boolean {
        if (!KrScriptRuntime.isReady || script.isNullOrEmpty() || callbackFunction.isNullOrEmpty()) {
            return false
        }
        fun emit(jsonBody: String) {
            webView.post {
                webView.evaluateJavascript("$callbackFunction($jsonBody)", null)
            }
        }
        val runner = ScriptProcessRunner(
            environment = KrScriptRuntime.scriptEnv,
            extractor = KrScriptRuntime.extractor,
            rootMode = KrScriptRuntime.rooted,
        )
        val process = runner.execute(
            script = script,
            node = virtualRoot,
            params = emptyMap(),
            tag = "web_" + System.currentTimeMillis(),
            onLine = { line, isErr ->
                val type = if (isErr) "readError" else "read"
                emit("{\"type\":\"$type\",\"message\":\"${jsonEscape(line)}\"}")
            },
            onExit = { code ->
                emit("{\"type\":\"exit\",\"message\":$code}")
            },
        )
        return process != null
    }

    @JavascriptInterface
    fun extractAssets(assetRef: String?): String {
        if (!KrScriptRuntime.isReady || assetRef.isNullOrEmpty()) return ""
        return KrScriptRuntime.extractor.extractResource(assetRef).orEmpty()
    }

    /** Opens the host file selector; result posts {absPath} to [callbackFunction]. */
    @JavascriptInterface
    fun fileChooser(callbackFunction: String): Boolean {
        if (callbackFunction.isEmpty()) return false
        webView.post { onFileChooser(callbackFunction) }
        return true
    }
}

internal fun copyUriToCacheForWeb(context: Context, uri: Uri): String? = runCatching {
    val out = java.io.File(context.cacheDir, "webpick/${System.currentTimeMillis()}")
    out.parentFile?.mkdirs()
    context.contentResolver.openInputStream(uri)?.use { input ->
        out.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    out.absolutePath
}.getOrNull()

private fun jsonEscape(text: String): String =
    text.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
