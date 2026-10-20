// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.core.executor

import android.content.Context
import android.content.res.AssetManager
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Port of `common/src/main/java/com/omarea/common/shared/FileWrite.kt` @ b90f2cc —
 * the private-files-dir subset used by the execution layer.
 *
 * (The original's `writeFile(context, file, hasExtName)` variant writing to
 * external `Android/data/<pkg>/` is not used by the kr-script framework and is
 * intentionally omitted.)
 */
object FileWrite {
    val SDCardDir: String = Environment.getExternalStorageDirectory().absolutePath

    fun getPrivateFileDir(context: Context): String {
        return context.filesDir.absolutePath + "/"
    }

    fun getPrivateFilePath(context: Context, outName: String): String {
        return getPrivateFileDir(context) + (if (outName.startsWith("/")) outName.substring(1) else outName)
    }

    fun writePrivateFile(file: String, outName: String, context: Context): String? {
        return writePrivateFile(context.assets, file, outName, context)
    }

    fun writePrivateFile(assetManager: AssetManager, file: String, outName: String, context: Context): String? {
        try {
            val inputStream = if (file.startsWith("file:///android_asset/")) {
                assetManager.open(file.substring("file:///android_asset/".length))
            } else {
                assetManager.open(file)
            }

            val dir = File(getPrivateFileDir(context))
            if (!dir.exists())
                dir.mkdirs()
            val filePath = getPrivateFilePath(context, outName)
            val fileDir = File(filePath).parentFile
            if (!fileDir!!.exists())
                fileDir.mkdirs()

            val fileOutputStream = FileOutputStream(filePath)

            val datas = ByteArray(20480)
            while (true) {
                val len = inputStream.read(datas)
                if (len > 0) {
                    fileOutputStream.write(datas, 0, len)
                } else {
                    break
                }
            }

            fileOutputStream.close()
            inputStream.close()
            val writedFile = File(filePath)
            writedFile.setWritable(true)
            writedFile.setExecutable(true)
            writedFile.setReadable(true)
            return filePath
        } catch (e: IOException) {
            Log.e("writePrivateFile", "" + e.message)
            e.printStackTrace()
        }
        return null
    }

    fun writePrivateFile(bytes: ByteArray, outName: String, context: Context): Boolean {
        try {
            val dir = File(getPrivateFileDir(context))
            if (!dir.exists())
                dir.mkdirs()
            val filePath = getPrivateFilePath(context, outName)
            val fileDir = File(filePath).parentFile
            if (!fileDir!!.exists())
                fileDir.mkdirs()

            val fileOutputStream = FileOutputStream(filePath)
            fileOutputStream.write(bytes, 0, bytes.size)
            fileOutputStream.close()
            File(filePath).setExecutable(true, false)
            val writedFile = File(filePath)
            writedFile.setWritable(true)
            writedFile.setExecutable(true)
            writedFile.setReadable(true)
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    fun writePrivateShellFile(file: String, outName: String, context: Context): String? {
        val data = parseText(context, file)
        if (data.isNotEmpty() && writePrivateFile(data, outName, context)) {
            return getPrivateFilePath(context, outName)
        }
        return null
    }

    // Dos转Unix，避免\r\n导致的脚本无法解析
    private fun parseText(context: Context, fileName: String): ByteArray {
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open(fileName)
            val datas = ByteArray(inputStream.available())
            var len = inputStream.read(datas)
            if (len < 0) {
                len = 0
            }
            val codes = String(datas, 0, len).replace(Regex("\r\n"), "\n").replace(Regex("\r\t"), "\t")
            return codes.toByteArray(Charsets.UTF_8)
        } catch (ex: Exception) {
            Log.e("script-parse", "" + ex.message)
            return "".toByteArray()
        }
    }
}
