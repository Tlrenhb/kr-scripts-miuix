// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.model

class PageMenuOption(currentConfigXml: String) : RunnableNode(currentConfigXml) {
    var type: String = ""
    var isFab: Boolean = false
    var mime: String = ""
    var suffix: String = ""
}
