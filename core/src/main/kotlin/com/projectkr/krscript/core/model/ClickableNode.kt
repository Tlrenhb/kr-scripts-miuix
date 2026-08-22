// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.model

/**
 * A node that can be clicked (page / action / switch / picker).
 */
open class ClickableNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath) {

    /** Icon shown in the list. */
    var iconPath: String = ""

    /** Icon used for launcher shortcuts. */
    var logoPath: String = ""

    /** Whether a shortcut may be created (allowed by default when [key] is set and not prefixed with "@"). */
    var allowShortcut: Boolean? = null

    var locked: Boolean = false

    /** Script that resolves the lock state at render time. */
    var lockShell: String = ""

    /** Android SDK version requirements; 0 means unconstrained. */
    var targetSdkVersion: Int = 0
    var minSdkVersion: Int = 0
    var maxSdkVersion: Int = 100
}
