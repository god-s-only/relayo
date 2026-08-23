package com.relayo.data.filter

import com.relayo.domain.filter.ContentFilter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultContentFilter @Inject constructor():ContentFilter {

    // Core blocked lexicon — offline, on-device. Covers explicit, slur, and
    // harmful incitement categories. Word-boundary matched case-insensitive.
    private val blockedWords = setOf(
        // explicit / profanity
        "fuck", "fucking", "fucked", "shit", "shitty", "ass", "asshole",
        "bitch", "bastard", "cunt", "dick", "cock", "pussy", "slut", "whore",
        "douche", "douchebag", "bollocks", "wanker",
        // slurs / harassment
        "faggot", "fag", "nigger", "nigga", "retard", "retarded",
        // sexual explicit
        "porn", "porno", "xxx", "nude", "naked", "sex", "sexy",
        // harmful / incitement
        "kill", "killing", "murder", "rape", "rapist", "suicide",
        "bomb", "terror", "terrorist", "abuse", "abusive",
        "harass", "harassment",
        // extra harmful phrases handled as single tokens via substring
        "kill yourself", "kys"
    )

    // Precompile regexes for single-word patterns with word boundaries.
    // Multi-word phrases are checked via substring contains.
    private val singleWordPatterns: List<Pair<String, Regex>> = blockedWords
        .filter { !it.contains(' ') }
        .map { word ->
            val isShort = word.length <= 3
            // For very short words like "ass", require word boundaries to avoid
            // false positives in "classic", "pass", etc.
            val pattern = if(isShort) "\\b${Regex.escape(word)}\\b" else "\\b${Regex.escape(word)}\\b"
            word to Regex(pattern, RegexOption.IGNORE_CASE)
        }

    private val phraseWords = blockedWords.filter { it.contains(' ') }

    override fun isAllowed(text:String):Boolean = findViolation(text) == null

    override fun findViolation(text:String):String? {
        if(text.isBlank()) return null
        val lower = text.lowercase()
        // Check phrases first (substring)
        for(phrase in phraseWords) {
            if(lower.contains(phrase)) return phrase
        }
        for((word, regex) in singleWordPatterns) {
            if(regex.containsMatchIn(text)) return word
        }
        return null
    }

    override fun sanitize(text:String):String {
        var result = text
        for((word, regex) in singleWordPatterns) {
            result = regex.replace(result) { "*".repeat(it.value.length) }
        }
        for(phrase in phraseWords) {
            val regex = Regex(Regex.escape(phrase), RegexOption.IGNORE_CASE)
            result = regex.replace(result) { "*".repeat(it.value.length) }
        }
        return result
    }
}
