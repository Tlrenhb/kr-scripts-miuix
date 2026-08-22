// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.runtime

/**
 * Read/write access to the application private directory (implemented by the app
 * layer over `context.filesDir`; faked in tests). Paths are relative to that dir.
 */
interface PrivateFileStore {
    /** Absolute path of the private root dir (no trailing slash). */
    fun privateDir(): String

    /** Writes [bytes] to [relPath], creating parent dirs; returns success. */
    fun writePrivateFile(relPath: String, bytes: ByteArray): Boolean

    /** Absolute path for a relative [relPath]. */
    fun privateFilePath(relPath: String): String

    fun exists(relPath: String): Boolean

    /** Marks [relPath] executable; default is a no-op success for fakes. */
    fun setExecutable(relPath: String): Boolean = true
}
