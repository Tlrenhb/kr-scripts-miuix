// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core

import com.projectkr.krscript.core.exec.ScriptProcessRunner
import com.projectkr.krscript.core.exec.ScriptEnvironment
import com.projectkr.krscript.core.model.NodeInfoBase
import com.projectkr.krscript.core.runtime.DefaultAssetExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScriptProcessRunnerTest {

    private fun runner(): Pair<ScriptProcessRunner, FakeFiles> {
        val assets = FakeAssets().apply {
            add("kr-script/executor.sh", "#!/bin/sh\nsh \"\$1\"\n")
        }
        val files = FakeFiles(Files.createTempDirectory("krproc").toFile())
        val extractor = DefaultAssetExtractor(assets, files)
        val env = ScriptEnvironment(FakeShell(), assets, files, extractor)
        env.init("file:///android_asset/kr-script/executor.sh", null, emptyMap())
        return Pair(ScriptProcessRunner(env, extractor, rootMode = false), files)
    }

    @Test
    fun `streams stdout and exits`() {
        val (runner, _) = runner()
        val lines = mutableListOf<String>()
        val done = CountDownLatch(1)
        var exitCode = -99

        val process = runner.execute(
            script = "echo line1; echo err-on-stderr 1>&2; echo line2",
            node = NodeInfoBase(""),
            params = mapOf("state" to "1"),
            tag = "test_session_1",
            onLine = { line, isErr -> lines.add((if (isErr) "E:" else "") + line) },
            onExit = { code -> exitCode = code; done.countDown() },
        )
        assertNotNull(process)
        assertTrue(done.await(30, TimeUnit.SECONDS))
        assertTrue(lines.contains("line1"))
        assertTrue(lines.contains("line2"))
        assertTrue(lines.contains("E:err-on-stderr"))
        assertEquals(0, exitCode)
    }

    @Test
    fun `params are exported as environment variables`() {
        val (runner, _) = runner()
        val lines = mutableListOf<String>()
        val done = CountDownLatch(1)

        runner.execute(
            script = "echo \"value=\$MY_VALUE\"",
            node = NodeInfoBase(""),
            params = mapOf("MY_VALUE" to "hello'world"),
            tag = "test_session_2",
            onLine = { line, _ -> lines.add(line) },
            onExit = { done.countDown() },
        )
        assertTrue(done.await(30, TimeUnit.SECONDS))
        // The single quote in the value survives the export escaping.
        assertTrue(lines.contains("value=hello'world"))
    }

    @Test
    fun `page context vars reach the script`() {
        val (runner, _) = runner()
        val lines = mutableListOf<String>()
        val done = CountDownLatch(1)

        runner.execute(
            script = "echo \"file=\$PAGE_CONFIG_FILE\"",
            node = NodeInfoBase("/data/kr/page.xml"),
            params = emptyMap(),
            tag = "test_session_3",
            onLine = { line, _ -> lines.add(line) },
            onExit = { done.countDown() },
        )
        assertTrue(done.await(30, TimeUnit.SECONDS))
        assertTrue(lines.contains("file=/data/kr/page.xml"))
    }
}
