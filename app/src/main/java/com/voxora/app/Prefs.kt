package com.voxora.app

import android.content.Context

object Prefs {
    private const val FILE = "voxora"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun language(c: Context): String = sp(c).getString("language", "en-IN") ?: "en-IN"
    fun setLanguage(c: Context, v: String) = sp(c).edit().putString("language", v).apply()

    fun removeFillers(c: Context): Boolean = sp(c).getBoolean("fillers", true)
    fun setRemoveFillers(c: Context, v: Boolean) = sp(c).edit().putBoolean("fillers", v).apply()

    fun bubbleOn(c: Context): Boolean = sp(c).getBoolean("bubble_on", true)
    fun setBubbleOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("bubble_on", v).apply()

    fun lastText(c: Context): String = sp(c).getString("last_text", "") ?: ""
    fun setLastText(c: Context, v: String) = sp(c).edit().putString("last_text", v).apply()

    fun bubbleY(c: Context): Int = sp(c).getInt("bubble_y", -1)
    fun setBubbleY(c: Context, v: Int) = sp(c).edit().putInt("bubble_y", v).apply()

    fun wordsTotal(c: Context): Int = sp(c).getInt("words_total", 0)
    fun addWords(c: Context, n: Int) = sp(c).edit().putInt("words_total", wordsTotal(c) + n).apply()
}

object TextCleaner {
    private val fillers = Regex("""(?i)\b(um+|uh+|erm+|er|hmm+|uhm+)\b[,]?\s*""")

    fun clean(raw: String, removeFillers: Boolean): String {
        var t = raw.trim()
        if (removeFillers) t = fillers.replace(t, "")
        t = t.replace(Regex("""\s{2,}"""), " ").replace(Regex("""\s+([,.!?])"""), "$1").trim()
        if (t.isNotEmpty()) t = t[0].uppercaseChar() + t.substring(1)
        return t
    }
}
