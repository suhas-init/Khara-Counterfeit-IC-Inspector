package com.khara.prototype.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticHelper(context: Context) {
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun triggerPassFeedback() {
        val passPattern = longArrayOf(0, 50, 100, 50)
        val passAmplitudes = intArrayOf(0, 200, 0, 180)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(passPattern, passAmplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(passPattern, -1)
        }
    }

    fun triggerFlaggedFeedback() {
        val flagPattern = longArrayOf(0, 400)
        val flagAmplitudes = intArrayOf(0, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(flagPattern, flagAmplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(flagPattern, -1)
        }
    }
}
