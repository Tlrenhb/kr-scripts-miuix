// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.model

import java.io.File
import java.util.UUID

/**
 * Base of every KrScript XML node.
 *
 * @param currentPageConfigPath absolute path (or asset ref) of the config this node was parsed from
 */
open class NodeInfoBase(val currentPageConfigPath: String) {

    /** Directory of [currentPageConfigPath]; asset refs keep the `file:///android_asset/` prefix. */
    val pageConfigDir: String = run {
        if (currentPageConfigPath.isEmpty()) {
            ""
        } else {
            val dir = File(currentPageConfigPath).parent ?: ""
            if (dir.startsWith("file:/android_asset/")) {
                "file:///android_asset/" + dir.substring("file:/android_asset/".length)
            } else {
                dir
            }
        }
    }

    /** Unique identity, required for shortcuts. */
    var key: String = ""

    /** Auto generated index. */
    val index: String = UUID.randomUUID().toString()

    var title: String = ""
    var desc: String = ""
    var descSh: String = ""
    var summary: String = ""
    var summarySh: String = ""
}
