// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

package com.projectkr.shell.favorites

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted favorite entries. Each entry points at the node's [key] inside its
 * source config ([configPath]); the favorites tab re-parses those configs and
 * renders the matching nodes fully interactive.
 */
data class FavoriteEntry(
    val configPath: String,
    val key: String,
    val title: String,
)

class FavoritesStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kr-favorites", Context.MODE_PRIVATE)

    fun load(): List<FavoriteEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                FavoriteEntry(
                    configPath = obj.getString("cfg"),
                    key = obj.getString("key"),
                    title = obj.optString("title"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun isFavorite(configPath: String, key: String): Boolean =
        load().any { it.configPath == configPath && it.key == key }

    fun toggle(configPath: String, key: String, title: String) {
        val current = load().toMutableList()
        val existing = current.indexOfFirst { it.configPath == configPath && it.key == key }
        if (existing >= 0) {
            current.removeAt(existing)
        } else {
            current.add(FavoriteEntry(configPath, key, title))
        }
        save(current)
    }

    private fun save(entries: List<FavoriteEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("cfg", entry.configPath)
                    .put("key", entry.key)
                    .put("title", entry.title),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "entries"
    }
}
