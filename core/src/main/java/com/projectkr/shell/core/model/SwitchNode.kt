// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.model

class SwitchNode(currentPageConfigXml: String) : RunnableNode(currentPageConfigXml) {
    var getState: String = ""
    var checked: Boolean = false
}
