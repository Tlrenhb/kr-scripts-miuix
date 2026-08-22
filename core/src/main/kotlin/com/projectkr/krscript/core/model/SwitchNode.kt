// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.model

/**
 * `<switch>` node: two-state control bound to get/set scripts.
 */
class SwitchNode(currentConfigXml: String) : RunnableNode(currentConfigXml) {
    var getState: String = ""
    var checked: Boolean = false
}
