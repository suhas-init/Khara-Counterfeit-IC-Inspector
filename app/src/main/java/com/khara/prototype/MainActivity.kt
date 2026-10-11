package com.khara.prototype

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.util.Log
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.khara.prototype.data.ScanSession
import com.khara.prototype.ui.capture.CaptureScreen
import com.khara.prototype.ui.home.HomeScreen
import com.khara.prototype.ui.login.LoginScreen
import com.khara.prototype.ui.processing.ProcessingScreen
import com.khara.prototype.ui.reference.ReferenceDetailScreen
import com.khara.prototype.ui.reference.ReferenceSelectionScreen
import com.khara.prototype.ui.register.RegisterScreen
import com.khara.prototype.ui.result.ResultScreen
import com.khara.prototype.ui.theme.KharaTheme
import com.khara.prototype.util.SoundHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var gyroCount = 0
    private var accelCount = 0
    private var lastGyro = Triple(0f, 0f, 0f)
    private var lastAccel = Triple(0f, 0f, 0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundHelper.init(this)
        registerIqooSensors()

        setContent {
            var isAuthenticated by remember { mutableStateOf(false) }
            KharaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isAuthenticated) {
                        LoginScreen(onAuthSuccess = { name ->
                            ScanSession.currentUser = name
                            isAuthenticated = true
                        })
                    } else {
                        AppNavigation()
                    }
                }
            }
        }

        checkVoiceAvailability()

        lifecycleScope.launch {
            delay(2000)
            val available = SpeechRecognizer.isRecognitionAvailable(this@MainActivity)
            val onDeviceAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SpeechRecognizer.isOnDeviceRecognitionAvailable(this@MainActivity) else false
            val googleQuickSearch = try {
                packageManager.getApplicationInfo("com.google.android.googlequicksearchbox", 0)
                true
            } catch (e: Exception) { false }
            Log.d("KHARA_VOICE_DIAG", "recognizer=$available onDevice=$onDeviceAvailable googleApp=$googleQuickSearch")
        }
    }

    private fun checkVoiceAvailability() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Log.w("KHARA_VOICE", "Speech recognition not available on this device")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                Log.w("KHARA_VOICE", "On-device recognition not available — will use online fallback")
            }
            val pm = packageManager
            val googlePackage = "com.google.android.googlequicksearchbox"
            val hasMic = pm.checkPermission(Manifest.permission.RECORD_AUDIO, googlePackage) == PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                Log.w("KHARA_VOICE", "Google app lacks microphone permission — voice may fail. User should enable in Settings > Apps > Google > Permissions > Microphone")
            }
        } catch (e: Exception) {
            Log.e("KHARA_VOICE", "Error checking voice availability", e)
        }
    }

    private fun registerIqooSensors() {
        try {
            sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
            val sm = sensorManager ?: return

            // Light sensor
            sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }

            // Gyroscope & Accelerometer
            sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }

            // Flicker, Color Temp, Hall/IR Sensors on iQOO 15
            val allSensors = sm.getSensorList(Sensor.TYPE_ALL)
            for (sensor in allSensors) {
                val nameLower = sensor.name.lowercase()
                val type = sensor.type
                if (type == 0x0000001B || nameLower.contains("flicker")) {
                    sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                    Log.d("KHARA_SENSOR", "Registered Flicker Sensor: ${sensor.name}")
                }
                if (type == 0x0000001C || nameLower.contains("color") || nameLower.contains("temperature")) {
                    sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                    Log.d("KHARA_SENSOR", "Registered Color Temp Sensor: ${sensor.name}")
                }
                if (nameLower.contains("hall") || nameLower.contains("infrared")) {
                    sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                    Log.d("KHARA_SENSOR", "Registered Hall/IR Sensor: ${sensor.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("KHARA_SENSOR", "Error registering sensors", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        try {
            when (event.sensor.type) {
                Sensor.TYPE_LIGHT -> {
                    val lux = event.values.firstOrNull() ?: 0f
                    Log.d("KHARA_SENSOR", "lux=$lux")
                }
                Sensor.TYPE_GYROSCOPE -> {
                    lastGyro = Triple(event.values[0], event.values[1], event.values[2])
                    gyroCount++
                    if (gyroCount % 10 == 0) {
                        Log.d("KHARA_SENSOR", "gyro=(${lastGyro.first},${lastGyro.second},${lastGyro.third}) accel=(${lastAccel.first},${lastAccel.second},${lastAccel.third})")
                    }
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    lastAccel = Triple(event.values[0], event.values[1], event.values[2])
                    accelCount++
                }
                0x0000001B -> {
                    Log.d("KHARA_SENSOR", "flicker detected")
                }
                0x0000001C -> {
                    val val0 = event.values.firstOrNull() ?: 0f
                    Log.d("KHARA_SENSOR", "colorTemp=$val0")
                }
                else -> {
                    val nameLower = event.sensor.name.lowercase()
                    if (nameLower.contains("flicker")) {
                        Log.d("KHARA_SENSOR", "flicker detected")
                    } else if (nameLower.contains("color") || nameLower.contains("temperature")) {
                        val val0 = event.values.firstOrNull() ?: 0f
                        Log.d("KHARA_SENSOR", "colorTemp=$val0")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("KHARA_SENSOR", "Error handling sensor change", e)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.e("KHARA_SENSOR", "Error unregistering sensors", e)
        }
        SoundHelper.release()
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
            HomeScreen(
                onRegisterReference = {
                    if (hasCameraPermission) {
                        navController.navigate("register")
                    } else {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                },
                onStartScan = {
                    if (hasCameraPermission) {
                        navController.navigate("select_reference")
                    } else {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                },
                onOpenReference = { chipName ->
                    navController.navigate("reference_detail/$chipName")
                },
                onOpenResult = {
                    if (ScanSession.currentResult != null) {
                        navController.navigate("result")
                    }
                }
            )
        }
        composable("select_reference") {
            ReferenceSelectionScreen(
                onReferenceSelected = { name ->
                    ScanSession.selectedReferenceName = name
                    navController.navigate("capture?ref=$name") {
                        popUpTo("home")
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("register") {
            RegisterScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onComplete = {
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }
        composable("reference_detail/{name}") { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: ""
            ReferenceDetailScreen(
                chipName = name,
                onBack = { navController.popBackStack() },
                onUseAsReference = { refName ->
                    navController.navigate("capture?ref=$refName") {
                        popUpTo("home")
                    }
                }
            )
        }
        composable("capture?ref={ref}") { backStackEntry ->
            val refName = backStackEntry.arguments?.getString("ref") ?: ScanSession.selectedReferenceName ?: ""
            ScanSession.selectedReferenceName = refName
            CaptureScreen(
                refName = refName,
                onCaptureComplete = { uris ->
                    ScanSession.currentUris = uris
                    navController.navigate("processing") {
                        popUpTo("capture") { inclusive = true }
                    }
                }
            )
        }
        composable("capture") {
            CaptureScreen(
                refName = ScanSession.selectedReferenceName ?: "",
                onCaptureComplete = { uris ->
                    ScanSession.currentUris = uris
                    navController.navigate("processing") {
                        popUpTo("capture") { inclusive = true }
                    }
                }
            )
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
