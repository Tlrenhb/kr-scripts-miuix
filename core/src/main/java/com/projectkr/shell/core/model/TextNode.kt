// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.model

import android.text.Layout

class TextNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath) {
    val rows = ArrayList<TextRow>()

    class TextRow {
        var size: Int = -1
        var color: Int = -1
        var bgColor: Int = -1
        var bold: Boolean = false
        var italic: Boolean = false
        var underline: Boolean = false
        var breakRow: Boolean = false
        var align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
        var link: String = ""
        var activity: String = ""
        var text: String = ""
        var dynamicTextSh: String = ""
        var onClickScript: String = ""
    }
}
