// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.projectkr.krscript.core.model.PageNode
import com.projectkr.shell.navigation.Route
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * Opens a page node following the original priority: link → browser,
 * activity → component intent, html → online page, otherwise sub config.
 *
 * Duplicate pushes are rejected by miuix-nav ("Duplicate contentKey"), so the
 * guard below is mandatory — double taps and self-referential pages would
 * otherwise crash.
 */
fun openPageNode(context: Context, node: PageNode, backStack: MutableList<NavKey>) {
    when {
        node.link.isNotEmpty() -> openInBrowser(context, node.link)
        node.activity.isNotEmpty() -> openActivity(context, node.activity)
        node.onlineHtmlPage.isNotEmpty() -> {
            PageRegistry.put(node)
            pushIfAbsent(backStack, Route.OnlinePage(url = node.onlineHtmlPage, title = node.title))
        }
        else -> {
            PageRegistry.put(node)
            pushIfAbsent(
                backStack,
                Route.PageDetail(
                    configPath = node.pageConfigPath,
                    title = node.title,
                    nodeKey = node.index,
                ),
            )
        }
    }
}

/** Pushes only when the identical route is not already on the stack. */
fun pushIfAbsent(backStack: MutableList<NavKey>, route: NavKey) {
    if (backStack.none { it == route }) {
        backStack.add(route)
    }
}

fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/** Opens `package/component` style activity refs, mirroring TryOpenActivity. */
fun openActivity(context: Context, activity: String) {
    runCatching {
        val intent = if (activity.contains("/")) {
            val pkg = activity.substringBefore("/")
            val cls = activity.substringAfter("/")
            Intent().setClassName(pkg, if (cls.startsWith(".")) pkg + cls else cls)
        } else {
            context.packageManager.getLaunchIntentForPackage(activity)
        }
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
