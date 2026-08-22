// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.projectkr.krscript.core.model.PageNode

/**
 * In-memory registry handing rich [PageNode] data (menu options…) to pushed
 * routes. Survives navigation; lost on process death, in which case the route's
 * plain fields still render the page.
 */
object PageRegistry {
    private val pages = HashMap<String, PageNode>()

    fun put(page: PageNode): String {
        pages[page.index] = page
        return page.index
    }

    fun get(index: String): PageNode? = pages[index]
}

/** Result channel for the file selector route (miuix-nav v1 has no result API). */
object FilePickerResult {
    var pendingPath by androidx.compose.runtime.mutableStateOf<String?>(null)

    fun consume(): String? {
        val value = pendingPath
        pendingPath = null
        return value
    }
}
