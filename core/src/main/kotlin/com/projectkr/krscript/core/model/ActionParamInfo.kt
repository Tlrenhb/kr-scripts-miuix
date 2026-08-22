// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.model

/**
 * A `<param>` of an `<action>`: one input collected in the params dialog.
 *
 * Supported [type]s: `text` (default), `select`, `multiple`, `switch`, `seekbar`,
 * `color`, `file`. Unknown types fall back to text.
 */
class ActionParamInfo {
    /** Parameter name; must be unique within the action. */
    var name: String? = null

    var title: String? = null
    var label: String? = null
    var desc: String? = null

    var value: String? = null
    var valueShell: String? = null
    var valueFromShell: String? = null

    /** Text input only. */
    var maxLength: Int = -1

    var type: String? = null

    /** Seekbar only. */
    var max: Int = Int.MAX_VALUE
    var min: Int = Int.MIN_VALUE

    var required: Boolean = false
    var readonly: Boolean = false

    var options: ArrayList<SelectItem>? = null
    var optionsFromShell: ArrayList<SelectItem>? = null
    var optionsSh: String = ""

    /** Allow multiple selection (options / multiple types). */
    var multiple: Boolean = false

    var supported: Boolean = true

    /** Text field placeholder. */
    var placeholder: String = ""

    /** File mime filter (type=file). */
    var mime: String = ""
    var suffix: String = ""

    /** Allow manual path entry for file params. */
    var editable: Boolean = false

    /** Separator joining multiple selected values. */
    var separator: String = "\n"
}
