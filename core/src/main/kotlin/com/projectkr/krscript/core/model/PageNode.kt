// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.model

/**
 * `<page>` node: opens a sub page (local config, online html, link or activity).
 */
class PageNode(currentConfigXml: String) : ClickableNode(currentConfigXml) {

    /** Path of the sub page config (relative or absolute). */
    var pageConfigPath: String = ""

    /** Script that prints the sub page config. */
    var pageConfigSh: String = ""

    /** Online html page ref. */
    var onlineHtmlPage: String = ""

    /** Web link opened in the browser. */
    var link: String = ""

    /** Android activity (component) opened on click. */
    var activity: String = ""

    /** Script executed before the config is read. */
    var beforeRead: String = ""

    /** Script executed after the config is read. */
    var afterRead: String = ""

    var pageMenuOptions: ArrayList<PageMenuOption>? = null
    var pageMenuOptionsSh: String = ""

    /** Script handling menu / fab clicks of this page. */
    var pageHandlerSh: String = ""

    /** Scripts executed depending on the load outcome. */
    var loadSuccess: String = ""
    var loadFail: String = ""
}
