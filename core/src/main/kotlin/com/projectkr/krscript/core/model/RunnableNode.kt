// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.model

/**
 * A node whose click runs a script (action / switch / picker / menu option).
 */
open class RunnableNode(currentConfigXml: String) : ClickableNode(currentConfigXml) {

    /** Ask for confirmation before running. */
    var confirm: Boolean = false

    /** Warning text shown with the confirmation. */
    var warning: String = ""

    /** Close the log view automatically when the script finishes. */
    var autoOff: Boolean = false

    /** Whether the user may interrupt the execution. */
    var interruptable: Boolean = true

    /** Reload the whole page after execution. */
    var reloadPage: Boolean = false

    /** Blocks (node keys) to refresh after execution. */
    var updateBlocks: Array<String>? = null

    /** Close the page automatically after execution. */
    var autoFinish: Boolean = false

    /** Interaction mode: [shellModeDefault], [shellModeBgTask] or [shellModeHidden]. */
    var shell: String = shellModeDefault

    companion object {
        const val shellModeDefault = "default"
        const val shellModeBgTask = "bg-task"
        const val shellModeHidden = "hidden"
    }

    /** The set/state script of this node. */
    var setState: String? = null
}
