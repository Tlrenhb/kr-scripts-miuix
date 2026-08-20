// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.model

open class ClickableNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath) {
    var iconPath: String = ""
    var logoPath: String = ""
    var allowShortcut: Boolean? = null
    var locked: Boolean = false
    var lockShell: String = ""
    var targetSdkVersion: Int = 0
    var minSdkVersion: Int = 0
    var maxSdkVersion: Int = 100
}
