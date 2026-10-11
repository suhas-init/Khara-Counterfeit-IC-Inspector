package com.khara.prototype.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log

object SoundHelper {
    private var soundPool: SoundPool? = null
    private var passSound: Int = 0
    private var flagSound: Int = 0
    private var captureSound: Int = 0
    private var saveSound: Int = 0
    private var micStartSound: Int = 0
    private var micOkSound: Int = 0
    private var micFailSound: Int = 0

    private var toneGen: ToneGenerator? = null
    private const val VOLUME = 90

    fun init(context: Context) {
        if (soundPool != null) return
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build()

            val res = context.resources
            fun loadRaw(name: String): Int {
                val id = res.getIdentifier(name, "raw", context.packageName)
                return if (id != 0) soundPool?.load(context, id, 1) ?: 0 else 0
            }

            passSound = loadRaw("pass_chime")
            flagSound = loadRaw("flag_tone")
            captureSound = loadRaw("capture_click")
            saveSound = loadRaw("save_success")
            micStartSound = loadRaw("mic_start")
            micOkSound = loadRaw("mic_ok")
            micFailSound = loadRaw("mic_fail")

            ensureToneInit()
        } catch (e: Exception) {
            Log.e("KHARA_SOUND", "Error initializing SoundPool: ${e.message}")
            ensureToneInit()
        }
    }

    private fun ensureToneInit() {
        if (toneGen == null) {
            try {
                toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
            } catch (e: Exception) {
                Log.e("KHARA_SOUND", "ToneGenerator init failed: ${e.message}")
            }
        }
    }

    private fun canPlay(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.ringerMode != AudioManager.RINGER_MODE_SILENT
    }

    fun playPass(context: Context) {
        if (!canPlay(context)) return
        if (passSound != 0 && soundPool != null) {
            soundPool?.play(passSound, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            try {
                ensureToneInit()
                toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
                Handler(Looper.getMainLooper()).postDelayed({
                    try { toneGen?.startTone(ToneGenerator.TONE_PROP_PROMPT, 200) } catch (_: Exception) {}
                }, 270)
            } catch (e: Exception) { Log.e("KHARA_SOUND", "playPass failed: ${e.message}") }
        }
    }

    fun playFlag(context: Context) {
        if (!canPlay(context)) return
        if (flagSound != 0 && soundPool != null) {
            soundPool?.play(flagSound, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            try {
                ensureToneInit()
                toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
                Handler(Looper.getMainLooper()).postDelayed({
                    try { toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300) } catch (_: Exception) {}
                }, 350)
                Handler(Looper.getMainLooper()).postDelayed({
                    try { toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400) } catch (_: Exception) {}
                }, 700)
            } catch (e: Exception) { Log.e("KHARA_SOUND", "playFlag failed: ${e.message}") }
        }
    }

    fun playCapture(context: Context) {
        if (!canPlay(context)) return
        if (captureSound != 0 && soundPool != null) {
            soundPool?.play(captureSound, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            try {
                ensureToneInit()
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            } catch (e: Exception) { Log.e("KHARA_SOUND", "playCapture failed: ${e.message}") }
        }
    }

    fun playSaveSuccess(context: Context) {
        if (!canPlay(context)) return
        if (saveSound != 0 && soundPool != null) {
            soundPool?.play(saveSound, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            try {
                ensureToneInit()
                toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 180)
                Handler(Looper.getMainLooper()).postDelayed({
                    try { toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 150) } catch (_: Exception) {}
                }, 200)
            } catch (e: Exception) { Log.e("KHARA_SOUND", "playSaveSuccess failed: ${e.message}") }
        }
    }

    fun playMicStart(context: Context) {
        if (!canPlay(context)) return
        if (micStartSound != 0 && soundPool != null) {
            soundPool?.play(micStartSound, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            try {
                ensureToneInit()
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 80)
            } catch (e: Exception) { Log.e("KHARA_SOUND", "playMicStart failed: ${e.message}") }
        }
    }

    fun playMicRecognized(context: Context) {
        if (!canPlay(context)) return
        if (micOkSound != 0 && soundPool != null) {
            soundPool?.play(micOkSound, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            try {
                ensureToneInit()
                toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 80)
                Handler(Looper.getMainLooper()).postDelayed({
                    try { toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 80) } catch (_: Exception) {}
                }, 100)
            } catch (e: Exception) { Log.e("KHARA_SOUND", "playMicRecognized failed: ${e.message}") }
        }
    }

    fun playMicFailed(context: Context) {
        if (!canPlay(context)) return
        if (micFailSound != 0 && soundPool != null) {
            soundPool?.play(micFailSound, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            try {
                ensureToneInit()
                toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 200)
            } catch (e: Exception) { Log.e("KHARA_SOUND", "playMicFailed failed: ${e.message}") }
        }
    }

    fun release() {
        try {
            soundPool?.release()
            soundPool = null
            toneGen?.release()
            toneGen = null
        } catch (e: Exception) {
            Log.e("KHARA_SOUND", "Error releasing SoundHelper: ${e.message}")
        }
    }
}
