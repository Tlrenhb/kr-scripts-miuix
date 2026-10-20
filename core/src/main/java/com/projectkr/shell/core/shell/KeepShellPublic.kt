// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.shell

/**
 * Port of `common/src/main/java/com/omarea/common/shell/KeepShellPublic.kt` @ b90f2cc.
 *
 * Named [KeepShell] instances plus the shared default/secondary instances.
 * [getDefaultInstance] picks the idle one (default unless it is busy and the
 * secondary is idle).
 */
object KeepShellPublic {
    private val keepShells = HashMap<String, KeepShell>()

    fun getInstance(key: String, rootMode: Boolean): KeepShell {
        synchronized(keepShells) {
            if (!keepShells.containsKey(key)) {
                keepShells[key] = KeepShell(rootMode)
            }
            return keepShells[key]!!
        }
    }

    fun destroyInstance(key: String) {
        synchronized(keepShells) {
            if (!keepShells.containsKey(key)) {
                return
            } else {
                val keepShell = keepShells[key]!!
                keepShells.remove(key)
                keepShell.tryExit()
            }
        }
    }

    fun destroyAll() {
        synchronized(keepShells) {
            while (keepShells.isNotEmpty()) {
                val key = keepShells.keys.first()
                val keepShell = keepShells[key]!!
                keepShells.remove(key)
                keepShell.tryExit()
            }
        }
    }

    val defaultKeepShell = KeepShell()
    val secondaryKeepShell = KeepShell()

    fun getDefaultInstance(): KeepShell {
        return if (defaultKeepShell.isIdle || !secondaryKeepShell.isIdle) {
            defaultKeepShell
        } else {
            secondaryKeepShell
        }
    }

    fun doCmdSync(commands: List<String>): Boolean {
        val stringBuilder = StringBuilder()

        for (cmd in commands) {
            stringBuilder.append(cmd)
            stringBuilder.append("\n\n")
        }

        return doCmdSync(stringBuilder.toString()) != "error"
    }

    /** 执行脚本 */
    fun doCmdSync(cmd: String): String {
        return getDefaultInstance().doCmdSync(cmd)
    }

    /** 检查是否已经获得ROOT权限 */
    fun checkRoot(): Boolean {
        return defaultKeepShell.checkRoot()
    }

    fun tryExit() {
        defaultKeepShell.tryExit()
        secondaryKeepShell.tryExit()
    }
}
