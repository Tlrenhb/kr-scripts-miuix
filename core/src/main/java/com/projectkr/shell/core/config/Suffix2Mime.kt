// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.config

class Suffix2Mime {
    fun toMime(suffix: String?): String {
        return when (suffix) {
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "gz" -> "application/x-gzip"
            "tar,taz,tgz" -> "application/x-tar"
            "img" -> "application/x-img"
            "apk" -> "application/vnd.android"
            "jpg,jpeg,jpe" -> "image/jpeg"
            "png" -> "image/png"
            "txt" -> "text/plain"
            "xml" -> "text/xml"
            "html,htm,shtml" -> "text/html"
            else -> ""
        }
    }
}
