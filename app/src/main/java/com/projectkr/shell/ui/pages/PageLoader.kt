// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.pages

import com.projectkr.krscript.core.config.ConfReader
import com.projectkr.krscript.core.config.PageConfigReader
import com.projectkr.krscript.core.config.PathAnalysis
import com.projectkr.krscript.core.model.NodeInfoBase
import com.projectkr.krscript.core.model.PageNode
import com.projectkr.krscript.core.runtime.DefaultAssetExtractor

/**
 * Loads KrScript page configs into node lists. All functions block — call from a
 * background dispatcher.
 */
object PageLoader {

    /** Loads the top page list defined by `page_list_config` in kr-script.conf. */
    fun loadTopPage(): List<NodeInfoBase> {
        if (!com.projectkr.shell.runtime.KrScriptRuntime.isReady) return emptyList()
        val confMap = readConf()
        val page = PageNode("").apply {
            pageConfigPath = confMap.getOrDefault(
                ConfReader.PAGE_LIST_CONFIG,
                "file:///android_asset/kr-script/sample.xml",
            )
            pageConfigSh = confMap.getOrDefault(ConfReader.PAGE_LIST_CONFIG_SH, "")
        }
        return loadSubPage(page) ?: emptyList()
    }

    /** Builds the favorites page node defined by `favorite_config`. */
    fun favoritesPage(): PageNode {
        val confMap = readConf()
        return PageNode("").apply {
            pageConfigPath = confMap.getOrDefault(
                ConfReader.FAVORITE_CONFIG,
                "file:///android_asset/kr-script/favorites.xml",
            )
            pageConfigSh = confMap.getOrDefault(ConfReader.FAVORITE_CONFIG_SH, "")
        }
    }

    /**
     * Loads [page]'s config: beforeRead → resolve (config-sh or config path) →
     * parse → afterRead → loadSuccess/loadFail. Returns null on failure.
     */
    fun loadSubPage(page: PageNode): List<NodeInfoBase>? {
        val runtime = com.projectkr.shell.runtime.KrScriptRuntime
        if (!runtime.isReady) return null
        if (page.beforeRead.isNotEmpty()) {
            runtime.scriptEnv.executeResult(page.beforeRead, page)
        }

        var nodes: List<NodeInfoBase>? = null
        try {
            var inlineXml: String? = null
            var ref = ""
            if (page.pageConfigSh.isNotEmpty()) {
                // Original PageConfigSh: result ending .xml → path; starting
                // <?xml → parse the content directly; otherwise treat as path
                // and fall back to the static config on failure.
                val shResult = runtime.scriptEnv.executeResult(page.pageConfigSh, page).trim()
                when {
                    shResult.startsWith("<?xml") -> inlineXml = shResult
                    shResult.isNotEmpty() -> ref = shResult
                }
            }
            if (ref.isEmpty()) ref = page.pageConfigPath
            if (inlineXml != null || ref.isNotEmpty()) {
                val extractor = DefaultAssetExtractor(runtime.assetSource, runtime.fileStore)
                val locator = PathAnalysis(
                    assets = runtime.assetSource,
                    files = runtime.fileStore,
                    shell = runtime.shell,
                    extractor = extractor,
                    parentDir = page.pageConfigDir,
                )
                nodes = if (inlineXml != null) {
                    val reader = PageConfigReader(
                        runtime.evaluator, locator, extractor,
                        sourceStream = inlineXml.byteInputStream(),
                    )
                    reader.readConfigXml()
                } else {
                    val reader = PageConfigReader(
                        evaluator = runtime.evaluator,
                        locator = locator,
                        extractor = extractor,
                        pageConfigRef = ref,
                        parentDir = page.pageConfigDir,
                    )
                    reader.readConfigXml()
                }
            }
        } catch (_: Exception) {
            nodes = null
        }

        if (page.afterRead.isNotEmpty()) {
            runtime.scriptEnv.executeResult(page.afterRead, page)
        }
        when {
            nodes != null && page.loadSuccess.isNotEmpty() ->
                runtime.scriptEnv.executeResult(page.loadSuccess, page)
            nodes == null && page.loadFail.isNotEmpty() ->
                runtime.scriptEnv.executeResult(page.loadFail, page)
        }
        return nodes
    }

    private fun readConf(): Map<String, String> {
        val text = runCatching {
            com.projectkr.shell.runtime.KrScriptRuntime.assetSource.open("kr-script.conf")
                ?.bufferedReader()?.readText()
        }.getOrNull().orEmpty()
        return ConfReader().parse(text)
    }
}
