// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.runtime

import java.io.InputStream

/**
 * Read-only view over the application assets (implemented by the app layer with
 * `AssetManager`; faked in tests).
 */
interface AssetSource {
    /** Opens [path] (relative inside assets), or null when missing. */
    fun open(path: String): InputStream?

    /** Lists entries of [dir]; null when missing. Names are simple file/dir names. */
    fun list(dir: String): List<String>?
}
