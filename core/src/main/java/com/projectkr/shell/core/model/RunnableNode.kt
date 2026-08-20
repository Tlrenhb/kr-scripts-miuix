// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.model

open class RunnableNode(currentPageConfigPath: String) : ClickableNode(currentPageConfigPath) {
    var confirm: Boolean = false
    var warning: String = ""
    var autoOff: Boolean = false
    var interruptable: Boolean = true
    var reloadPage: Boolean = false
    var updateBlocks: Array<String>? = null
    var autoFinish: Boolean = false
    var shell: String = shellModeDefault
    var setState: String? = null

    companion object {
        const val shellModeDefault = "default"
        const val shellModeBgTask = "bg-task"
        const val shellModeHidden = "hidden"
    }
}
