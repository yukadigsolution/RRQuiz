package com.yuka.rrquiz

import android.content.Context

/**
 * Penyimpanan URL WebView di SharedPreferences.
 * Satu-satunya sumber kebenaran untuk URL yang dimuat MainActivity.
 */
object UrlPrefs {
    private const val PREFS_NAME = "rrquiz_prefs"
    private const val KEY_URL = "webview_url"

    const val DEFAULT_URL = "https://alfalearning.sat.co.id/"

    fun getUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun saveUrl(context: Context, rawUrl: String): String {
        val url = normalize(rawUrl)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, url)
            .apply()
        return url
    }

    /** Tambah https:// kalau user mengetik tanpa skema, mis. "alfalearning.sat.co.id". */
    fun normalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return DEFAULT_URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "https://$trimmed"
    }
}
