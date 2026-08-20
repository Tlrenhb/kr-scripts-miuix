// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.model

class GroupNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath) {
    var supported: Boolean = true
    val children: ArrayList<NodeInfoBase> = ArrayList()
}
