// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.model

class PageNode(currentPageConfigXml: String) : ClickableNode(currentPageConfigXml) {
    var pageConfigPath: String = ""
    var pageConfigSh: String = ""
    var onlineHtmlPage: String = ""
    var link: String = ""
    var activity: String = ""
    var beforeRead: String = ""
    var afterRead: String = ""
    var pageMenuOptions: ArrayList<PageMenuOption>? = null
    var pageMenuOptionsSh: String = ""
    var pageHandlerSh: String = ""
    var loadSuccess: String = ""
    var loadFail: String = ""
}
