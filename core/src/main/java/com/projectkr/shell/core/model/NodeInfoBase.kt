// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.model

import java.io.File
import java.io.Serializable
import java.util.UUID

open class NodeInfoBase(open val currentPageConfigPath: String) : Serializable {
    val pageConfigDir: String = if (currentPageConfigPath.isNotEmpty()) {
        val dir = File(currentPageConfigPath).parent ?: ""
        if (dir.startsWith("file:/android_asset/")) {
            "file:///android_asset/" + dir.substring("file:/android_asset/".length)
        } else {
            dir
        }
    } else {
        ""
    }

    var key: String = ""
    val index: String = UUID.randomUUID().toString()
    var title: String = ""
    var desc: String = ""
    var descSh: String = ""
    var summary: String = ""
    var summarySh: String = ""
}
