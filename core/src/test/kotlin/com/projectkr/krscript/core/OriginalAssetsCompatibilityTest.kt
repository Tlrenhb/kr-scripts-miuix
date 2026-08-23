// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core

import com.projectkr.krscript.core.config.ConfReader
import com.projectkr.krscript.core.config.PageConfigReader
import com.projectkr.krscript.core.config.PathAnalysis
import com.projectkr.krscript.core.model.GroupNode
import com.projectkr.krscript.core.model.PageNode
import com.projectkr.krscript.core.runtime.DefaultAssetExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Proves the rewritten engine parses the ORIGINAL PIO assets unchanged —
 * fixtures under /original/ are verbatim copies from the 2021 codebase.
 */
class OriginalAssetsCompatibilityTest {

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/original/$name")!!
            .bufferedReader().readText()

    private fun reader(xml: String, evaluator: FakeEvaluator): PageConfigReader {
        val assets = FakeAssets()
        val files = FakeFiles(Files.createTempDirectory("krorig").toFile())
        val extractor = DefaultAssetExtractor(assets, files)
        val locator = PathAnalysis(assets, files, FakeShell(), extractor)
        return PageConfigReader(
            evaluator, locator, extractor,
            sourceStream = xml.byteInputStream(),
        )
    }

    @Test
    fun `original more_xml parses with root page element`() {
        // more.xml's ROOT element is <page> (not <kr-script>): it must NOT be
        // added as a node, but its groups/children and root-level resources
        // must still be processed.
        val xml = resource("more.xml")
        var extractedDirs = mutableListOf<String>()
        val assets = FakeAssets()
        val files = FakeFiles(Files.createTempDirectory("krorig2").toFile())
        val recordingExtractor = object : com.projectkr.krscript.core.runtime.AssetExtractor by DefaultAssetExtractor(assets, files) {
            override fun extractResources(dir: String): String {
                extractedDirs.add(dir)
                return dir
            }
            override fun extractResource(fileName: String): String? {
                extractedDirs.add(fileName)
                return fileName
            }
        }
        val locator = PathAnalysis(assets, files, FakeShell(), recordingExtractor)
        val nodes = PageConfigReader(
            FakeEvaluator(), locator, recordingExtractor,
            sourceStream = xml.byteInputStream(),
        ).readConfigXml()

        assertNotNull(nodes)
        val groups = nodes!!.filterIsInstance<GroupNode>()
        assertEquals(
            listOf("开发文档", "无关紧要的测试代码", "路径测试", "Ansole 联动", "其它"),
            groups.map { it.title },
        )
        // Root-level <resource> tags must be honored (asset extraction).
        assertTrue(extractedDirs.any { it.contains("samples/thermal") })
        assertTrue(extractedDirs.any { it.contains("samples/kt") })
        assertTrue(extractedDirs.any { it == "samples/relative_path" || it.endsWith("relative_path") })
        assertTrue(extractedDirs.any { it.contains("app_store") })

        // The html page inside the first group becomes a node with onlineHtmlPage.
        val htmlPage = groups.first().children.filterIsInstance<PageNode>().first()
        assertEquals(
            "file:///android_asset/docs/index.html#/document-library",
            htmlPage.onlineHtmlPage,
        )
    }

    @Test
    fun `original favorites_xml parses with items root`() {
        val xml = resource("favorites.xml")
        val nodes = reader(xml, FakeEvaluator()).readConfigXml()

        assertNotNull(nodes)
        val groups = nodes!!.filterIsInstance<GroupNode>()
        assertEquals(
            listOf("运行环境测试", "功能节点", "文字节点", "其它", "Web引擎"),
            groups.map { it.title },
        )
        // Every entry is a sub-page pointing at a samples config.
        val configs = groups.flatMap { it.children }
            .filterIsInstance<PageNode>()
            .map { it.pageConfigPath }
        assertTrue(configs.contains("samples/action.xml"))
        assertTrue(configs.contains("samples/bg_task/bg_task.xml"))
    }

    @Test
    fun `original local conf parses all keys`() {
        val map = ConfReader().parse(resource("kr-script-local.conf"))
        assertEquals("file:///android_asset/kr-script/executor.sh", map["executor_core"])
        assertEquals("file:///android_asset/kr-script/more.xml", map["page_list_config"])
        assertEquals("file:///android_asset/kr-script/favorites.xml", map["favorite_config"])
        assertEquals("0", map["allow_home_page"])
        assertEquals("file:///android_asset/kr-script/toolkit", map["toolkit_dir"])
    }

    @Test
    fun `original online conf parses script-driven keys`() {
        val map = ConfReader().parse(resource("kr-script-online.conf"))
        assertEquals(
            "file:///android_asset/kr-script-online/more.sh",
            map["page_list_config_sh"],
        )
        assertEquals(
            "file:///android_asset/kr-script-online/favorites.sh",
            map["favorite_config_sh"],
        )
        assertEquals(
            "file:///android_asset/kr-script-online/before_start.sh",
            map["before_start_sh"],
        )
        assertEquals("1", map["allow_home_page"])
    }
}
