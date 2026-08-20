// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.model

class ActionParamInfo {
    var name: String? = null
    var title: String? = null
    var label: String? = null
    var desc: String? = null
    var value: String? = null
    var valueShell: String? = null
    var valueFromShell: String? = null
    var maxLength: Int = -1
    var type: String? = null
    var max: Int = Int.MAX_VALUE
    var min: Int = Int.MIN_VALUE
    var required: Boolean = false
    var readonly: Boolean = false
    var options: ArrayList<SelectItem>? = null
    var optionsFromShell: ArrayList<SelectItem>? = null
    var optionsSh: String = ""
    var multiple: Boolean = false
    var supported: Boolean = true
    var placeholder: String = ""
    var mime: String = ""
    var suffix: String = ""
    var editable: Boolean = false
    var separator: String = "\n"
}
