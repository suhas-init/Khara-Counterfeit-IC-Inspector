package com.khara.prototype.util

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {
    private const val PREFS = "khara_engineer"
    private const val KEY_NAME = "engineer_name"

    fun isAvailable(context: Context): Boolean = try {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (e: Exception) { false }

    fun getSavedName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null)

    fun saveName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_NAME, name).apply()
    }

    fun authenticate(
        activity: FragmentActivity,
        subtitle: String,
        onSuccess: () -> Unit,
        onFail: (String) -> Unit
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    Log.d("KHARA_BIO", "success")
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.e("KHARA_BIO", "error=$errorCode msg=$errString")
                    onFail(errString.toString())
                }
                override fun onAuthenticationFailed() {
                    Log.d("KHARA_BIO", "failed - retry")
                }
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Khara")
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                .setNegativeButtonText("Cancel")
                .build()
            prompt.authenticate(info)
        } catch (e: Exception) {
            Log.e("KHARA_BIO", "auth failed: ${e.message}")
            onFail(e.message ?: "unavailable")
        }
    }
}
