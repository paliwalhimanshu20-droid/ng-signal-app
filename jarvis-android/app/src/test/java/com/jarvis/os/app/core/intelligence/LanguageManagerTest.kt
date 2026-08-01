package com.jarvis.os.app.core.intelligence

import com.jarvis.os.app.designsystem.JarvisLanguage
import com.jarvis.os.app.testutil.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Phase 3C, Section 6+7 -- Language Manager + Conversation Language Memory." */
class LanguageDetectorTest {

    @Test
    fun `Devanagari script is detected as Hindi`() {
        assertEquals(JarvisLanguage.Hindi, LanguageDetector.detect("आज बाजार कैसा है"))
    }

    @Test
    fun `romanized Hindi marker words are detected as Hinglish`() {
        assertEquals(JarvisLanguage.Hinglish, LanguageDetector.detect("aaj ka signal kya hai bhai"))
    }

    @Test
    fun `plain Latin script with no Hindi markers is detected as English`() {
        assertEquals(JarvisLanguage.English, LanguageDetector.detect("What's the price of natural gas?"))
    }

    @Test
    fun `word boundary matching means chair does not falsely trigger the hai marker`() {
        assertEquals(JarvisLanguage.English, LanguageDetector.detect("Please pull up that chair"))
    }

    @Test
    fun `short neutral acknowledgments are ambiguous, not an English switch`() {
        assertNull(LanguageDetector.detect("ok"))
        assertNull(LanguageDetector.detect("Thanks"))
        assertNull(LanguageDetector.detect("Hey"))
    }

    @Test
    fun `blank text is ambiguous and returns null`() {
        assertNull(LanguageDetector.detect(""))
        assertNull(LanguageDetector.detect("   "))
    }
}

class LanguageManagerTest {

    @Test
    fun `a detected switch persists through SettingsRepository`() = runTest {
        val settings = FakeSettingsRepository(language = JarvisLanguage.English)
        val manager = LanguageManager(settings)

        val result = manager.observeAndUpdate("aaj ka signal kya hai")

        assertEquals(JarvisLanguage.Hinglish, result)
        assertEquals(JarvisLanguage.Hinglish, settings.appearance.first().language)
    }

    @Test
    fun `an ambiguous message never changes the current conversation language`() = runTest {
        val settings = FakeSettingsRepository(language = JarvisLanguage.Hindi)
        val manager = LanguageManager(settings)

        val result = manager.observeAndUpdate("   ")

        assertEquals(JarvisLanguage.Hindi, result)
        assertEquals(JarvisLanguage.Hindi, settings.appearance.first().language)
    }

    @Test
    fun `switching once persists until explicitly changed again`() = runTest {
        val settings = FakeSettingsRepository(language = JarvisLanguage.English)
        val manager = LanguageManager(settings)

        // Romanized, no Devanagari script -- correctly Hinglish, not Hindi, per LanguageDetector's own rules.
        manager.observeAndUpdate("hindi mein baat karo")
        assertEquals(JarvisLanguage.Hinglish, settings.appearance.first().language)

        // A neutral, marker-free short reply shouldn't flip it back to English on its own.
        manager.observeAndUpdate("ok")
        assertEquals(JarvisLanguage.Hinglish, settings.appearance.first().language)
    }
}
