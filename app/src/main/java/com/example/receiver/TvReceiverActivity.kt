package com.example.receiver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.ui.receiver.TvReceiverScreen
import com.example.ui.theme.MyApplicationTheme

class TvReceiverActivity : ComponentActivity() {

    private lateinit var server: TvReceiverServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        server = TvReceiverServer.getInstance(applicationContext, lifecycleScope)
        server.startServer()

        setContent {
            MyApplicationTheme {
                TvReceiverScreen(server = server)
            }
        }
    }
}
