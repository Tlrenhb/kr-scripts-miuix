// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core

import com.projectkr.krscript.core.exec.ScriptEnvironment
import com.projectkr.krscript.core.model.NodeInfoBase
import com.projectkr.krscript.core.runtime.DefaultAssetExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ScriptEnvironmentTest {

    private val executorSh = """
        #!/system/bin/sh
        EXECUTOR="$({EXECUTOR_PATH})"
        TOOLKIT="$({TOOLKIT})"
        echo "$1"
    """.trimIndent()

    private fun env(
        shell: FakeShell,
    ): Triple<ScriptEnvironment, FakeAssets, FakeFiles> {
        val assets = FakeAssets().apply { add("kr-script/executor.sh", executorSh) }
        val files = FakeFiles(Files.createTempDirectory("krenv").toFile())
        val extractor = DefaultAssetExtractor(assets, files)
        return Triple(ScriptEnvironment(shell, assets, files, extractor), assets, files)
    }

    @Test
    fun `init substitutes variables and writes the executor`() {
        val shell = FakeShell()
        val (env, _, files) = env(shell)

        assertTrue(
            env.init(
                "file:///android_asset/kr-script/executor.sh",
                null,
                mapOf("TOOLKIT" to "/data/toolkit"),
            ),
        )
        val written = File(files.privateFilePath("kr-script/executor.sh")).readText()
        assertFalse(written.contains("\$({EXECUTOR_PATH})"))
        assertFalse(written.contains("\$({TOOLKIT})"))
        assertTrue(written.contains(files.privateFilePath("kr-script/executor.sh")))
        assertTrue(written.contains("/data/toolkit"))
    }

    @Test
    fun `executeResult builds the page context exports`() {
        val shell = FakeShell { "" }
        val (env, _, _) = env(shell)
        env.init("file:///android_asset/kr-script/executor.sh", null, emptyMap())

        val node = NodeInfoBase("file:///android_asset/kr-script/sample.xml")
        env.executeResult("echo hi", node)

        assertEquals(1, shell.commands.size)
        val cmd = shell.commands[0]
        assertTrue(cmd.contains("export PAGE_CONFIG_DIR='file:///android_asset/kr-script'"))
        assertTrue(cmd.contains("export PAGE_CONFIG_FILE='file:///android_asset/kr-script/sample.xml'"))
        // Asset-based pages export the extracted on-disk work paths.
        assertTrue(cmd.contains("export PAGE_WORK_DIR='"))
        assertTrue(cmd.contains("export PAGE_WORK_FILE='"))
        // The script is cached to a file and passed quoted after the executor path.
        assertTrue(cmd.trimEnd().endsWith("\""))
        assertTrue(cmd.contains("echo hi") || cmd.contains("cache"))
    }

    @Test
    fun `inline scripts are cached by md5`() {
        val shell = FakeShell { "" }
        val (env, _, files) = env(shell)
        env.init("file:///android_asset/kr-script/executor.sh", null, emptyMap())

        env.executeResult("echo cached", NodeInfoBase(""))
        val cacheDir = File(files.privateFilePath("kr-script/cache"))
        assertTrue(cacheDir.isDirectory)
        assertEquals(1, cacheDir.listFiles()!!.size)
        val content = cacheDir.listFiles()!![0].readText()
        assertTrue(content.startsWith("#!/system/bin/sh"))
        assertTrue(content.contains("echo cached"))

        // Second run reuses the same cache file.
        env.executeResult("echo cached", NodeInfoBase(""))
        assertEquals(1, cacheDir.listFiles()!!.size)
    }

    @Test
    fun `empty scripts short-circuit without touching the shell`() {
        val shell = FakeShell()
        val (env, _, _) = env(shell)
        env.init("file:///android_asset/kr-script/executor.sh", null, emptyMap())
        assertEquals("", env.executeResult("", NodeInfoBase("")))
        assertEquals("", env.executeResult(null, NodeInfoBase("")))
        assertEquals(0, shell.commands.size)
    }

    @Test
    fun `uninited environment returns empty output`() {
        val shell = FakeShell()
        val (env, _, _) = env(shell)
        assertEquals("", env.executeResult("echo x", null))
        assertEquals(0, shell.commands.size)
    }
}
