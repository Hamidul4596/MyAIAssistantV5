package com.myaiaassistant.v4.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Wraps Android's built-in TextToSpeech engine. Tries Bengali first; if the
 * device has no Bengali voice installed it silently falls back to English
 * rather than crashing or staying silent.
 */
class TtsManager(context: Context) {

    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) selectBestAvailableLanguage()
    }

    private fun selectBestAvailableLanguage() {
        val bengali = Locale("bn", "BD")
        val result = tts.setLanguage(bengali)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US)
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
