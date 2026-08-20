// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.model

class SelectItem {
    var title: String? = null
    var value: String? = null
    var selected: Boolean = false

    override fun toString(): String {
        return when {
            !title.isNullOrEmpty() -> title!!
            !value.isNullOrEmpty() -> value!!
            else -> ""
        }
    }
}
