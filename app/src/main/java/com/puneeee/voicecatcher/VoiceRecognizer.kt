package com.puneeee.voicecatcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Keeps listening across short pauses. The user can finish explicitly, or the
 * session submits after several seconds with no further recognised speech.
 */
class VoiceRecognizer(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onState: (String) -> Unit,
    private val onCaptureState: (Boolean) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val phrases = mutableListOf<String>()
    private var recognizer: SpeechRecognizer? = null
    private var capturing = false
    private var sessionId = 0
    private val finishAfterSilence = Runnable { finish() }

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onState("Speech recognition is unavailable on this phone.")
            return
        }
        destroyRecognizer()
        phrases.clear()
        sessionId++
        capturing = true
        onCaptureState(true)
        onState("Listening… you can pause. Tap Finish speaking when you are done.")
        startListeningSession(sessionId)
    }

    fun finish() {
        if (!capturing) return
        capturing = false
        handler.removeCallbacks(finishAfterSilence)
        recognizer?.stopListening()
        destroyRecognizer()
        onCaptureState(false)
        val transcript = phrases.joinToString(" ").trim()
        phrases.clear()
        if (transcript.isBlank()) onState("I did not catch that. Please try again.") else onResult(transcript)
    }

    fun destroy() {
        capturing = false
        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        onCaptureState(false)
    }

    private fun startListeningSession(expectedSessionId: Int) {
        if (!capturing || expectedSessionId != sessionId) return
        destroyRecognizer()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { speechRecognizer ->
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onState("Listening… you can pause, then continue speaking.")
                override fun onBeginningOfSpeech() = onState("Listening…")
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = onState("Short pause detected — still listening.")
                override fun onError(error: Int) {
                    if (!capturing || expectedSessionId != sessionId) return
                    if (error !in setOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                        onState("Speech recognition stopped. Tap Start speaking and try again.")
                        finish()
                    } else {
                        // Recognition providers commonly signal a timeout between phrases.
                        onState("Listening… continue speaking or tap Finish speaking.")
                        handler.removeCallbacks(finishAfterSilence)
                        handler.postDelayed(finishAfterSilence, SILENCE_FINISH_DELAY_MS)
                        handler.postDelayed({ startListeningSession(expectedSessionId) }, RESTART_DELAY_MS)
                    }
                }
                override fun onResults(results: Bundle?) {
                    if (!capturing || expectedSessionId != sessionId) return
                    val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    if (!phrase.isNullOrBlank() && phrases.lastOrNull() != phrase) phrases.add(phrase)
                    onState("Heard: ${phrases.joinToString(" ")}. Continue or tap Finish speaking.")
                    handler.removeCallbacks(finishAfterSilence)
                    handler.postDelayed(finishAfterSilence, SILENCE_FINISH_DELAY_MS)
                    handler.postDelayed({ startListeningSession(expectedSessionId) }, RESTART_DELAY_MS)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            speechRecognizer.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_000L)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3_500L)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a reminder or task"),
            )
        }
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private companion object {
        const val RESTART_DELAY_MS = 250L
        const val SILENCE_FINISH_DELAY_MS = 5_000L
    }
}
