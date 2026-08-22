// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.model

/**
 * A menu option (`<option>`/`<menu-item>`) declared inside a `<page>`.
 *
 * [type] may be empty for plain script options, or `finish` / `refresh` / `file` for
 * built-in behaviors; `type=file` uses [mime]/[suffix] as the picker filter.
 */
class PageMenuOption(currentConfigXml: String) : RunnableNode(currentConfigXml) {
    var type: String = ""
    var isFab: Boolean = false

    var mime: String = ""
    var suffix: String = ""
}
