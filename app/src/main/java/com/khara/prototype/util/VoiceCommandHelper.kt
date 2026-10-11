package com.khara.prototype.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceCommandHelper(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isContinuous = false

    fun isAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            false
        }
    }

    fun startContinuousListening(onCommand: (String) -> Unit, onError: (Int) -> Unit) {
        isContinuous = true
        isListening = true
        listenLoop(onCommand, onError)
    }

    private fun listenLoop(onCommand: (String) -> Unit, onError: (Int) -> Unit) {
        if (!isContinuous) return
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {}
        speechRecognizer = null

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isContinuous) return@postDelayed
            try {
                speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        // silently retry after 300ms if still continuous
                        if (isContinuous) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (isContinuous) listenLoop(onCommand, onError)
                            }, 300)
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                        if (text.isNotBlank()) onCommand(text)
                        // restart loop for next command
                        if (isContinuous) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (isContinuous) listenLoop(onCommand, onError)
                            }, 300)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                if (isContinuous) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isContinuous) listenLoop(onCommand, onError)
                    }, 500)
                }
            }
        }, 400)
    }

    fun stopContinuousListening() {
        isContinuous = false
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {}
        speechRecognizer = null
        isListening = false
    }

    fun stopListening() {
        stopContinuousListening()
    }

    fun destroy() {
        stopContinuousListening()
    }
}
