// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.krscript.core.config

import com.projectkr.krscript.core.model.NodeInfoBase

/**
 * Evaluates a KrScript snippet in the script environment (executor.sh), used while
 * parsing configs for `visible`/`support`, `desc-sh`, `summary-sh`, switch states
 * and picker values.
 *
 * Implemented by the app layer over [com.projectkr.krscript.core.exec.ScriptEnvironment];
 * faked in tests.
 */
fun interface ScriptEvaluator {
    fun evaluate(script: String?, node: NodeInfoBase?): String
}
