package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tv_servers")
data class TvServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val ipAddress: String,
    val port: Int = 8765,
    val protocol: String = "WEBSOCKET", // WEBSOCKET, UDP, BLUETOOTH
    val lastConnected: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

@Entity(tableName = "controller_profiles")
data class ControllerProfileEntity(
    @PrimaryKey
    val id: String = "default",
    val profileName: String = "Standard Gamer",
    val hapticEnabled: Boolean = true,
    val vibrationStrength: Float = 0.8f,
    val stickDeadzone: Float = 0.12f,
    val stickSensitivity: Float = 1.0f,
    val buttonLayout: String = "XBOX", // XBOX, PLAYSTATION, NINTENDO
    val autoReconnect: Boolean = true
)
