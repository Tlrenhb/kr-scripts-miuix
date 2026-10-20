// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.shell

/**
 * Port of the detection half of
 * `common/src/main/java/com/omarea/common/shared/MagiskExtend.java` @ b90f2cc.
 *
 * Only what the execution contract needs: [magiskSupported] / [moduleInstalled]
 * and the [MAGISK_PATH] resolution that becomes the `MAGISK_PATH` env var baked
 * into executor.sh. The module creation/patching APIs (`createFileReplaceModule`,
 * `magiskModuleInstall`, `replaceSystemFile`, …) are scene-specific features that
 * nothing in the kr-script framework consumes — intentionally omitted.
 */
object MagiskExtend {
    // source /data/adb/util_functions.sh
    var MAGISK_PATH = "/sbin/.core/img/scene_systemless/"
    private const val MAGISK_PATH_19 = "/data/adb/modules" //  "/sbin/.magisk/modules"
    private const val MAGISK_ROOT_PATH1 = "/sbin/.core/img"
    private const val MAGISK_ROOT_PATH2 = "/sbin/.magisk/img"

    private const val MAGISK_MODULE_NAME = "scene_systemless"

    // magisk 19 /data/adb/modules
    private var supported = -1
    private var magiskVersion = 0

    /**
     * 是否已经安装magisk并且版本合适
     *
     * @return 是否已安装
     */
    fun magiskSupported(): Boolean {
        if (supported == -1 || magiskVersion < 1) {
            val versionOutput = KeepShellPublic.doCmdSync("magisk -V")
            if (versionOutput != "error") {
                try {
                    magiskVersion = versionOutput.toInt() / 1000
                    supported = if (magiskVersion >= 17) 1 else 0

                    if (supported == 1) {
                        MAGISK_PATH = when {
                            magiskVersion >= 19 -> "$MAGISK_PATH_19/$MAGISK_MODULE_NAME/"
                            RootFile.dirExists(MAGISK_ROOT_PATH1) -> "$MAGISK_ROOT_PATH1/$MAGISK_MODULE_NAME/"
                            RootFile.dirExists(MAGISK_ROOT_PATH2) -> "$MAGISK_ROOT_PATH2/$MAGISK_MODULE_NAME/"
                            else -> MAGISK_PATH
                        }
                    }
                } catch (ignored: Exception) {
                }
            } else {
                supported = 0
            }
        }
        return supported == 1
    }

    /**
     * 是否已安装模块
     */
    fun moduleInstalled(): Boolean {
        return magiskSupported() && RootFile.fileExists(MAGISK_PATH + "module.prop")
    }
}
