package com.jarvis.os.app.core.intelligence

import com.jarvis.os.app.data.settings.SettingsRepository
import com.jarvis.os.app.designsystem.JarvisLanguage
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Phase 3C, Section 6 -- Language Manager." A deterministic classifier, not an LLM guess --
 * the same input always produces the same output, which is the literal mechanism behind "never
 * randomly switch languages": before this class, [com.jarvis.os.app.core.chat.JarvisPersona]'s
 * system prompt just told the model "follow the Owner's language," re-interpreted fresh by the
 * model every single turn with no persisted state backing it -- exactly the kind of soft,
 * unauditable behavior that produces inconsistent switching. This is a real, honest heuristic,
 * not a full NLP language-ID model: Devanagari script is unambiguous (Hindi), a curated set of
 * common romanized Hindi/Hinglish marker words is a reasonable, real signal for Hinglish, and
 * anything else is left undetected (null) rather than guessed -- see [LanguageManager] for why an
 * undetected turn deliberately leaves the current conversation language untouched instead of
 * defaulting to English.
 */
object LanguageDetector {

    /** Devanagari Unicode block (U+0900-U+097F) -- covers Hindi and several other Indic scripts sharing the same block; unambiguous enough for this purpose (JARVIS only distinguishes English/Hindi/Hinglish, not which specific Devanagari-script language). */
    private val DEVANAGARI_RANGE = 0x0900..0x097F

    /** A real, if necessarily partial, list of common romanized Hindi words that show up in Hinglish text -- word-boundary matched so "hai" doesn't fire on "chair". Not exhaustive; a genuinely comprehensive list is a larger, separate effort this class is honest about not attempting. */
    private val HINGLISH_MARKER_WORDS = setOf(
        "hai", "hain", "nahi", "nahin", "kya", "kaise", "kar", "karo", "karna", "krna",
        "mein", "kro", "acha", "theek", "bhai", "yaar", "bata", "batao", "chahiye",
        "kaam", "aap", "tum", "hoon", "hun", "raha", "rahi", "rahe", "diya", "diye",
        "wala", "wali", "abhi", "kal", "aaj", "haan", "matlab", "dekho", "suno",
    )

    /** Short, common neutral acknowledgments that appear identically in English and Hinglish conversation -- treated as ambiguous (null), not as English, so a "ok"/"thanks" reply mid-Hindi-conversation doesn't get misread as an explicit switch back to English. This is exactly the gap a naive "any Latin letters -> English" rule would have: those letters are real, but they're not real evidence of a language switch. */
    private val NEUTRAL_TOKENS = setOf(
        "ok", "okay", "k", "yes", "no", "hi", "hello", "hey", "thanks", "thank you",
        "hmm", "yeah", "yep", "nope", "sure", "cool", "great", "nice", "good",
    )

    /** Returns null for ambiguous/undetected text -- see [LanguageManager]'s own docstring for why null is a meaningfully different signal from "detected English", not the same thing spelled differently. */
    fun detect(text: String): JarvisLanguage? {
        if (text.isBlank()) return null
        if (text.any { it.code in DEVANAGARI_RANGE }) return JarvisLanguage.Hindi

        val lower = text.trim().lowercase()
        if (lower in NEUTRAL_TOKENS) return null
        val hasHinglishMarker = HINGLISH_MARKER_WORDS.any { word -> Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(lower) }
        if (hasHinglishMarker) return JarvisLanguage.Hinglish

        val hasLatinLetters = lower.any { it in 'a'..'z' }
        return if (hasLatinLetters) JarvisLanguage.English else null
    }
}

/**
 * "Phase 3C, Section 6+7 -- Language Manager + Conversation Language Memory." Deliberately reuses
 * [SettingsRepository.appearance]'s existing `language` field as BOTH the persistent default AND
 * the live conversation language, rather than introducing a second, session-scoped store -- per
 * this phase's explicit "reuse the existing SettingsRepository... do NOT build another settings
 * system" rule. "Changing language once changes the conversation language until the user changes
 * it again" (the original mission statement's own phrasing) IS what a single persisted value that
 * only updates on a real detection does -- no separate "temporary vs default" state is needed to
 * satisfy that behavior, and adding one would be exactly the kind of duplicate infrastructure this
 * phase's engineering rule forbids.
 *
 * [observeAndUpdate] is meant to be called once per Owner turn, before that turn's language-
 * dependent rendering (the AI persona system prompt, any local template) happens -- see
 * [com.jarvis.os.app.core.JarvisCore.sendChatMessage]'s call site.
 */
@Singleton
class LanguageManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Detects a language signal from [text]; if one was found AND it differs from the current
     * conversation language, persists the switch and returns the new language. If detection was
     * ambiguous (null) -- e.g. a short reply like "ok" or "yes" with no script/marker signal --
     * the current conversation language is left untouched and returned as-is. This is the literal
     * mechanism behind "never randomly switch languages": an ambiguous turn is never treated as
     * evidence to switch away from whatever language the conversation was already in.
     */
    suspend fun observeAndUpdate(text: String): JarvisLanguage {
        val current = settingsRepository.appearance.first().language
        val detected = LanguageDetector.detect(text) ?: return current
        if (detected != current) {
            settingsRepository.setLanguage(detected)
        }
        return detected
    }

    suspend fun currentLanguage(): JarvisLanguage = settingsRepository.appearance.first().language
}
