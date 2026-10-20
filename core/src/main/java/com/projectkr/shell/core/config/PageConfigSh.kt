// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.config

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.projectkr.shell.core.model.NodeInfoBase
import com.projectkr.shell.core.model.PageNode
import java.io.ByteArrayInputStream

/**
 * 通过脚本枚举页面列表（移植自 b90f2cc `krscript/.../config/PageConfigSh.kt`）。
 *
 * 协议（§1.12）：以 root 执行 [pageConfigSh] 并 trim 输出：
 * - 以 `.xml` 结尾 → 当作配置文件路径，用 [PageConfigReader] 读取（读到 null →
 *   toast “指定的配置文件不存在或无读取权限”，即原 `kr_page_sh_file_permission`）
 * - 以 `<?xml` 开头且以 `>` 结尾 → 当作内联 XML 直接解析
 * - 其它非空输出 → toast “无效的页面配置输出”（原 `kr_page_sh_invalid`）
 * - 空输出 → 返回 null，由调用方回退到静态 `pageConfigPath`
 *
 * 与原版差异：构造参数由 `Activity` 放宽为 `Context`（Toast 不依赖 Activity）；
 * Toast 文案硬编码为原版中文字符串（core 模块没有资源）；脚本执行经
 * [NodeScriptExecutor]（PAGE_CONFIG_* 环境变量注入 + asset 脚本抽取，语义同原版
 * `ScriptEnvironmen.executeResultRoot`）。
 */
class PageConfigSh(
    private val context: Context,
    private val shellRunner: ShellRunner,
    private val pageConfigSh: String,
    private val parentConfig: PageNode?,
) {
    private val handler = Handler(Looper.getMainLooper())

    private fun pageConfigShError(content: String) {
        handler.post {
            Toast.makeText(context, "无效的页面配置输出\n$content", Toast.LENGTH_LONG).show()
        }
    }

    private fun noReadPermission() {
        handler.post {
            Toast.makeText(context, "指定的配置文件不存在或无读取权限", Toast.LENGTH_LONG).show()
        }
    }

    fun execute(): ArrayList<NodeInfoBase>? {
        var items: ArrayList<NodeInfoBase>? = null

        val result = NodeScriptExecutor(context, shellRunner).execute(pageConfigSh, parentConfig).trim()
        if (result.endsWith(".xml")) {
            items = PageConfigReader(context, shellRunner)
                .readConfigXml(result, parentConfig?.pageConfigDir)
            if (items == null) {
                noReadPermission()
            }
        } else if (result.startsWith("<?xml") && result.endsWith(">")) {
            val inputStream = ByteArrayInputStream(result.toByteArray())
            items = PageConfigReader(context, shellRunner).readConfigXml(inputStream)
        } else if (result.isNotEmpty()) {
            pageConfigShError(result)
        }
        return items
    }
}
