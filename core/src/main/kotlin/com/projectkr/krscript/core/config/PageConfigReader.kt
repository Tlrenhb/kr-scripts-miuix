// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.config

import com.projectkr.krscript.core.model.ActionNode
import com.projectkr.krscript.core.model.ActionParamInfo
import com.projectkr.krscript.core.model.ClickableNode
import com.projectkr.krscript.core.model.GroupNode
import com.projectkr.krscript.core.model.NodeInfoBase
import com.projectkr.krscript.core.model.PageMenuOption
import com.projectkr.krscript.core.model.PageNode
import com.projectkr.krscript.core.model.PickerNode
import com.projectkr.krscript.core.model.RunnableNode
import com.projectkr.krscript.core.model.SelectItem
import com.projectkr.krscript.core.model.SwitchNode
import com.projectkr.krscript.core.model.TextNode
import com.projectkr.krscript.core.runtime.AssetExtractor
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Parses a KrScript page config (XML) into the node list — a faithful port of the
 * original `PageConfigReader` semantics, decoupled from Android.
 *
 * Shell evaluation (`visible`, `desc-sh`, switch/picker state…) goes through
 * [evaluator]; asset extraction (`<resource>`) through [extractor]; config lookup
 * through [locator].
 */
class PageConfigReader(
    private val evaluator: ScriptEvaluator,
    private val locator: PathAnalysis,
    private val extractor: AssetExtractor,
    private val pageConfigRef: String = "",
    parentDir: String = "",
    private val sourceStream: InputStream? = null,
) {

    // Resolved when reading from a path ref; empty for direct streams.
    private var pageConfigAbsPath: String = ""
    private var virtualRoot: NodeInfoBase? = null

    private var actionParamInfos: ArrayList<ActionParamInfo>? = null
    private var actionParamInfo: ActionParamInfo? = null

    constructor(
        evaluator: ScriptEvaluator,
        locator: PathAnalysis,
        extractor: AssetExtractor,
        stream: InputStream,
    ) : this(evaluator, locator, extractor, pageConfigRef = "", parentDir = "", sourceStream = stream)

    /**
     * Parses the config. Returns the node list, an empty list when the config file
     * cannot be located, or null on a parse failure.
     */
    fun readConfigXml(): MutableList<NodeInfoBase>? {
        sourceStream?.let { return readConfigXml(it) }
        return try {
            val located = locator.parsePath(pageConfigRef) ?: return ArrayList()
            pageConfigAbsPath = locator.getCurrentAbsPath()
            readConfigXml(located.stream)
        } catch (ex: Exception) {
            null
        }
    }

    private fun evaluate(script: String?): String {
        if (virtualRoot == null) {
            virtualRoot = NodeInfoBase(pageConfigAbsPath)
        }
        return evaluator.evaluate(script, virtualRoot)
    }

    private fun readConfigXml(stream: InputStream): MutableList<NodeInfoBase>? {
        return try {
            val parser = newParser()
            parser.setInput(stream, "utf-8")
            parse(parser)
        } catch (ex: Exception) {
            null
        } finally {
            try { stream.close() } catch (_: Exception) {}
        }
    }

    private fun newParser(): XmlPullParser {
        val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newPullParser()
    }

    private fun parse(parser: XmlPullParser): MutableList<NodeInfoBase> {
        val mainList = ArrayList<NodeInfoBase>()
        var action: ActionNode? = null
        var switch: SwitchNode? = null
        var picker: PickerNode? = null
        var group: GroupNode? = null
        var page: PageNode? = null
        var text: TextNode? = null
        var isRootNode = true

        var type = parser.eventType
        while (type != XmlPullParser.END_DOCUMENT) {
            when (type) {
                XmlPullParser.START_TAG -> {
                    if ("group" == parser.name) {
                        if (group != null && group.supported) mainList.add(group)
                        group = groupNode(parser)
                    } else if (group != null && !group.supported) {
                        // Skip everything inside an unsupported group.
                    } else {
                        when {
                            "page" == parser.name -> {
                                if (!isRootNode) {
                                    page = clickbleNode(PageNode(pageConfigAbsPath), parser)
                                        as PageNode?
                                    if (page != null) page = pageNode(page, parser)
                                }
                            }
                            "action" == parser.name ->
                                action = runnableNode(ActionNode(pageConfigAbsPath), parser)
                                    as ActionNode?
                            "switch" == parser.name ->
                                switch = runnableNode(SwitchNode(pageConfigAbsPath), parser)
                                    as SwitchNode?
                            "picker" == parser.name -> {
                                picker = runnableNode(PickerNode(pageConfigAbsPath), parser)
                                    as PickerNode?
                                if (picker != null) pickerAttrs(picker, parser)
                            }
                            "text" == parser.name ->
                                text = mainNode(TextNode(pageConfigAbsPath), parser)
                                    as TextNode?
                            page != null -> tagStartInPage(page, parser)
                            action != null -> tagStartInAction(action, parser)
                            switch != null -> tagStartInSwitch(switch, parser)
                            picker != null -> tagStartInPicker(picker, parser)
                            text != null -> tagStartInText(text, parser)
                        }
                    }
                    isRootNode = false
                }

                XmlPullParser.END_TAG -> {
                    if ("group" == parser.name) {
                        if (group != null && group.supported) mainList.add(group)
                        group = null
                    } else if (group != null) {
                        when (parser.name) {
                            "page" -> { tagEndInPage(page); page?.let { group.children.add(it) }; page = null }
                            "action" -> { tagEndInAction(action); action?.let { group.children.add(it) }; action = null }
                            "switch" -> { tagEndInSwitch(switch); switch?.let { group.children.add(it) }; switch = null }
                            "picker" -> { tagEndInPicker(picker); picker?.let { group.children.add(it) }; picker = null }
                            "text" -> { text?.let { group.children.add(it) }; text = null }
                        }
                    } else {
                        when (parser.name) {
                            "page" -> { tagEndInPage(page); page?.let { mainList.add(it) }; page = null }
                            "action" -> { tagEndInAction(action); action?.let { mainList.add(it) }; action = null }
                            "switch" -> { tagEndInSwitch(switch); switch?.let { mainList.add(it) }; switch = null }
                            "picker" -> { tagEndInPicker(picker); picker?.let { mainList.add(it) }; picker = null }
                            "text" -> { text?.let { mainList.add(it) }; text = null }
                        }
                    }
                }
            }
            type = parser.next()
        }
        return mainList
    }

    // region shared attribute processors (same layering as the original)

    private fun attr(name: String): Boolean =
        name == "support" || name == "visible"

    private fun mainNode(nodeInfoBase: NodeInfoBase, parser: XmlPullParser): NodeInfoBase? {
        for (i in 0 until parser.attributeCount) {
            val attrValue = parser.getAttributeValue(i)
            when (parser.getAttributeName(i)) {
                "key", "index", "id" -> nodeInfoBase.key = attrValue.trim()
                "title" -> nodeInfoBase.title = attrValue
                "desc" -> nodeInfoBase.desc = attrValue
                "summary" -> nodeInfoBase.summary = attrValue
                "desc-sh" -> {
                    nodeInfoBase.descSh = attrValue
                    nodeInfoBase.desc = evaluate(attrValue)
                }
                "summary-sh" -> {
                    nodeInfoBase.summarySh = attrValue
                    nodeInfoBase.summary = evaluate(attrValue)
                }
                else -> {
                    if (attr(parser.getAttributeName(i)) && evaluate(attrValue) != "1") {
                        return null
                    }
                }
            }
        }
        return nodeInfoBase
    }

    private fun clickbleNode(clickableNode: ClickableNode, parser: XmlPullParser): ClickableNode? {
        val node = mainNode(clickableNode, parser) as ClickableNode? ?: return null
        for (i in 0 until parser.attributeCount) {
            val attrValue = parser.getAttributeValue(i)
            when (parser.getAttributeName(i)) {
                "lock", "lock-state", "locked" ->
                    node.locked = attrValue == "1" || attrValue == "true" || attrValue == "locked"
                "min-sdk", "sdk-min" -> node.minSdkVersion = attrValue.trim().toInt()
                "max-sdk", "sdk-max" -> node.maxSdkVersion = attrValue.trim().toInt()
                "target-sdk", "sdk-target" -> node.targetSdkVersion = attrValue.trim().toInt()
                "icon", "icon-path" -> node.iconPath = attrValue.trim()
                "logo", "logo-path" -> node.logoPath = attrValue.trim()
                "allow-shortcut" ->
                    node.allowShortcut = attrValue == "allow" ||
                        attrValue == "allow-shortcut" ||
                        attrValue == "true" || attrValue == "1"
            }
        }
        if (node.key.isNotEmpty() && node.key.startsWith("@") && node.allowShortcut == null) {
            node.allowShortcut = false
        }
        return node
    }

    private fun runnableNode(node: RunnableNode, parser: XmlPullParser): RunnableNode? {
        val clickable = clickbleNode(node, parser) as RunnableNode? ?: return null
        for (i in 0 until parser.attributeCount) {
            val attrValue = parser.getAttributeValue(i)
            when (parser.getAttributeName(i)) {
                "confirm" ->
                    clickable.confirm = attrValue == "confirm" || attrValue == "true" || attrValue == "1"
                "warn", "warning" -> clickable.warning = attrValue
                "auto-off", "auto-close" ->
                    clickable.autoOff = attrValue == "auto-close" ||
                        attrValue == "auto-off" || attrValue == "true" || attrValue == "1"
                "auto-finish" ->
                    clickable.autoFinish = attrValue == "auto-finish" ||
                        attrValue == "true" || attrValue == "1"
                "interruptible", "interruptable" ->
                    clickable.interruptable = attrValue.isEmpty() ||
                        attrValue == "interruptable" || attrValue == "interruptible" ||
                        attrValue == "true" || attrValue == "1"
                "reload-page" ->
                    if (attrValue == "reload-page" || attrValue == "reload" ||
                        attrValue == "page" || attrValue == "true" || attrValue == "1"
                    ) clickable.reloadPage = true
                "reload" -> {
                    if (attrValue == "reload-page" || attrValue == "reload" ||
                        attrValue == "page" || attrValue == "true" || attrValue == "1"
                    ) {
                        clickable.reloadPage = true
                    } else if (attrValue.isNotEmpty()) {
                        clickable.updateBlocks = attrValue.split(",")
                            .map { it.trim() }
                            .dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    }
                }
                "shell" -> clickable.shell = attrValue
                "bg-task", "background-task", "async-task" ->
                    if (attrValue == "async-task" || attrValue == "async" ||
                        attrValue == "bg-task" || attrValue == "background" ||
                        attrValue == "background-task" || attrValue == "true" || attrValue == "1"
                    ) clickable.shell = RunnableNode.shellModeBgTask
            }
        }
        return clickable
    }

    // endregion

    // region per-type tags

    private fun descNode(nodeInfoBase: NodeInfoBase, parser: XmlPullParser) {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName == "su" || attrName == "sh" || attrName == "desc-sh") {
                nodeInfoBase.descSh = parser.getAttributeValue(i)
                nodeInfoBase.desc = evaluate(nodeInfoBase.descSh)
            }
        }
        if (nodeInfoBase.desc.isEmpty()) nodeInfoBase.desc = parser.nextText()
    }

    private fun summaryNode(nodeInfoBase: NodeInfoBase, parser: XmlPullParser) {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName == "su" || attrName == "sh" || attrName == "summary-sh") {
                nodeInfoBase.summarySh = parser.getAttributeValue(i)
                nodeInfoBase.summary = evaluate(nodeInfoBase.summarySh)
            }
        }
        if (nodeInfoBase.summary.isEmpty()) nodeInfoBase.summary = parser.nextText()
    }

    private fun resourceNode(parser: XmlPullParser) {
        for (i in 0 until parser.attributeCount) {
            when (parser.getAttributeName(i)) {
                "file" -> extractor.extractResource(parser.getAttributeValue(i).trim())
                "dir" -> extractor.extractResources(parser.getAttributeValue(i).trim())
            }
        }
    }

    private fun groupNode(parser: XmlPullParser): GroupNode {
        val group = GroupNode(pageConfigAbsPath)
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            val attrValue = parser.getAttributeValue(i)
            when (attrName) {
                "key", "index", "id" -> group.key = attrValue.trim()
                "title" -> group.title = attrValue
                "support", "visible" -> group.supported = evaluate(attrValue) == "1"
            }
        }
        return group
    }

    private fun pageNode(page: PageNode, parser: XmlPullParser): PageNode {
        for (attrIndex in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(attrIndex)
            val attrValue = parser.getAttributeValue(attrIndex)
            when (attrName) {
                "config" -> page.pageConfigPath = attrValue
                "html" -> page.onlineHtmlPage = attrValue
                "before-load", "before-read" -> page.beforeRead = attrValue
                "after-load", "after-read" -> page.afterRead = attrValue
                "load-ok", "load-success" -> page.loadSuccess = attrValue
                "load-fail", "load-error" -> page.loadFail = attrValue
                "config-sh" -> page.pageConfigSh = attrValue
                "link", "href" -> page.link = attrValue
                "activity", "a", "intent" -> page.activity = attrValue
                "option-sh", "option-su", "options-sh" -> page.pageMenuOptionsSh = attrValue
                "handler-sh", "handler", "set", "getstate", "script" -> page.pageHandlerSh = attrValue
            }
        }
        return page
    }

    private fun tagStartInPage(node: PageNode, parser: XmlPullParser) {
        when (parser.name) {
            "title" -> node.title = parser.nextText()
            "desc" -> descNode(node, parser)
            "summary" -> summaryNode(node, parser)
            "resource" -> resourceNode(parser)
            "html" -> node.onlineHtmlPage = parser.nextText()
            "config" -> node.pageConfigPath = parser.nextText()
            "handler-sh", "handler", "set", "getstate", "script" -> node.pageHandlerSh = parser.nextText()
            "lock", "lock-state" -> node.lockShell = parser.nextText()
            "option", "page-option", "menu", "menu-item" -> {
                val option = runnableNode(PageMenuOption(pageConfigAbsPath), parser)
                    as PageMenuOption? ?: return
                for (i in 0 until parser.attributeCount) {
                    when (parser.getAttributeName(i)) {
                        "type" -> option.type = parser.getAttributeValue(i)
                        "style" -> option.isFab = parser.getAttributeValue(i) == "fab"
                        "suffix" -> {
                            val suffix = parser.getAttributeValue(i).lowercase().trim()
                            if (option.mime.isEmpty()) option.mime = Suffix2Mime().toMime(suffix)
                            option.suffix = suffix
                        }
                        "mime" -> option.mime = parser.getAttributeValue(i).lowercase()
                    }
                }
                option.title = parser.nextText()
                if (option.key.isEmpty()) option.key = option.title
                if (node.pageMenuOptions == null) node.pageMenuOptions = ArrayList()
                node.pageMenuOptions!!.add(option)
            }
        }
    }

    private fun tagStartInAction(action: ActionNode, parser: XmlPullParser) {
        if ("title" == parser.name) {
            action.title = parser.nextText()
        } else if ("desc" == parser.name) {
            descNode(action, parser)
        } else if ("summary" == parser.name) {
            summaryNode(action, parser)
        } else if ("script" == parser.name || "set" == parser.name || "setstate" == parser.name) {
            action.setState = parser.nextText().trim()
        } else if ("lock" == parser.name || "lock-state" == parser.name) {
            action.lockShell = parser.nextText()
        } else if ("param" == parser.name) {
            if (actionParamInfos == null) actionParamInfos = ArrayList()
            val info = ActionParamInfo()
            actionParamInfo = info
            for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                val attrValue = parser.getAttributeValue(i)
                when {
                    attrName == "name" -> info.name = attrValue
                    attrName == "label" -> info.label = attrValue
                    attrName == "placeholder" -> info.placeholder = attrValue
                    attrName == "title" -> info.title = attrValue
                    attrName == "desc" -> info.desc = attrValue
                    attrName == "value" -> info.value = attrValue
                    attrName == "type" -> info.type = attrValue.lowercase().trim()
                    attrName == "suffix" -> {
                        val suffix = attrValue.lowercase().trim()
                        if (info.mime.isEmpty()) info.mime = Suffix2Mime().toMime(suffix)
                        info.suffix = suffix
                    }
                    attrName == "mime" -> info.mime = attrValue.lowercase()
                    attrName == "readonly" -> {
                        val v = attrValue.lowercase().trim()
                        info.readonly = v == "readonly" || v == "true" || v == "1"
                    }
                    attrName == "maxlength" -> info.maxLength = attrValue.toInt()
                    attrName == "min" -> info.min = attrValue.toInt()
                    attrName == "max" -> info.max = attrValue.toInt()
                    attrName == "required" ->
                        info.required = attrValue == "true" || attrValue == "1" || attrValue == "required"
                    attrName == "value-sh" || attrName == "value-su" -> info.valueShell = attrValue
                    attrName == "options-sh" || attrName == "option-sh" || attrName == "options-su" -> {
                        if (info.options == null) info.options = ArrayList()
                        info.optionsSh = attrValue
                    }
                    attrName == "multiple" ->
                        info.multiple = attrValue == "multiple" || attrValue == "true" || attrValue == "1"
                    attrName == "editable" ->
                        info.editable = attrValue == "editable" || attrValue == "true" || attrValue == "1"
                    attrName == "separator" -> info.separator = attrValue
                    attr(attrName) -> if (evaluate(attrValue) != "1") info.supported = false
                }
            }
            if (info.supported && !info.name.isNullOrEmpty()) {
                actionParamInfos!!.add(info)
            }
        } else if (actionParamInfo != null && "option" == parser.name) {
            val info = actionParamInfo!!
            if (info.options == null) info.options = ArrayList()
            val option = SelectItem()
            for (i in 0 until parser.attributeCount) {
                if (parser.getAttributeName(i) == "val" || parser.getAttributeName(i) == "value") {
                    option.value = parser.getAttributeValue(i)
                }
            }
            option.title = parser.nextText()
            if (option.value == null) option.value = option.title
            info.options!!.add(option)
        } else if ("resource" == parser.name) {
            resourceNode(parser)
        }
    }

    private fun tagEndInAction(action: ActionNode?) {
        if (action != null) {
            if (action.setState == null) action.setState = ""
            action.params = actionParamInfos
            actionParamInfos = null
        }
    }

    private fun tagStartInSwitch(switchNode: SwitchNode, parser: XmlPullParser) {
        when (parser.name) {
            "title" -> switchNode.title = parser.nextText()
            "desc" -> descNode(switchNode, parser)
            "summary" -> summaryNode(switchNode, parser)
            "get", "getstate" -> switchNode.getState = parser.nextText()
            "set", "setstate" -> switchNode.setState = parser.nextText()
            "resource" -> resourceNode(parser)
            "lock", "lock-state" -> switchNode.lockShell = parser.nextText()
        }
    }

    private fun tagEndInSwitch(switchNode: SwitchNode?) {
        if (switchNode != null) {
            val shellResult = evaluate(switchNode.getState)
            switchNode.checked = shellResult != "error" &&
                (shellResult == "1" || shellResult.lowercase() == "true")
            if (switchNode.setState == null) switchNode.setState = ""
        }
    }

    private fun pickerAttrs(pickerNode: PickerNode, parser: XmlPullParser) {
        for (attrIndex in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(attrIndex)
            val attrValue = parser.getAttributeValue(attrIndex)
            when (attrName) {
                "option-sh", "options-sh", "options-su" -> {
                    if (pickerNode.options == null) pickerNode.options = ArrayList()
                    pickerNode.optionsSh = attrValue
                }
                "multiple" ->
                    pickerNode.multiple = attrValue == "multiple" || attrValue == "true" || attrValue == "1"
                "separator" -> pickerNode.separator = attrValue
            }
        }
    }

    private fun tagStartInPicker(pickerNode: PickerNode, parser: XmlPullParser) {
        when (parser.name) {
            "title" -> pickerNode.title = parser.nextText()
            "desc" -> descNode(pickerNode, parser)
            "summary" -> summaryNode(pickerNode, parser)
            "option" -> {
                if (pickerNode.options == null) pickerNode.options = ArrayList()
                val option = SelectItem()
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == "val" || parser.getAttributeName(i) == "value") {
                        option.value = parser.getAttributeValue(i)
                    }
                }
                option.title = parser.nextText()
                if (option.value == null) option.value = option.title
                pickerNode.options!!.add(option)
            }
            "getstate", "get" -> pickerNode.getState = parser.nextText()
            "setstate", "set" -> pickerNode.setState = parser.nextText()
            "resource" -> resourceNode(parser)
            "lock", "lock-state" -> pickerNode.lockShell = parser.nextText()
        }
    }

    private fun tagEndInPicker(pickerNode: PickerNode?) {
        if (pickerNode != null) {
            pickerNode.value = if (pickerNode.getState == null) "" else evaluate(pickerNode.getState)
            if (pickerNode.setState == null) pickerNode.setState = ""
        }
    }

    private fun tagStartInText(textNode: TextNode, parser: XmlPullParser) {
        when (parser.name) {
            "title" -> textNode.title = parser.nextText()
            "desc" -> descNode(textNode, parser)
            "summary" -> summaryNode(textNode, parser)
            "slice" -> rowNode(textNode, parser)
            "resource" -> resourceNode(parser)
        }
    }

    private fun rowNode(textNode: TextNode, parser: XmlPullParser) {
        val row = TextNode.TextRow()
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i).lowercase()
            val attrValue = parser.getAttributeValue(i)
            try {
                when (attrName) {
                    "bold", "b" -> row.bold = attrValue == "1" || attrValue == "true" || attrValue == "bold"
                    "italic", "i" -> row.italic = attrValue == "1" || attrValue == "true" || attrValue == "italic"
                    "underline", "u" -> row.underline = attrValue == "1" || attrValue == "true" || attrValue == "underline"
                    "foreground", "color" -> row.color = parseColor(attrValue)
                    "bg", "background", "bgcolor" -> row.bgColor = parseColor(attrValue)
                    "size" -> row.size = attrValue.toInt()
                    "break" -> row.breakRow = attrValue == "1" || attrValue == "true" || attrValue == "break"
                    "link", "href" -> row.link = attrValue
                    "activity", "a", "intent" -> row.activity = attrValue
                    "script", "run" -> row.onClickScript = attrValue
                    "sh" -> row.dynamicTextSh = attrValue
                    "align" -> when (attrValue) {
                        "left" -> row.align = TextNode.Align.LEFT
                        "right" -> row.align = TextNode.Align.RIGHT
                        "center" -> row.align = TextNode.Align.CENTER
                        "normal" -> row.align = TextNode.Align.NORMAL
                    }
                }
            } catch (_: Exception) {
                // Invalid attribute values are ignored, matching the original.
            }
        }
        row.text = parser.nextText()
        textNode.rows.add(row)
    }

    private fun tagEndInPage(page: PageNode?) {
        // Nothing to finalize; kept for structural parity with the original.
    }

    // endregion

    companion object {
        const val ASSETS_PREFIX = PathAnalysis.ASSETS_FILE

        /**
         * Parses `#RGB`, `#ARGB`, `#RRGGBB` and `#AARRGGBB` into an ARGB int.
         * Throws [IllegalArgumentException] for anything else (named colors are not
         * supported and are ignored by callers, matching original behavior).
         */
        fun parseColor(colorString: String): Int {
            var s = colorString
            if (s[0] != '#') throw IllegalArgumentException("Unknown color: $colorString")
            s = s.substring(1)
            return when (s.length) {
                3 -> {
                    val r = Character.digit(s[0], 16)
                    val g = Character.digit(s[1], 16)
                    val b = Character.digit(s[2], 16)
                    if (r < 0 || g < 0 || b < 0) throw IllegalArgumentException("Unknown color: $colorString")
                    -0x1000000 or (r shl 20) or (r shl 16) or (g shl 12) or (g shl 8) or (b shl 4) or b
                }
                4 -> {
                    val a = Character.digit(s[0], 16)
                    val r = Character.digit(s[1], 16)
                    val g = Character.digit(s[2], 16)
                    val b = Character.digit(s[3], 16)
                    if (a < 0 || r < 0 || g < 0 || b < 0) throw IllegalArgumentException("Unknown color: $colorString")
                    (a shl 28) or (r shl 20) or (r shl 16) or (g shl 12) or (g shl 8) or (b shl 4) or b
                }
                6 -> {
                    val value = s.toLong(16)
                    -0x1000000 or value.toInt()
                }
                8 -> s.toLong(16).toInt()
                else -> throw IllegalArgumentException("Unknown color: $colorString")
            }
        }
    }
}
