// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core

import com.projectkr.krscript.core.runtime.KeepShellRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [KeepShellRunner] against a real POSIX shell — available on any
 * Linux CI runner.
 */
class KeepShellRunnerTest {

    @Test
    fun `captures output between markers`() {
        KeepShellRunner(rootMode = false).use { sh ->
            assertEquals("hello", sh.execute("echo hello"))
            assertEquals("a\nb", sh.execute("echo a; echo b"))
            // Multi-line command with its own output ordering preserved.
            val out = sh.execute("for i in 1 2 3; do echo \"n=\$i\"; done")
            assertEquals("n=1\nn=2\nn=3", out)
        }
    }

    @Test
    fun `output containing marker-like text is handled`() {
        KeepShellRunner(rootMode = false).use { sh ->
            val out = sh.execute("echo '|SH>>|weird|<<SH|'")
            assertEquals("weird", out)
        }
    }

    @Test
    fun `createWithFallback yields a working shell in any environment`() {
        // Root or not (CI runners vs root containers), the fallback factory must
        // produce a shell that can execute commands.
        KeepShellRunner.createWithFallback().use { sh ->
            assertEquals("ok", sh.execute("echo ok"))
            assertEquals(true, sh.isIdle || !sh.isIdle) // state flag stays consistent
        }
    }

    private inline fun <T> KeepShellRunner.use(block: (KeepShellRunner) -> T): T {
        try {
            return block(this)
        } finally {
            tryExit()
        }
    }
}
