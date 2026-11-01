package com.altften.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.altften.recorder.data.RecordingConfig
import com.altften.recorder.presentation.MainScreen
import com.altften.recorder.presentation.theme.AltftenTheme
import com.altften.recorder.service.RecordingService

class MainActivity : ComponentActivity() {

    private var pendingStartConfig: RecordingConfig? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (recordGranted) {
            pendingStartConfig?.let { config ->
                RecordingService.startService(this, config)
                pendingStartConfig = null
            }
        } else {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AltftenTheme {
                MainScreen(
                    onStartRecording = { config ->
                        checkPermissionsAndStart(config)
                    },
                    onStopRecording = {
                        RecordingService.stopService(this)
                    },
                    onSaveLastChunks = { count ->
                        RecordingService.saveLast(this, count)
                    }
                )
            }
        }
    }

    private fun checkPermissionsAndStart(config: RecordingConfig) {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            pendingStartConfig = config
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            RecordingService.startService(this, config)
        }
    }
}
