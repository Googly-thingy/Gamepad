package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.ui.controller.ControllerScreen
import com.example.ui.controller.ControllerViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val controllerViewModel: ControllerViewModel by viewModels()

    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (_: Throwable) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                requestBluetoothPermissions.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            } catch (_: Throwable) {}
        }

        setContent {
            MyApplicationTheme {
                ControllerScreen(viewModel = controllerViewModel)
            }
        }
    }
}
