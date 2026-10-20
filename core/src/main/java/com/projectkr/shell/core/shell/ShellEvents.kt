// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.shell

import android.os.Handler

/**
 * Port of `common/src/main/java/com/omarea/common/shell/ShellEvents.kt` @ b90f2cc.
 *
 * Event constants used by [KeepShellAsync]. `PROCESS_EVENT_EXIT` is declared but
 * never sent by the original — preserved as-is.
 */
open class ShellEvents {
    protected var processHandler: Handler? = null
    var PROCESS_EVENT_STAR = 0
    var PROCESS_EVENT_CONTENT = 1
    var PROCESS_EVENT_ERROR_CONTENT = 2
    var PROCESS_EVENT_EXIT = -1
}
