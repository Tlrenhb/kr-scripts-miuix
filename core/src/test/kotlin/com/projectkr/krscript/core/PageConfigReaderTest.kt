// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core

import com.projectkr.krscript.core.config.PageConfigReader
import com.projectkr.krscript.core.model.ActionNode
import com.projectkr.krscript.core.model.GroupNode
import com.projectkr.krscript.core.model.PageNode
import com.projectkr.krscript.core.model.PickerNode
import com.projectkr.krscript.core.model.SwitchNode
import com.projectkr.krscript.core.model.TextNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class PageConfigReaderTest {

    private fun reader(xml: String, evaluator: FakeEvaluator): PageConfigReader {
        val assets = FakeAssets()
        val files = FakeFiles(Files.createTempDirectory("krtest").toFile())
        val extractor = com.projectkr.krscript.core.runtime.DefaultAssetExtractor(assets, files)
        val locator = com.projectkr.krscript.core.config.PathAnalysis(
            assets, files, com.projectkr.krscript.core.FakeShell(), extractor,
        )
        return PageConfigReader(
            evaluator, locator, extractor,
            sourceStream = ByteArrayInputStream(xml.toByteArray()),
        )
    }

    @Test
    fun `parses all node types`() {
        val xml = """
            <kr-script>
                <group title="分组">
                    <switch key="sw1" title="开关">
                        <get>echo 1</get>
                        <set>echo set</set>
                    </switch>
                    <picker key="pk1" title="选择器">
                        <option val="a">A</option>
                        <option val="b">B</option>
                        <get>echo b</get>
                        <set>echo set</set>
                    </picker>
                    <action key="ac1" title="动作">
                        <script>echo run</script>
                    </action>
                </group>
                <text title="文本">
                    <slice bold="true" color="#FF0000">红字</slice>
                </text>
                <page key="pg1" title="子页" config="file:///android_asset/kr-script/sub.xml" />
            </kr-script>
        """.trimIndent()
        // switch get → "1", picker get → "b"
        val evaluator = FakeEvaluator(mapOf("echo 1" to "1", "echo b" to "b"))
        val nodes = reader(xml, evaluator).readConfigXml()

        assertNotNull(nodes)
        assertEquals(3, nodes!!.size)

        val group = nodes[0] as GroupNode
        assertEquals("分组", group.title)
        assertTrue(group.supported)
        assertEquals(3, group.children.size)

        val sw = group.children[0] as SwitchNode
        assertEquals("sw1", sw.key)
        assertEquals("开关", sw.title)
        assertTrue(sw.checked)
        assertEquals("echo set", sw.setState)

        val picker = group.children[1] as PickerNode
        assertEquals("b", picker.value)
        assertEquals(2, picker.options!!.size)
        assertEquals("a", picker.options!![0].value)
        assertEquals("A", picker.options!![0].title)

        val action = group.children[2] as ActionNode
        assertEquals("echo run", action.setState)
        assertNull(action.params)

        val text = nodes[1] as TextNode
        assertEquals(1, text.rows.size)
        val row = text.rows[0]
        assertTrue(row.bold)
        assertEquals(-65536, row.color) // 0xFFFF0000

        val page = nodes[2] as PageNode
        assertEquals("子页", page.title)
        assertEquals("file:///android_asset/kr-script/sub.xml", page.pageConfigPath)
    }

    @Test
    fun `visible attr filters nodes via shell evaluation`() {
        val xml = """
            <kr-script>
                <action title="可见" visible="echo 1"><script>a</script></action>
                <action title="隐藏" visible="echo 0"><script>b</script></action>
            </kr-script>
        """.trimIndent()
        val evaluator = FakeEvaluator(mapOf("echo 1" to "1", "echo 0" to "0"))
        val nodes = reader(xml, evaluator).readConfigXml()

        assertEquals(1, nodes!!.size)
        assertEquals("可见", (nodes[0] as ActionNode).title)
        assertTrue(evaluator.evaluated.containsAll(listOf("echo 1", "echo 0")))
    }

    @Test
    fun `unsupported groups skip their children`() {
        val xml = """
            <kr-script>
                <group title="隐藏组" visible="echo 0">
                    <action title="内部动作"><script>x</script></action>
                </group>
                <group title="显示组">
                    <action title="外部动作"><script>y</script></action>
                </group>
            </kr-script>
        """.trimIndent()
        val evaluator = FakeEvaluator(mapOf("echo 0" to "0"))
        val nodes = reader(xml, evaluator).readConfigXml()

        assertEquals(1, nodes!!.size)
        val group = nodes[0] as GroupNode
        assertEquals("显示组", group.title)
        assertEquals(1, group.children.size)
    }

    @Test
    fun `parses action params of every type`() {
        val xml = """
            <kr-script>
                <action title="参数动作">
                    <param name="msg" type="text" title="文本" value="hi" maxlength="10" required="true" />
                    <param name="mode" type="select" title="单选">
                        <option val="fast">快速</option>
                    </param>
                    <param name="tags" type="multiple" multiple="1" separator=",">
                        <option val="x">X</option>
                        <option val="y">Y</option>
                    </param>
                    <param name="on" type="switch" value="1" />
                    <param name="level" type="seekbar" min="0" max="50" value="25" />
                    <param name="color" type="color" value="#00FF00" />
                    <param name="doc" type="file" suffix="zip" editable="true" />
                    <param name="hidden" type="text" visible="echo 0" />
                    <script>echo done</script>
                </action>
            </kr-script>
        """.trimIndent()
        val evaluator = FakeEvaluator(mapOf("echo 0" to "0"))
        val nodes = reader(xml, evaluator).readConfigXml()

        val action = nodes!![0] as ActionNode
        val params = action.params!!
        assertEquals(7, params.size) // the invisible param is dropped

        val text = params[0]
        assertEquals("msg", text.name)
        assertEquals("text", text.type)
        assertEquals("hi", text.value)
        assertEquals(10, text.maxLength)
        assertTrue(text.required)

        val select = params[1]
        assertEquals("select", select.type)
        assertEquals("fast", select.options!![0].value)
        assertEquals("快速", select.options!![0].title)

        val multiple = params[2]
        assertTrue(multiple.multiple)
        assertEquals(",", multiple.separator)
        assertEquals(2, multiple.options!!.size)

        val sw = params[3]
        assertEquals("switch", sw.type)
        assertEquals("1", sw.value)

        val seekbar = params[4]
        assertEquals(0, seekbar.min)
        assertEquals(50, seekbar.max)
        assertEquals("25", seekbar.value)

        val color = params[5]
        assertEquals("color", color.type)
        assertEquals("#00FF00", color.value)

        val file = params[6]
        assertEquals("file", file.type)
        assertEquals("zip", file.suffix)
        assertEquals("application/zip", file.mime)
        assertTrue(file.editable)
    }

    @Test
    fun `parses page menu options and handlers`() {
        val xml = """
            <kr-script>
                <page title="带菜单的页面" handler-sh="echo handler">
                    <menu-item type="refresh">刷新</menu-item>
                    <menu type="file" style="fab">选择文件</menu>
                    <config>sub/page.xml</config>
                </page>
            </kr-script>
        """.trimIndent()
        val nodes = reader(xml, FakeEvaluator()).readConfigXml()

        val page = nodes!![0] as PageNode
        assertEquals("sub/page.xml", page.pageConfigPath)
        assertEquals("echo handler", page.pageHandlerSh)
        assertNotNull(page.pageMenuOptions)
        assertEquals(2, page.pageMenuOptions!!.size)

        val refresh = page.pageMenuOptions!![0]
        assertEquals("refresh", refresh.type)
        assertFalse(refresh.isFab)
        assertEquals("刷新", refresh.key) // key defaults to title

        val fab = page.pageMenuOptions!![1]
        assertTrue(fab.isFab)
        assertEquals("file", fab.type)
    }

    @Test
    fun `root level page element is skipped like the original`() {
        val xml = "<page title=\"根级页面\"><config>a.xml</config></page>"
        val nodes = reader(xml, FakeEvaluator()).readConfigXml()
        assertEquals(0, nodes!!.size)
    }

    @Test
    fun `desc-sh evaluates at parse time`() {
        val xml = """
            <kr-script>
                <action title="t" desc-sh="echo 动态描述"><script>x</script></action>
            </kr-script>
        """.trimIndent()
        val evaluator = FakeEvaluator(mapOf("echo 动态描述" to "动态描述"))
        val nodes = reader(xml, evaluator).readConfigXml()
        assertEquals("动态描述", (nodes!![0] as ActionNode).desc)
    }

    @Test
    fun `bg-task attribute selects background mode`() {
        val xml = """
            <kr-script>
                <action title="后台任务" bg-task="true"><script>sleep 5</script></action>
            </kr-script>
        """.trimIndent()
        val nodes = reader(xml, FakeEvaluator()).readConfigXml()
        val action = nodes!![0] as ActionNode
        assertEquals(com.projectkr.krscript.core.model.RunnableNode.shellModeBgTask, action.shell)
    }
}
