package com.voxora.app

import android.content.Context

object Prefs {
    private const val FILE = "voxora"
    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun consented(c: Context) = sp(c).getBoolean("consent_v2", false)
    fun setConsented(c: Context, v: Boolean) = sp(c).edit().putBoolean("consent_v2", v).apply()

    /** "auto" = phone language */
    fun language(c: Context): String = sp(c).getString("language", "auto") ?: "auto"
    fun setLanguage(c: Context, v: String) = sp(c).edit().putString("language", v).apply()
    fun recentLanguages(c: Context): List<String> =
        (sp(c).getString("recent_langs", "") ?: "").split(',').filter { it.isNotBlank() }
    fun pushRecent(c: Context, v: String) {
        val l = (listOf(v) + recentLanguages(c).filter { it != v }).take(4)
        sp(c).edit().putString("recent_langs", l.joinToString(",")).apply()
    }

    fun removeFillers(c: Context) = sp(c).getBoolean("fillers", true)
    fun setRemoveFillers(c: Context, v: Boolean) = sp(c).edit().putBoolean("fillers", v).apply()

    fun voiceCommands(c: Context) = sp(c).getBoolean("commands", true)
    fun setVoiceCommands(c: Context, v: Boolean) = sp(c).edit().putBoolean("commands", v).apply()

    fun bubbleOn(c: Context) = sp(c).getBoolean("bubble_on", true)
    fun setBubbleOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("bubble_on", v).apply()

    fun keepLast(c: Context) = sp(c).getBoolean("keep_last", true)
    fun setKeepLast(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("keep_last", v).apply()
        if (!v) setLastText(c, "")
    }

    fun lastText(c: Context): String = Vault.decrypt(sp(c).getString("last_text_enc", "") ?: "")
    fun setLastText(c: Context, v: String) {
        if (v.isNotEmpty() && !keepLast(c)) return
        sp(c).edit().putString("last_text_enc", Vault.encrypt(v)).apply()
    }

    fun bubbleY(c: Context) = sp(c).getInt("bubble_y", -1)
    fun setBubbleY(c: Context, v: Int) = sp(c).edit().putInt("bubble_y", v).apply()

    fun wordsTotal(c: Context) = sp(c).getInt("words_total", 0)
    fun addWords(c: Context, n: Int) = sp(c).edit().putInt("words_total", wordsTotal(c) + n).apply()

    /** Shortcuts: say "my email" -> types "you@gmail.com". Stored encrypted. */
    fun shortcuts(c: Context): List<Pair<String, String>> {
        val raw = Vault.decrypt(sp(c).getString("shortcuts_enc", "") ?: "")
        if (raw.isBlank()) return emptyList()
        return raw.split('\u001E').mapNotNull {
            val p = it.split('\u001F'); if (p.size == 2 && p[0].isNotBlank()) p[0] to p[1] else null
        }
    }
    fun setShortcuts(c: Context, list: List<Pair<String, String>>) {
        val raw = list.joinToString("\u001E") { it.first + "\u001F" + it.second }
        sp(c).edit().putString("shortcuts_enc", Vault.encrypt(raw)).apply()
    }

    fun wipeAll(c: Context) {
        val keep = consented(c)
        sp(c).edit().clear().apply()
        setConsented(c, keep)
    }
}
