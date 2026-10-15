package com.voxora.app

/** Turns raw speech into clean, ready-to-send text. Runs 100% on the phone. */
object TextCleaner {
    private val fillers = Regex("""(?i)\b(um+|uh+|erm+|er|hmm+|uhm+|uh huh)\b[,]?\s*""")

    /** Spoken commands -> symbols (English). Longest first. */
    private val commands = listOf(
        "new paragraph" to "\n\n", "new line" to "\n", "next line" to "\n",
        "question mark" to "?", "exclamation mark" to "!", "exclamation point" to "!",
        "full stop" to ".", "period" to ".", "comma" to ",", "colon" to ":", "semicolon" to ";",
        "open bracket" to "(", "close bracket" to ")", "hashtag" to "#", "at the rate" to "@",
        "smiley emoji" to "🙂", "smiley face" to "🙂", "laughing emoji" to "😂", "crying emoji" to "😭",
        "heart emoji" to "❤️", "thumbs up emoji" to "👍", "fire emoji" to "🔥", "folded hands emoji" to "🙏",
        "party emoji" to "🎉", "thinking emoji" to "🤔", "skull emoji" to "💀", "100 emoji" to "💯",
    )

    private val undoPhrases = setOf("scratch that", "delete that", "undo that", "undo")

    fun isUndo(raw: String) = raw.trim().lowercase().trimEnd('.', '!') in undoPhrases

    fun clean(raw: String, removeFillers: Boolean, voiceCommands: Boolean, english: Boolean,
              shortcuts: List<Pair<String, String>>): String {
        var t = raw.trim()
        if (removeFillers) t = fillers.replace(t, "")

        // personal shortcuts: say "my email" -> types your email
        for ((say, type) in shortcuts) {
            if (say.isBlank()) continue
            t = Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(say.trim()) + "(?![\\p{L}\\p{N}])").replace(t) { type }
        }

        if (voiceCommands && english) {
            for ((say, sym) in commands) {
                t = Regex("(?i)\\s*\\b" + Regex.escape(say) + "\\b\\s*").replace(t) {
                    when (sym) {
                        "\n", "\n\n" -> sym
                        "(", "#", "@" -> " $sym"
                        else -> if (sym.length > 1 || sym[0].code > 127) " $sym " else "$sym "
                    }
                }
            }
        }

        t = t.replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex("""[ \t]+([,.!?;:)])"""), "$1")
            .replace(Regex("""\n[ \t]+"""), "\n")
            .replace(Regex("""([(#@])\s+"""), "$1")
            .trim(' ', '\t')

        if (english) {
            t = t.replace(Regex("""(?<![\p{L}])i(?=('m|'d|'ll|'ve)?(?![\p{L}]))"""), "I")
            // capital letter after . ! ? and new lines
            t = Regex("""([.!?]\s+|\n)(\p{Ll})""").replace(t) { it.groupValues[1] + it.groupValues[2].uppercase() }
        }
        if (t.isNotEmpty()) t = t[0].uppercaseChar() + t.substring(1)
        return t
    }
}
