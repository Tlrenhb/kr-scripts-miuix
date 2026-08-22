// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core

import com.projectkr.krscript.core.config.PathAnalysis
import com.projectkr.krscript.core.runtime.DefaultAssetExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class PathAnalysisTest {

    private val assets = FakeAssets().apply {
        add("kr-script/sample.xml", "<kr-script/>")
        add("kr-script/sub/sub.xml", "<kr-script/>")
    }

    private fun analysis(parentDir: String): PathAnalysis {
        val files = FakeFiles(Files.createTempDirectory("krlayout").toFile())
        val extractor = DefaultAssetExtractor(assets, files)
        return PathAnalysis(assets, files, FakeShell(), extractor, parentDir)
    }

    @Test
    fun `direct asset refs open the asset`() {
        val located = analysis("").parsePath("file:///android_asset/kr-script/sample.xml")
        assertNotNull(located)
        assertEquals(
            "file:///android_asset/kr-script/sample.xml",
            analysis("").let { it.parsePath("file:///android_asset/kr-script/sample.xml"); it.getCurrentAbsPath() },
        )
    }

    @Test
    fun `relative refs resolve against an asset parent`() {
        val pa = analysis("file:///android_asset/kr-script")
        assertNotNull(pa.parsePath("sample.xml"))

        val pa2 = analysis("file:///android_asset/kr-script/sub")
        assertNotNull(pa2.parsePath("../sample.xml"))
    }

    @Test
    fun `pathConcat resolves dot-dot segments`() {
        val pa = analysis("")
        assertEquals(
            "file:///android_asset/kr-script/sample.xml",
            pa.pathConcat("file:///android_asset/kr-script/sub", "../sample.xml"),
        )
        assertEquals(
            "file:///android_asset/kr-script/sub/sub.xml",
            pa.pathConcat("file:///android_asset/kr-script/", "./sub/sub.xml"),
        )
        assertEquals("/sdcard/a/b.xml", pa.pathConcat("/sdcard/a", "b.xml"))
    }

    @Test
    fun `missing files return null`() {
        assertNull(analysis("file:///android_asset/kr-script").parsePath("nope.xml"))
    }

    @Test
    fun `absolute readable paths open directly`() {
        val tmp = Files.createTempFile("krabs", ".xml").toFile()
        tmp.writeText("<kr-script/>")
        val pa = analysis("")
        val located = pa.parsePath(tmp.absolutePath)
        assertNotNull(located)
        assertEquals(tmp.absolutePath, pa.getCurrentAbsPath())
        located!!.stream.use { s -> assertTrue(s.readBytes().isNotEmpty()) }
    }
}
