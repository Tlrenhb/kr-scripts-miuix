// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.config

import android.content.Context
import android.os.Process
import android.os.UserManager

/**
 * 移植自 b90f2cc `krscript/.../FileOwner.java`。
 *
 * - [getUserId]：当前用户的 serial number（如 `0`），即环境变量 `ANDROID_UID`
 * - [getFileOwner]：`u<serial>_a<(uid % 100000) - 10000>`（如 `u0_a123`），即 `APP_USER_ID`，
 *   脚本里 `chown $APP_USER_ID` 依赖该格式
 */
class FileOwner(private val context: Context) {

    fun getUserId(): Int {
        val um = context.getSystemService(Context.USER_SERVICE) as UserManager
        val userHandle = Process.myUserHandle()

        return try {
            um.getSerialNumberForUser(userHandle).toInt()
        } catch (ignored: Exception) {
            0
        }
    }

    fun getFileOwner(): String {
        val androidUid = getUserId()
        // 100000 => UserHandle.PER_USER_RANGE
        return "u" + androidUid + "_a" + ((Process.myUid() % 100000) - Process.FIRST_APPLICATION_UID)
    }
}
