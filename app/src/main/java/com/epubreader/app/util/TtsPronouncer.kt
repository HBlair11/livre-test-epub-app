package com.epubreader.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsPronouncer(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var pendingWord: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isInitialized = true
            pendingWord?.let {
                speak(it)
                pendingWord = null
            }
        }
    }

    fun speak(word: String) {
        if (isInitialized) {
            tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "vocab_pronounce_id")
        } else {
            pendingWord = word
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
