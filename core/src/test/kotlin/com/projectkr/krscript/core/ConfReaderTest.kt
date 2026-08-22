// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core

import com.projectkr.krscript.core.config.ConfReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConfReaderTest {

    @Test
    fun `parses key-value pairs and ignores comments`() {
        val conf = """
            # 这是注释
            executor_core="file:///android_asset/kr-script/executor.sh"

            page_list_config="file:///android_asset/kr-script/sample.xml"
            allow_home_page="1"
        """.trimIndent()

        val map = ConfReader().parse(conf)

        assertEquals(
            "file:///android_asset/kr-script/executor.sh",
            map["executor_core"],
        )
        assertEquals("file:///android_asset/kr-script/sample.xml", map["page_list_config"])
        assertEquals("1", map["allow_home_page"])
        assertFalse(map.containsKey("# 这是注释"))
    }

    @Test
    fun `unquoted values are kept verbatim`() {
        val map = ConfReader().parse("key=value\nother = spaced value ")
        assertEquals("value", map["key"])
        assertEquals("spaced value", map["other"])
    }

    @Test
    fun `values may contain equals signs`() {
        val map = ConfReader().parse("cmd=\"echo a=b\"")
        assertEquals("echo a=b", map["cmd"])
    }
}
