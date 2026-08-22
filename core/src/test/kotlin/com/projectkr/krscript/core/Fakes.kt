// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core

import com.projectkr.krscript.core.runtime.AssetSource
import com.projectkr.krscript.core.runtime.PrivateFileStore
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/** In-memory asset source backed by a path→content map. */
class FakeAssets(private val files: MutableMap<String, ByteArray> = LinkedHashMap()) : AssetSource {

    fun add(path: String, content: String) {
        files[path] = content.toByteArray()
    }

    override fun open(path: String): InputStream? =
        files[path]?.let { ByteArrayInputStream(it) }

    override fun list(dir: String): List<String>? {
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        val children = files.keys
            .filter { it.startsWith(prefix) && it.length > prefix.length }
            .map { it.substring(prefix.length).split("/")[0] }
            .toSet()
        return if (files.keys.any { it.startsWith(prefix) }) children.toList() else null
    }
}

/** In-memory private file store backed by a temp directory. */
class FakeFiles(private val root: File) : PrivateFileStore {

    override fun privateDir(): String = root.absolutePath

    override fun writePrivateFile(relPath: String, bytes: ByteArray): Boolean {
        return try {
            val f = File(root, relPath)
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
            true
        } catch (ex: Exception) {
            false
        }
    }

    override fun privateFilePath(relPath: String): String = File(root, relPath).absolutePath

    override fun exists(relPath: String): Boolean = File(root, relPath).exists()

    override fun setExecutable(relPath: String): Boolean =
        File(root, relPath).setExecutable(true)
}

/** Script evaluator returning canned results, recording evaluated scripts. */
class FakeEvaluator(private val answers: Map<String, String> = emptyMap()) :
    com.projectkr.krscript.core.config.ScriptEvaluator {

    val evaluated = mutableListOf<String>()

    override fun evaluate(script: String?, node: com.projectkr.krscript.core.model.NodeInfoBase?): String {
        evaluated.add(script ?: "")
        return answers[script] ?: ""
    }
}

/** Shell runner returning canned results, recording executed commands. */
class FakeShell(private val answers: (String) -> String = { "" }) :
    com.projectkr.krscript.core.runtime.ShellRunner {

    val commands = mutableListOf<String>()

    override fun execute(cmd: String): String {
        commands.add(cmd)
        return answers(cmd)
    }
}
