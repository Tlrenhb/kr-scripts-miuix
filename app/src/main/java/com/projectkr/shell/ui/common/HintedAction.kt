// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TooltipBox

/**
 * Icon-only action with a long-press/hover tooltip (tooltip.md basic usage).
 */
@Composable
fun HintedAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    TooltipBox(text = text) {
        IconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = text)
        }
    }
}
