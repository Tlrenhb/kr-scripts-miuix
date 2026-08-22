// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.model

/**
 * `<picker>` node: single (or multiple) selection list bound to get/set scripts.
 */
class PickerNode(currentConfigXml: String) : RunnableNode(currentConfigXml) {
    var options: ArrayList<SelectItem>? = null
    var optionsSh: String = ""
    var value: String? = null
    var getState: String? = null

    /** Parameter name used when the selection is passed to scripts. */
    var name: String = ""

    var multiple: Boolean = false
    var separator: String = "\n"
}
