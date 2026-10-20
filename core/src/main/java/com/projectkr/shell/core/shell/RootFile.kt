// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.shell

/**
 * Port of the shell-based predicates from
 * `common/src/main/java/com/omarea/common/shell/RootFile.kt` @ b90f2cc.
 *
 * Only the existence/identity checks consumed by the execution layer
 * ([MagiskExtend.moduleInstalled] etc.) are ported; the original's `ls`-parsing
 * file-browser API is scene UI functionality and intentionally omitted.
 */
object RootFile {
    fun itemExists(path: String): Boolean {
        return KeepShellPublic.doCmdSync("if [[ -e \"$path\" ]]; then echo 1; fi;") == "1"
    }

    fun fileExists(path: String): Boolean {
        return KeepShellPublic.doCmdSync("if [[ -f \"$path\" ]]; then echo 1; fi;") == "1"
    }

    fun fileNotEmpty(path: String): Boolean {
        return KeepShellPublic.doCmdSync("if [[ -f \"$path\" ]] && [[ -s \"$path\" ]]; then echo 1; fi;") == "1"
    }

    fun dirExists(path: String): Boolean {
        return KeepShellPublic.doCmdSync("if [[ -d \"$path\" ]]; then echo 1; fi;") == "1"
    }

    fun deleteDirOrFile(path: String) {
        KeepShellPublic.doCmdSync("rm -rf \"$path\"")
    }

    // 通过MD5比对两个文件是否相同
    fun fileEquals(path1: String, path2: String): Boolean {
        if (path1 == path2) {
            return true
        }
        return KeepShellPublic.doCmdSync("if [[ -f \"$path1\" ]] && [[ -f \"$path2\" ]]; then\nif [[ `md5sum -b \"$path1\"` = `md5sum -b \"$path2\"` ]]; then\n echo 1\nfi\nfi") == "1"
    }
}
