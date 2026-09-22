package com.example.receiver

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.receiver.TvReceiverScreen
import com.example.ui.theme.MyApplicationTheme

class TvReceiverActivity : ComponentActivity() {

    private lateinit var server: TvReceiverServer

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // When permissions are granted/denied, refresh server (e.g. start Bluetooth if now permitted)
        try {
            server.startServer()
        } catch (t: Throwable) {
            Log.e("TvReceiverActivity", "Error starting server after permission callback", t)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (_: Throwable) {}

        server = TvReceiverServer.getInstance(this)

        try {
            server.startServer()
        } catch (t: Throwable) {
            Log.e("TvReceiverActivity", "Error starting server in onCreate", t)
        }

        // Request Bluetooth and Notification permissions if needed
        val permsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permsToRequest.isNotEmpty()) {
            try {
                permissionLauncher.launch(permsToRequest.toTypedArray())
            } catch (t: Throwable) {
                Log.w("TvReceiverActivity", "Could not request permissions", t)
            }
        }

        setContent {
            MyApplicationTheme {
                TvReceiverScreen(server = server)
            }
        }
    }
}
