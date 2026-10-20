// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.config

import android.content.Context
import com.projectkr.shell.core.model.PageNode
import java.nio.charset.Charset

/**
 * `kr-script.conf` 解析（移植自 b90f2cc `pio/.../KrScriptConfig.java`）。
 *
 * 与原版一致的约定：
 * - 配置文件**只从 assets 读取**（原版没有 SAF/DocumentFile 查找逻辑——已核对原版源码）；
 *   文件名默认 [KR_SCRIPT_CONFIG_FILE]（原版经 `R.string.kr_script_config` 间接配置，
 *   core 模块没有资源，故改为常量 + 可选参数）。
 * - 每行格式 `key="value"`；`#` 开头或不含 `=` 的行跳过；在**第一个** `=` 处分割；
 *   value = `rowText.substring(separator + 2, rowText.length - 1).trim()` —— 盲目去掉
 *   `=` 之后一个字符与行尾一个字符（即引号剥离）。因此：行尾注释会污染 value；不带引号
 *   的 value 会被截断；畸形行抛出异常并**中断剩余行的解析**（外层 catch 整体吞掉，
 *   已解析的 key 保留）。
 * - 配置表是进程级静态的，整个进程只解析一次（[init] 幂等）；先写入 6 个默认值，
 *   再由配置文件覆盖（`page_list_config_sh` / `favorite_config_sh` 无默认值，不出现则不注入）。
 * - 未知 key 保留在 map 中，[getVariables] 全量暴露 —— 原版用它把全部 key 作为环境变量
 *   注入 `before_start_sh` 进程。
 */
class KrScriptConfig {

    companion object {
        private const val ASSETS_FILE = "file:///android_asset/"

        /** 原版 `R.string.kr_script_config` 的值 */
        const val KR_SCRIPT_CONFIG_FILE = "kr-script.conf"

        const val TOOLKIT_DIR = "toolkit_dir"
        const val TOOLKIT_DIR_DEFAULT = "file:///android_asset/kr-script/toolkit"

        const val EXECUTOR_CORE = "executor_core"
        const val EXECUTOR_CORE_DEFAULT = "file:///android_asset/kr-script/executor.sh"

        const val PAGE_LIST_CONFIG = "page_list_config"
        const val PAGE_LIST_CONFIG_DEFAULT = "file:///android_asset/kr-script/pages/more.xml"
        const val PAGE_LIST_CONFIG_SH = "page_list_config_sh"

        const val FAVORITE_CONFIG = "favorite_config"
        const val FAVORITE_CONFIG_DEFAULT = "file:///android_asset/kr-script/pages/favorites.xml"
        const val FAVORITE_CONFIG_SH = "favorite_config_sh"

        const val ALLOW_HOME_PAGE = "allow_home_page"
        const val ALLOW_HOME_PAGE_DEFAULT = "1"

        const val BEFORE_START_SH = "before_start_sh"
        const val BEFORE_START_SH_DEFAULT = ""

        // 原版为 static HashMap：整个进程只解析一次
        @Volatile
        private var configInfo: HashMap<String, String>? = null
    }

    /**
     * 读取 assets 中的 kr-script.conf 并合并进默认配置（仅首次调用生效）。
     *
     * 与原版差异：原版 init 末尾调用 `ScriptEnvironmen.init(context, executorCore, toolkitDir)`
     * 完成 executor 占位符替换与 toolkit 抽取；重写版的 `core.executor.ScriptEnvironment`
     * 为懒加载且不归本模块契约管辖，这里只通过 [getExecutorCore]/[getToolkitDir] 暴露配置，
     * 由上层负责接线。
     */
    fun init(context: Context, configFileName: String = KR_SCRIPT_CONFIG_FILE): KrScriptConfig {
        if (configInfo == null) {
            val config = HashMap<String, String>()
            config[EXECUTOR_CORE] = EXECUTOR_CORE_DEFAULT
            config[PAGE_LIST_CONFIG] = PAGE_LIST_CONFIG_DEFAULT
            config[FAVORITE_CONFIG] = FAVORITE_CONFIG_DEFAULT
            config[ALLOW_HOME_PAGE] = ALLOW_HOME_PAGE_DEFAULT
            config[TOOLKIT_DIR] = TOOLKIT_DIR_DEFAULT
            config[BEFORE_START_SH] = BEFORE_START_SH_DEFAULT
            // 先发布再解析：与原版一致，解析中途异常时已解析的 key 仍然保留
            configInfo = config

            try {
                var fileName = configFileName
                if (fileName.startsWith(ASSETS_FILE)) {
                    fileName = fileName.substring(ASSETS_FILE.length)
                }
                val inputStream = context.assets.open(fileName)
                val bytes = ByteArray(inputStream.available())
                inputStream.read(bytes)
                val rows = String(bytes, Charset.defaultCharset()).split("\n")
                for (row in rows) {
                    val rowText = row.trim()
                    if (!rowText.startsWith("#") && rowText.contains("=")) {
                        val separator = rowText.indexOf("=")
                        val key = rowText.substring(0, separator).trim()
                        // 原版 quirk：盲目剥离 `=` 后一个字符与行尾一个字符（即引号）；
                        // 非法行抛出 StringIndexOutOfBoundsException，中断剩余行的解析
                        val value = rowText.substring(separator + 2, rowText.length - 1).trim()
                        config.remove(key)
                        config[key] = value
                    }
                }
            } catch (ex: Exception) {
                // 原版：配置文件缺失/解析失败一律吞掉，保留默认值
            }
        }

        return this
    }

    /** 全量配置（含未知 key）；原版返回 live map，调用方可遍历注入环境变量 */
    fun getVariables(): HashMap<String, String>? {
        return configInfo
    }

    /** 原版为 private；重写版公开以便上层把 executor 路径接入 ScriptEnvironment */
    fun getExecutorCore(): String {
        val config = configInfo
        if (config != null && config.containsKey(EXECUTOR_CORE)) {
            return config[EXECUTOR_CORE] ?: EXECUTOR_CORE_DEFAULT
        }
        return EXECUTOR_CORE_DEFAULT
    }

    /** 原版为 private；重写版公开以便上层把 toolkit 目录接入资源抽取 */
    fun getToolkitDir(): String {
        val config = configInfo
        if (config != null && config.containsKey(TOOLKIT_DIR)) {
            return config[TOOLKIT_DIR] ?: TOOLKIT_DIR_DEFAULT
        }
        return TOOLKIT_DIR_DEFAULT
    }

    /** 全部（pages）列表配置；sh 优先、静态路径兜底由调用方按 PageConfigSh 协议处理 */
    fun getPageListConfig(): PageNode? {
        val config = configInfo ?: return null
        val pageInfo = PageNode("")
        if (config.containsKey(PAGE_LIST_CONFIG_SH)) {
            pageInfo.pageConfigSh = config[PAGE_LIST_CONFIG_SH] ?: ""
        }
        if (config.containsKey(PAGE_LIST_CONFIG)) {
            pageInfo.pageConfigPath = config[PAGE_LIST_CONFIG] ?: ""
        }
        return pageInfo
    }

    /** 收藏夹列表配置（同上） */
    fun getFavoriteConfig(): PageNode? {
        val config = configInfo ?: return null
        val pageInfo = PageNode("")
        if (config.containsKey(FAVORITE_CONFIG_SH)) {
            pageInfo.pageConfigSh = config[FAVORITE_CONFIG_SH] ?: ""
        }
        if (config.containsKey(FAVORITE_CONFIG)) {
            pageInfo.pageConfigPath = config[FAVORITE_CONFIG] ?: ""
        }
        return pageInfo
    }

    fun getAllowHomePage(): Boolean {
        val config = configInfo
        if (config != null && config.containsKey(ALLOW_HOME_PAGE)) {
            return config[ALLOW_HOME_PAGE] == "1"
        }
        return ALLOW_HOME_PAGE_DEFAULT == "1"
    }

    fun getBeforeStartSh(): String {
        val config = configInfo
        if (config != null && config.containsKey(BEFORE_START_SH)) {
            return config[BEFORE_START_SH] ?: BEFORE_START_SH_DEFAULT
        }
        return BEFORE_START_SH_DEFAULT
    }
}
