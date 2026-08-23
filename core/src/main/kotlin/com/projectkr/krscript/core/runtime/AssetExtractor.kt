// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.runtime

/**
 * Extracts assets into the private dir so scripts can read them from disk.
 * Mirrors the original `ExtractAssets` semantics, including the extraction cache.
 */
interface AssetExtractor {
    /** Extracts a single asset file; returns its absolute private path. */
    fun extractResource(fileName: String): String?

    /** Recursively extracts an asset directory; returns its absolute private path. */
    fun extractResources(dir: String): String

    /** Maps an asset ref (or relative path) to its absolute extracted path. */
    fun getExtractPath(file: String): String

    companion object {
        const val ASSETS_PREFIX = "file:///android_asset/"

        fun stripAssetPrefix(path: String): String =
            if (path.startsWith(ASSETS_PREFIX)) path.substring(ASSETS_PREFIX.length) else path
    }
}

class DefaultAssetExtractor(
    private val assets: AssetSource,
    private val files: PrivateFileStore,
) : AssetExtractor {

    private val history = HashMap<String, String>()

    override fun extractResource(fileName: String): String? {
        if (fileName.isEmpty()) return null
        history[fileName]?.let { return it }

        val rel = AssetExtractor.stripAssetPrefix(fileName)
        var bytes = try {
            assets.open(rel)?.use { it.readBytes() } ?: return null
        } catch (ex: Exception) {
            return null
        }
        // Original FileWrite.writePrivateShellFile normalized DOS endings for
        // every extracted script (parseText: \r\n→\n, \r\t→\t).
        if (rel.endsWith(".sh")) {
            val text = bytes.toString(Charsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("\r\t", "\t")
                .replace("\r", "\n")
            bytes = text.toByteArray(Charsets.UTF_8)
        }
        return if (files.writePrivateFile(rel, bytes)) {
            files.setExecutable(rel)
            val path = files.privateFilePath(rel)
            history[fileName] = path
            path
        } else {
            null
        }
    }

    override fun extractResources(dir: String): String {
        if (dir.isEmpty()) return ""
        history[dir]?.let { return it }

        var rel = if (dir.startsWith(AssetExtractor.ASSETS_PREFIX)) {
            dir.substring(AssetExtractor.ASSETS_PREFIX.length)
        } else if (dir.endsWith("/")) {
            dir.substring(0, dir.length - 1)
        } else {
            dir
        }

        val entries = try {
            assets.list(rel)
        } catch (ex: Exception) {
            null
        }
        if (entries.isNullOrEmpty()) {
            return extractResource(rel) ?: ""
        }
        for (entry in entries) {
            extractResources("$rel/$entry")
        }
        val outputDir = getExtractPath(rel)
        history[dir] = outputDir
        return outputDir
    }

    override fun getExtractPath(file: String): String =
        files.privateFilePath(AssetExtractor.stripAssetPrefix(file))
}
