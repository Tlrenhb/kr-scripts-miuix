// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.config

/**
 * Parses `kr-script.conf` — lines of `key="value"`, `#` comment lines ignored.
 *
 * Values keep their raw inner text; surrounding double quotes are stripped when
 * present (the original parser assumed the quoted form).
 */
class ConfReader {

    fun parse(content: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (rawLine in content.split("\n")) {
            val line = rawLine.trim()
            if (line.startsWith("#") || !line.contains("=")) continue
            val separator = line.indexOf('=')
            val key = line.substring(0, separator).trim()
            var value = line.substring(separator + 1).trim()
            if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length - 1)
            }
            result[key] = value
        }
        return result
    }

    companion object {
        const val EXECUTOR_CORE = "executor_core"
        const val PAGE_LIST_CONFIG = "page_list_config"
        const val PAGE_LIST_CONFIG_SH = "page_list_config_sh"
        const val FAVORITE_CONFIG = "favorite_config"
        const val FAVORITE_CONFIG_SH = "favorite_config_sh"
        const val ALLOW_HOME_PAGE = "allow_home_page"
        const val TOOLKIT_DIR = "toolkit_dir"
        const val BEFORE_START_SH = "before_start_sh"

        const val EXECUTOR_CORE_DEFAULT = "file:///android_asset/kr-script/executor.sh"
        const val TOOLKIT_DIR_DEFAULT = "file:///android_asset/kr-script/toolkit"
        const val ALLOW_HOME_PAGE_DEFAULT = "1"
        const val BEFORE_START_SH_DEFAULT = ""
    }
}
