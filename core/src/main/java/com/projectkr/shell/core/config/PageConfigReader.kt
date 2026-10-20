package com.projectkr.shell.core.config

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.util.Log
import android.util.Xml
import android.widget.Toast
import com.projectkr.shell.core.model.SelectItem
import com.projectkr.shell.core.model.*
import org.xmlpull.v1.XmlPullParser
import com.projectkr.shell.core.config.ShellRunner
import com.projectkr.shell.core.executor.ExtractAssets
import java.io.InputStream

/**
 * Created by Hello on 2018/04/01.
 */
class PageConfigReader(
    private val context: Context,
    private val shellRunner: ShellRunner,
) {
    private var pageConfig: String = ""

    // 读取pageConfig时自动获得
    private var pageConfigAbsPath: String = ""
    private var pageConfigStream: InputStream? = null
    private var parentDir: String = ""

    fun readConfigXml(pageConfig: String, parentDir: String? = null): ArrayList<NodeInfoBase>? {
        this.pageConfig = pageConfig
        this.parentDir = parentDir ?: ""
        this.pageConfigStream = null
        return readConfigXml()
    }

    fun readConfigXml(pageConfigStream: InputStream): ArrayList<NodeInfoBase>? {
        this.pageConfigStream = pageConfigStream
        return readConfigXml()
    }

    private fun readConfigXml(): ArrayList<NodeInfoBase>? {
        if (pageConfigStream != null) {
            return parseConfigXml(pageConfigStream!!)
        } else {
            try {
                val pathAnalysis = PathAnalysis(context, parentDir, shellRunner)
                val fileInputStream = pathAnalysis.parsePath(pageConfig) ?: return ArrayList()
                pageConfigAbsPath = pathAnalysis.getCurrentAbsPath()
                return parseConfigXml(fileInputStream)
            } catch (ex: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "解析配置文件失败\n" + ex.message, Toast.LENGTH_LONG).show()
                }
                Log.e("KrConfig Fail！", "" + ex.message)
            }
        }
        return null
    }


    private fun parseConfigXml(fileInputStream: InputStream): ArrayList<NodeInfoBase>? {
        try {
            val parser = Xml.newPullParser()// 获取xml解析器
            parser.setInput(fileInputStream, "utf-8")// 参数分别为输入流和字符编码
            var type = parser.eventType
            val mainList: ArrayList<NodeInfoBase> = ArrayList()
            var action: ActionNode? = null
            var switch: SwitchNode? = null
            var picker: PickerNode? = null
            var group: GroupNode? = null
            var page: PageNode? = null
            var text: TextNode? = null
            var isRootNode = true
            while (type != XmlPullParser.END_DOCUMENT) { // 如果事件不等于文档结束事件就继续循环
                when (type) {
                    XmlPullParser.START_TAG -> {
                        if ("group" == parser.name) {
                            if (group != null && group.supported) {
                                mainList.add(group)
                            }
                            group = groupNode(parser)
                        } else if (group != null && !group.supported) {
                            // 如果 group.supported !- true 跳过group内所有项
                        } else {
                            if ("page" == parser.name) {
                                if (!isRootNode) {
                                    page = clickbleNode(PageNode(pageConfigAbsPath), parser) as PageNode?
                                    if (page != null) {
                                        page = pageNode(page, parser)
                                    }
                                }
                            } else if ("action" == parser.name) {
                                action = runnableNode(ActionNode(pageConfigAbsPath), parser) as ActionNode?
                            } else if ("switch" == parser.name) {
                                switch = runnableNode(SwitchNode(pageConfigAbsPath), parser) as SwitchNode?
                            } else if ("picker" == parser.name) {
                                picker = runnableNode(PickerNode(pageConfigAbsPath), parser) as PickerNode?
                                if (picker != null) {
                                    pickerNode(picker, parser)
                                }
                            } else if ("text" == parser.name) {
                                text = mainNode(TextNode(pageConfigAbsPath), parser) as TextNode?
                            } else if (page != null) {
                                tagStartInPage(page, parser)
                            } else if (action != null) {
                                tagStartInAction(action, parser)
                            } else if (switch != null) {
                                tagStartInSwitch(switch, parser)
                            } else if (picker != null) {
                                tagStartInPicker(picker, parser)
                            } else if (text != null) {
                                tagStartInText(text, parser)
                            } else if ("resource" == parser.name) {
                                resourceNode(parser)
                            }
                        }
                        isRootNode = false
                    }
                    XmlPullParser.END_TAG ->
                        if ("group" == parser.name) {
                            if (group != null && group.supported) {
                                mainList.add(group)
                            }
                            group = null
                        } else if (group != null) {
                            if ("page" == parser.name) {
                                tagEndInPage(page, parser)
                                if (page != null) {
                                    group.children.add(page)
                                }
                                page = null
                            } else if ("action" == parser.name) {
                                tagEndInAction(action, parser)
                                if (action != null) {
                                    group.children.add(action)
                                }
                                action = null
                            } else if ("switch" == parser.name) {
                                tagEndInSwitch(switch, parser)
                                if (switch != null) {
                                    group.children.add(switch)
                                }
                                switch = null
                            } else if ("picker" == parser.name) {
                                tagEndInPicker(picker, parser)
                                if (picker != null) {
                                    group.children.add(picker)
                                }
                                picker = null
                            } else if ("text" == parser.name) {
                                tagEndInText(text, parser)
                                if (text != null) {
                                    group.children.add(text)
                                }
                                text = null
                            }
                        } else {
                            if ("page" == parser.name) {
                                tagEndInPage(page, parser)
                                if (page != null) {
                                    mainList.add(page)
                                }
                                page = null
                            } else if ("action" == parser.name) {
                                tagEndInAction(action, parser)
                                if (action != null) {
                                    mainList.add(action)
                                }
                                action = null
                            } else if ("switch" == parser.name) {
                                tagEndInSwitch(switch, parser)
                                if (switch != null) {
                                    mainList.add(switch)
                                }
                                switch = null
                            } else if ("picker" == parser.name) {
                                tagEndInPicker(picker, parser)
                                if (picker != null) {
                                    mainList.add(picker)
                                }
                                picker = null
                            } else if ("text" == parser.name) {
                                tagEndInText(text, parser)
                                if (text != null) {
                                    mainList.add(text)
                                }
                                text = null
                            }
                        }
                }
                type = parser.next()// 继续下一个事件
            }

            return mainList
        } catch (ex: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "解析配置文件失败\n" + ex.message, Toast.LENGTH_LONG).show()
            }
            Log.e("KrConfig Fail！", "" + ex.message)
        }

        return null
    }

    private var actionParamInfos: ArrayList<ActionParamInfo>? = null
    var actionParamInfo: ActionParamInfo? = null
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
            if (actionParamInfos == null) {
                actionParamInfos = ArrayList()
            }
            actionParamInfo = ActionParamInfo()
            val actionParamInfo = actionParamInfo!!
            for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                val attrValue = parser.getAttributeValue(i)
                when {
                    attrName == "name" -> actionParamInfo.name = attrValue
                    attrName == "label" -> actionParamInfo.label = attrValue
                    attrName == "placeholder" -> actionParamInfo.placeholder = attrValue
                    attrName == "title" -> actionParamInfo.title = attrValue
                    attrName == "desc" -> actionParamInfo.desc = attrValue
                    attrName == "value" -> actionParamInfo.value = attrValue
                    attrName == "type" -> actionParamInfo.type = attrValue.lowercase().trim { it <= ' ' }
                    attrName == "suffix" -> {
                        val suffix = attrValue.lowercase().trim { it <= ' ' }

                        if (actionParamInfo.mime.isEmpty()) {
                            actionParamInfo.mime = Suffix2Mime().toMime(suffix)
                        }

                        actionParamInfo.suffix = suffix
                    }
                    attrName == "mime" -> {
                        actionParamInfo.mime = attrValue.lowercase()
                    }
                    attrName == "readonly" -> {
                        val value = attrValue.lowercase().trim { it <= ' ' }
                        actionParamInfo.readonly = (value == "readonly" || value == "true" || value == "1")
                    }
                    attrName == "maxlength" -> actionParamInfo.maxLength = Integer.parseInt(attrValue)
                    attrName == "min" -> actionParamInfo.min = Integer.parseInt(attrValue)
                    attrName == "max" -> actionParamInfo.max = Integer.parseInt(attrValue)
                    attrName == "required" -> actionParamInfo.required = attrValue == "true" || attrValue == "1" || attrValue == "required"
                    attrName == "value-sh" || attrName == "value-su" -> {
                        val script = attrValue
                        actionParamInfo.valueShell = script
                    }
                    attrName == "options-sh" || attrName == "option-sh" || attrName == "options-su" -> {
                        if (actionParamInfo.options == null)
                            actionParamInfo.options = ArrayList<SelectItem>()
                        val script = attrValue
                        actionParamInfo.optionsSh = script
                    }
                    attrName == "support" || attrName == "visible" -> {
                        if (executeResultRoot(context, attrValue) != "1") {
                            actionParamInfo.supported = false
                        }
                    }
                    attrName == "multiple" -> {
                        actionParamInfo.multiple = attrValue == "multiple" || attrValue == "true" || attrValue == "1"
                    }
                    attrName == "editable" -> {
                        actionParamInfo.editable = attrValue == "editable" || attrValue == "true" || attrValue == "1"
                    }
                    attrName == "separator" -> {
                        actionParamInfo.separator = attrValue
                    }
                }
            }
            if (actionParamInfo.supported && actionParamInfo.name != null && actionParamInfo.name!!.isNotEmpty()) {
                actionParamInfos!!.add(actionParamInfo)
            }
        } else if (actionParamInfo != null && "option" == parser.name) {
            val actionParamInfo = actionParamInfo!!
            if (actionParamInfo.options == null) {
                actionParamInfo.options = ArrayList()
            }
            val option = SelectItem()
            for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                if (attrName == "val" || attrName == "value") {
                    option.value = parser.getAttributeValue(i)
                }
            }
            option.title = parser.nextText()
            if (option.value == null)
                option.value = option.title
            actionParamInfo.options!!.add(option)
        } else if ("resource" == parser.name) {
            resourceNode(parser)
        }
    }

    private fun tagEndInPage(page: PageNode?, parser: XmlPullParser) {
    }

    private fun tagEndInAction(action: ActionNode?, parser: XmlPullParser) {
        if (action != null) {
            if (action.setState == null)
                action.setState = ""
            action.params = actionParamInfos

            actionParamInfos = null
        }
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
                val option = runnableNode(PageMenuOption(pageConfigAbsPath), parser) as PageMenuOption?
                if (option != null) {
                    for (i in 0 until parser.attributeCount) {
                        when (parser.getAttributeName(i)) {
                            "type" -> {
                                option.type = parser.getAttributeValue(i)
                            }
                            "style" -> {
                                option.isFab = parser.getAttributeValue(i) == "fab"
                            }
                            "suffix" -> {
                                val suffix = parser.getAttributeValue(i).lowercase().trim { it <= ' ' }

                                if (option.mime.isEmpty()) {
                                    option.mime = Suffix2Mime().toMime(suffix)
                                }

                                option.suffix = suffix
                            }
                            "mime" -> {
                                option.mime = parser.getAttributeValue(i).lowercase()
                            }
                        }
                    }
                    option.title = parser.nextText()
                    if (option.key.isEmpty()) {
                        option.key = option.title
                    }

                    if (node.pageMenuOptions == null) {
                        node.pageMenuOptions = ArrayList()
                    }
                    node.pageMenuOptions?.add(option)
                }
            }
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

    private fun groupNode(parser: XmlPullParser): GroupNode {
        val groupInfo = GroupNode(pageConfigAbsPath)
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            val attrValue = parser.getAttributeValue(i)
            when (attrName) {
                "key", "index", "id" -> groupInfo.key = attrValue.trim()
                "title" -> groupInfo.title = attrValue
                "support", "visible" -> groupInfo.supported = executeResultRoot(context, attrValue) == "1"
            }
        }
        return groupInfo
    }

    // 通常指 page、action、switch、picker这种，可以点击的节点
    private fun clickbleNode(clickableNode: ClickableNode, parser: XmlPullParser): ClickableNode? {
        return (mainNode(clickableNode, parser) as ClickableNode?)?.apply {
            for (i in 0 until parser.attributeCount) {
                val attrValue = parser.getAttributeValue(i)
                when (parser.getAttributeName(i)) {
                    "lock", "lock-state", "locked" -> locked = (attrValue == "1" || attrValue == "true" || attrValue == "locked")
                    "min-sdk", "sdk-min" -> minSdkVersion = attrValue.trim().toInt()
                    "max-sdk", "sdk-max" -> maxSdkVersion = attrValue.trim().toInt()
                    "target-sdk", "sdk-target" -> targetSdkVersion = attrValue.trim().toInt()
                    "icon", "icon-path" -> iconPath = attrValue.trim()
                    "logo", "logo-path" -> logoPath = attrValue.trim()
                    "allow-shortcut" -> allowShortcut = attrValue == "allow" || attrValue == "allow-shortcut" || attrValue == "true" || attrValue == "1"
                }
            }
            if (key.isNotEmpty() && key.startsWith("@") && allowShortcut == null) {
                allowShortcut = false
            }
        }
    }

    // 通常指 action、switch、picker这种，点击后需要执行脚本的节点
    private fun runnableNode(node: RunnableNode, parser: XmlPullParser): RunnableNode? {
        val clickableNode = clickbleNode(node, parser) as RunnableNode?
        if (clickableNode != null) {
            for (i in 0 until parser.attributeCount) {
                val attrValue = parser.getAttributeValue(i)
                when (parser.getAttributeName(i)) {
                    "confirm" -> clickableNode.confirm = (attrValue == "confirm" || attrValue == "true" || attrValue == "1")
                    "warn", "warning" -> {
                        clickableNode.warning = attrValue
                    }
                    "auto-off", "auto-close" -> clickableNode.autoOff = (attrValue == "auto-close" || attrValue == "auto-off" || attrValue == "true" || attrValue == "1")
                    "auto-finish" -> clickableNode.autoFinish = (attrValue == "auto-finish" || attrValue == "true" || attrValue == "1")
                    "interruptible", "interruptable" -> clickableNode.interruptable = (
                            attrValue.isEmpty() || attrValue == "interruptable" || attrValue == "interruptable" || attrValue == "true" || attrValue == "1")
                    "reload-page" -> {
                        if (attrValue == "reload-page" || attrValue == "reload" || attrValue == "page" || attrValue == "true" || attrValue == "1") {
                            clickableNode.reloadPage = true
                        }
                    }
                    "reload" -> {
                        if (attrValue == "reload-page" || attrValue == "reload" || attrValue == "page" || attrValue == "true" || attrValue == "1") {
                            clickableNode.reloadPage = true
                        } else if (attrValue.isNotEmpty()) {
                            clickableNode.updateBlocks = attrValue.split(",").map { it.trim() }.dropLastWhile { it.isEmpty() }.toTypedArray()
                        }
                    }
                    "shell" -> {
                        clickableNode.shell = attrValue
                    }
                    "bg-task", "background-task", "async-task" -> {
                        if (attrValue == "async-task" || attrValue == "async" || attrValue == "bg-task" || attrValue == "background" || attrValue == "background-task" || attrValue == "true" || attrValue == "1") {
                            clickableNode.shell = RunnableNode.shellModeBgTask
                        }
                    }
                }
            }
        }

        return clickableNode
    }

    private fun mainNode(nodeInfoBase: NodeInfoBase, parser: XmlPullParser): NodeInfoBase? {
        for (i in 0 until parser.attributeCount) {
            val attrValue = parser.getAttributeValue(i)
            when (parser.getAttributeName(i)) {
                "key", "index", "id" -> nodeInfoBase.key = attrValue.trim()
                "title" -> nodeInfoBase.title = attrValue
                "desc" -> nodeInfoBase.desc = attrValue
                "support", "visible" -> {
                    if (executeResultRoot(context, attrValue) != "1") {
                        return null
                    }
                }
                "desc-sh" -> {
                    nodeInfoBase.descSh = parser.getAttributeValue(i)
                    nodeInfoBase.desc = executeResultRoot(context, nodeInfoBase.descSh)
                }
                "summary" -> {
                    nodeInfoBase.summary = parser.getAttributeValue(i)
                }
                "summary-sh" -> {
                    nodeInfoBase.summarySh = parser.getAttributeValue(i)
                    nodeInfoBase.summary = executeResultRoot(context, nodeInfoBase.summarySh)
                }
            }
        }
        return nodeInfoBase
    }

    // TODO: 整理Title和Desc
    // TODO: 整理ReloadPage
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

    private fun pickerNode(pickerNode: PickerNode, parser: XmlPullParser) {
        for (attrIndex in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(attrIndex)
            val attrValue = parser.getAttributeValue(attrIndex)
            when (attrName) {
                "option-sh", "options-sh", "options-su" -> {
                    if (pickerNode.options == null)
                        pickerNode.options = ArrayList()
                    pickerNode.optionsSh = attrValue
                }
                "multiple" -> {
                    pickerNode.multiple = attrValue == "multiple" || attrValue == "true" || attrValue == "1"
                }
                "separator" -> {
                    pickerNode.separator = attrValue
                }
            }
        }
    }

    private fun descNode(nodeInfoBase: NodeInfoBase, parser: XmlPullParser) {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName == "su" || attrName == "sh" || attrName == "desc-sh") {
                nodeInfoBase.descSh = parser.getAttributeValue(i)
                nodeInfoBase.desc = executeResultRoot(context, nodeInfoBase.descSh)
            }
        }
        if (nodeInfoBase.desc.isEmpty())
            nodeInfoBase.desc = parser.nextText()
    }

    private fun summaryNode(nodeInfoBase: NodeInfoBase, parser: XmlPullParser) {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName == "su" || attrName == "sh" || attrName == "summary-sh") {
                nodeInfoBase.summarySh = parser.getAttributeValue(i)
                nodeInfoBase.summary = executeResultRoot(context, nodeInfoBase.summarySh)
            }
        }
        if (nodeInfoBase.summary.isEmpty())
            nodeInfoBase.summary = parser.nextText()
    }

    private fun resourceNode(parser: XmlPullParser) {
        val extractor = ExtractAssets(context)
        for (i in 0 until parser.attributeCount) {
            when (parser.getAttributeName(i)) {
                "file" -> extractor.extractResource(parser.getAttributeValue(i).trim())
                "dir" -> extractor.extractResources(parser.getAttributeValue(i).trim())
            }
        }
    }

    private fun tagEndInSwitch(switchNode: SwitchNode?, parser: XmlPullParser) {
        if (switchNode != null) {
            val shellResult = executeResultRoot(context, switchNode.getState)
            switchNode.checked = shellResult != "error" && (shellResult == "1" || shellResult.lowercase() == "true")
            if (switchNode.setState == null) {
                switchNode.setState = ""
            }
        }
    }

    private fun tagStartInText(textNode: TextNode, parser: XmlPullParser) {
        if ("title" == parser.name) {
            textNode.title = parser.nextText()
        } else if ("desc" == parser.name) {
            descNode(textNode, parser)
        } else if ("summary" == parser.name) {
            summaryNode(textNode, parser)
        } else if ("slice" == parser.name) {
            rowNode(textNode, parser)
        } else if ("resource" == parser.name) {
            resourceNode(parser)
        }
    }

    private fun rowNode(textNode: TextNode, parser: XmlPullParser) {
        val textRow = TextNode.TextRow()
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i).lowercase()
            val attrValue = parser.getAttributeValue(i)
            try {
                when (attrName) {
                    "bold", "b" -> textRow.bold = (attrValue == "1" || attrValue == "true" || attrValue == "bold")
                    "italic", "i" -> textRow.italic = (attrValue == "1" || attrValue == "true" || attrValue == "italic")
                    "underline", "u" -> textRow.underline = (attrValue == "1" || attrValue == "true" || attrValue == "underline")
                    "foreground", "color" -> textRow.color = Color.parseColor(attrValue)
                    "bg", "background", "bgcolor" -> textRow.bgColor = Color.parseColor(attrValue)
                    "size" -> textRow.size = attrValue.toInt()
                    "break" -> textRow.breakRow = (attrValue == "1" || attrValue == "true" || attrValue == "break")
                    "link", "href" -> textRow.link = attrValue
                    "activity", "a", "intent" -> textRow.activity = attrValue
                    "script", "run" -> {
                        textRow.onClickScript = attrValue
                    }
                    "sh" -> {
                        textRow.dynamicTextSh = attrValue
                    }
                    "align" -> {
                        // 原版使用 Layout.Alignment.ALIGN_LEFT / ALIGN_RIGHT，但这两个常量在 AOSP 中是
                        // @hide（公共 SDK 不可见），故改用语义等价的公共常量：
                        // left → ALIGN_NORMAL（按书写方向贴起始边），right → ALIGN_OPPOSITE（贴结束边）
                        when (attrValue) {
                            "left" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                textRow.align = Layout.Alignment.ALIGN_NORMAL
                            }
                            "right" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                textRow.align = Layout.Alignment.ALIGN_OPPOSITE
                            }
                            "center" -> textRow.align = Layout.Alignment.ALIGN_CENTER
                            "normal" -> textRow.align = Layout.Alignment.ALIGN_NORMAL
                        }
                    }
                }
            } catch (ex: Exception) {
            }
        }
        textRow.text = "" + parser.nextText()
        textNode.rows.add(textRow)
    }

    private fun tagStartInPicker(pickerNode: PickerNode, parser: XmlPullParser) {
        if ("title" == parser.name) {
            pickerNode.title = parser.nextText()
        } else if ("desc" == parser.name) {
            descNode(pickerNode, parser)
        } else if ("summary" == parser.name) {
            summaryNode(pickerNode, parser)
        } else if ("option" == parser.name) {
            if (pickerNode.options == null) {
                pickerNode.options = ArrayList()
            }
            val option = SelectItem()
            for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                if (attrName == "val" || attrName == "value") {
                    option.value = parser.getAttributeValue(i)
                }
            }
            option.title = parser.nextText()
            if (option.value == null)
                option.value = option.title
            pickerNode.options!!.add(option)
        } else if ("getstate" == parser.name || "get" == parser.name) {
            pickerNode.getState = parser.nextText()
        } else if ("setstate" == parser.name || "set" == parser.name) {
            pickerNode.setState = parser.nextText()
        } else if ("resource" == parser.name) {
            resourceNode(parser)
        } else if ("lock" == parser.name || "lock-state" == parser.name) {
            pickerNode.lockShell = parser.nextText()
        }
    }

    private fun tagEndInPicker(pickerNode: PickerNode?, parser: XmlPullParser) {
        if (pickerNode != null) {
            if (pickerNode.getState == null) {
                pickerNode.getState = ""
            } else {
                val shellResult = executeResultRoot(context, "" + pickerNode.getState)
                pickerNode.value = shellResult
            }
            if (pickerNode.setState == null) {
                pickerNode.setState = ""
            }
        }
    }

    private fun tagEndInText(textNode: TextNode?, parser: XmlPullParser) {
    }

    private var vitualRootNode: NodeInfoBase? = null
    private fun executeResultRoot(context: Context, scriptIn: String): String {
        if (vitualRootNode == null) {
            vitualRootNode = NodeInfoBase(pageConfigAbsPath)
        }

        return NodeScriptExecutor(context, shellRunner).execute(scriptIn, vitualRootNode)
    }
}

/**
 * 解析期脚本执行（对齐 b90f2cc `ScriptEnvironmen.executeResultRoot` 的语义）：
 * - 空脚本 → `""`
 * - 以 `file:///android_asset/` 开头的脚本 → 先抽取到应用私有目录，再以 `sh <抽取路径>` 执行
 * - 其它脚本 → 原样执行
 * - 依据 node 注入 `PAGE_CONFIG_DIR`/`PAGE_CONFIG_FILE`/`PAGE_WORK_DIR`/`PAGE_WORK_FILE`
 *   环境变量（assets 来源时 WORK 指向抽取后的私有路径）。
 *
 * 与原版差异：原版把 export 行写进常驻 shell 的 stdin、再调 executor 执行脚本文件；
 * 重写版把 export 行拼进脚本文本一起缓存执行 —— export 在同一进程内生效，对脚本本身
 * 效果等价。原版空节点分支的 quirk（导出两次 `PAGE_WORK_DIR`、从不导出 `PAGE_WORK_FILE`）
 * 原样保留。另：原版经 `ShellTranslation` 做 `@string:` 输出翻译，重写版无此环节。
 */
internal class NodeScriptExecutor(
    private val context: Context,
    private val shellRunner: ShellRunner,
) {
    private val assetsFile = "file:///android_asset/"

    fun execute(script: String, node: NodeInfoBase?): String {
        if (script.isEmpty()) {
            return ""
        }

        val script2 = script.trim()
        val body = if (script2.startsWith(assetsFile)) {
            // asset 脚本：抽取为私有文件后按文件执行（原版 extractScript 语义）
            val path = ExtractAssets(context).extractResource(script2)
            if (path != null) "sh \"$path\"" else script
        } else {
            script
        }

        return shellRunner.execute(envPrefix(node) + body)
    }

    private fun envPrefix(node: NodeInfoBase?): String {
        val stringBuilder = StringBuilder("\n")
        if (node != null && node.currentPageConfigPath.isNotEmpty()) {
            val parentPageConfigDir = node.pageConfigDir
            val currentPageConfigPath = node.currentPageConfigPath
            stringBuilder.append("export PAGE_CONFIG_DIR='").append(parentPageConfigDir).append("'\n")
            stringBuilder.append("export PAGE_CONFIG_FILE='").append(currentPageConfigPath).append("'\n")

            if (currentPageConfigPath.startsWith(assetsFile)) {
                stringBuilder.append("export PAGE_WORK_DIR='")
                    .append(ExtractAssets(context).getExtractPath(parentPageConfigDir)).append("'\n")
                stringBuilder.append("export PAGE_WORK_FILE='")
                    .append(ExtractAssets(context).getExtractPath(currentPageConfigPath)).append("'\n")
            } else {
                stringBuilder.append("export PAGE_WORK_DIR='").append(parentPageConfigDir).append("'\n")
                stringBuilder.append("export PAGE_WORK_FILE='").append(currentPageConfigPath).append("'\n")
            }
        } else {
            stringBuilder.append("export PAGE_CONFIG_DIR=''\n")
            stringBuilder.append("export PAGE_CONFIG_FILE=''\n")
            stringBuilder.append("export PAGE_WORK_DIR=''\n")
            // 原版 quirk：这里重复导出 PAGE_WORK_DIR，而从不导出 PAGE_WORK_FILE —— 原样保留
            stringBuilder.append("export PAGE_WORK_DIR=''\n")
        }
        stringBuilder.append("\n\n")
        return stringBuilder.toString()
    }
}