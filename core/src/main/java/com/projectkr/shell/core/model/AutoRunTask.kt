// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.model

interface AutoRunTask {
    fun onCompleted(result: Boolean?)
    val key: String?
}
