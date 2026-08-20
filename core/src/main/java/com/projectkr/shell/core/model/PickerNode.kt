// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.model

class PickerNode(currentPageConfigXml: String) : RunnableNode(currentPageConfigXml) {
    var options: ArrayList<SelectItem>? = null
    var optionsSh: String = ""
    var value: String? = null
    var getState: String? = null
    var name: String = ""
    var multiple: Boolean = false
    var separator: String = "\n"
}
