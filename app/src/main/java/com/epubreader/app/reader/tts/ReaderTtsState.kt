package com.epubreader.app.reader.tts

data class ReaderTtsState(
    val isEnabled: Boolean = false,
    val isSpeaking: Boolean = false,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voiceName: String? = null
)
