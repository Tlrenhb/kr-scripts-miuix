// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.model

/**
 * `<text>` node: rich text rows rendered as formatted lines.
 */
class TextNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath) {
    val rows = ArrayList<TextRow>()

    class TextRow {
        /** Text size in sp; -1 keeps the theme default. */
        var size: Int = -1

        /** ARGB color; -1 keeps the theme default. */
        var color: Int = -1
        var bgColor: Int = -1

        var bold: Boolean = false
        var italic: Boolean = false
        var underline: Boolean = false

        /** Start on a new row. */
        var breakRow: Boolean = false

        var align: Align = Align.NORMAL

        /** Web link opened on click. */
        var link: String = ""

        /** Android activity (component) opened on click. */
        var activity: String = ""

        var text: String = ""

        /** Script that produces the displayed text dynamically. */
        var dynamicTextSh: String = ""

        /** Script executed on click. */
        var onClickScript: String = ""
    }

    enum class Align { NORMAL, LEFT, CENTER, RIGHT }
}
