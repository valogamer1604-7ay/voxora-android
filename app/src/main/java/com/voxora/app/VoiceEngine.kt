package com.voxora.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Press-and-hold speech recognition.
 * start() when the finger goes down, finish() when it lifts.
 * If Android stops listening early (pause in speech) while still held, we keep going.
 */
class VoiceEngine(
    private val context: Context,
    private val onLevel: (Float) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val pieces = mutableListOf<String>()
    private var partial = ""
    private var holding = false
    private var active = false
    private var delivered = false

    fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        pieces.clear(); partial = ""; holding = true; delivered = false
        if (!isAvailable()) { deliverError("No speech recognizer on this phone. Install or update the Google app."); return }
        listen()
    }

    fun finish() {
        holding = false
        if (active) {
            try { recognizer?.stopListening() } catch (_: Exception) {}
            // safety net: if Android never answers, use what we heard
            main.postDelayed({ deliver() }, 3500)
        } else deliver()
    }

    fun cancel() {
        holding = false; delivered = true; active = false
        try { recognizer?.cancel() } catch (_: Exception) {}
    }

    fun destroy() {
        cancel()
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    private fun listen() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(listener) }
        }
        val lang = Prefs.language(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        active = true
        try { recognizer?.startListening(intent) } catch (e: Exception) { active = false; deliverError("Could not start the microphone.") }
    }

    private fun currentText(): String {
        val all = pieces.toMutableList()
        if (partial.isNotBlank()) all.add(partial)
        return all.joinToString(" ").trim()
    }

    private fun deliver() {
        if (delivered) return
        delivered = true; active = false
        val text = currentText()
        if (text.isBlank()) onError("Didn't catch that. Hold the bubble and speak.") else onResult(text)
    }

    private fun deliverError(msg: String) {
        if (delivered) return
        delivered = true; active = false
        onError(msg)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { onLevel(rmsdB) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!p.isNullOrBlank()) partial = p
        }

        override fun onResults(results: Bundle?) {
            val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            partial = ""
            if (!best.isNullOrBlank()) pieces.add(best)
            active = false
            if (holding) main.post { if (holding) listen() } else deliver()
        }

        override fun onError(error: Int) {
            active = false
            val soft = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (holding && soft) { main.post { if (holding) listen() }; return }
            if (currentText().isNotBlank()) { deliver(); return }
            val msg = when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off. Open Voxora to allow it."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No internet for speech recognition."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy. Try again."
                SpeechRecognizer.ERROR_AUDIO -> "Microphone is being used by another app."
                else -> "Didn't catch that. Hold the bubble and speak."
            }
            if (holding) { holding = false }
            deliverError(msg)
        }
    }
}
