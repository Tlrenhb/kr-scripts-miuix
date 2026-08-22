// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.model

/**
 * `<group>` node: groups child nodes under a titled section.
 */
class GroupNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath) {
    var supported: Boolean = true
    val children: ArrayList<NodeInfoBase> = ArrayList()
}
