// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.model

class ActionNode(currentConfigXml: String) : RunnableNode(currentConfigXml) {
    var params: ArrayList<ActionParamInfo>? = null
}
