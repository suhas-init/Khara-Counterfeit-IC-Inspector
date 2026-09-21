package com.khara.prototype

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.khara.prototype.data.ScanSession
import com.khara.prototype.ui.capture.CaptureScreen
import com.khara.prototype.ui.home.HomeScreen
import com.khara.prototype.ui.processing.ProcessingScreen
import com.khara.prototype.ui.result.ResultScreen
import com.khara.prototype.ui.theme.KharaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KharaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(onStartScan = {
                if (hasCameraPermission) {
                    navController.navigate("capture")
                } else {
                    launcher.launch(Manifest.permission.CAMERA)
                }
            })
        }
        composable("capture") {
            CaptureScreen(onCaptureComplete = { uris ->
                ScanSession.currentUris = uris
                navController.navigate("processing")
            })
        }
        composable("processing") {
            ProcessingScreen(
                onProcessingComplete = {
                    navController.navigate("result")
                }
            )
        }
        composable("result") {
            ResultScreen(
                onReset = {
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }
    }
}
