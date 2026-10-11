package com.khara.prototype.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.khara.prototype.data.ScanSession
import com.khara.prototype.util.BiometricHelper
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(onAuthSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val savedName = remember { BiometricHelper.getSavedName(context) }
    val biometricAvailable = remember { BiometricHelper.isAvailable(context) }
    var nameInput by remember { mutableStateOf(savedName ?: "Suhas") }
    var errorText by remember { mutableStateOf("") }
    var failCount by remember { mutableIntStateOf(0) }

    val matrixGreen = Color(0xFF00FF41)
    val darkBg = Color(0xFF0A0A0A)

    fun startAuth(name: String) {
        if (!biometricAvailable || activity == null) {
            ScanSession.currentUser = name
            onAuthSuccess(name)
            return
        }

        BiometricHelper.authenticate(
            activity = activity,
            subtitle = "Verify your identity",
            onSuccess = {
                ScanSession.currentUser = name
                onAuthSuccess(name)
            },
            onFail = { msg ->
                errorText = msg
                failCount++
                if (failCount >= 3) {
                    ScanSession.currentUser = name
                    onAuthSuccess(name)
                }
            }
        )
    }

    if (savedName != null) {
        LaunchedEffect(Unit) {
            delay(500)
            if (!biometricAvailable) {
                ScanSession.currentUser = savedName
                onAuthSuccess(savedName)
            } else {
                startAuth(savedName)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "KHARA",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = matrixGreen,
            letterSpacing = 4.sp
        )
        Text(
            text = "Offline Counterfeit IC Inspector",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(60.dp))

        if (savedName == null) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Engineer name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = matrixGreen,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = matrixGreen,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (nameInput.isBlank()) {
                        errorText = "Enter your name"
                    } else {
                        BiometricHelper.saveName(context, nameInput)
                        startAuth(nameInput)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = matrixGreen,
                    contentColor = Color.Black
                )
            ) {
                Text("Verify Fingerprint", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            if (errorText.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(errorText, color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        } else {
            Text("Welcome back, $savedName", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Verify your identity to continue", color = Color.Gray, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = {
                    errorText = ""
                    startAuth(savedName)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = matrixGreen,
                    contentColor = Color.Black
                )
            ) {
                Text("Verify Fingerprint", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            if (errorText.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(errorText, color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        errorText = ""
                        startAuth(savedName)
                    },
                    border = BorderStroke(1.dp, matrixGreen),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = matrixGreen)
                ) {
                    Text("Try again")
                }
            }
        }
    }
}
