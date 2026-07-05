package com.example.dynamicisland.state

sealed class IslandState {
    object Idle : IslandState()
    data class IncomingCall(val callerName: String, val callerNumber: String, val callerPhoto: android.graphics.Bitmap? = null) : IslandState()
    data class OngoingCall(val callerName: String, val durationSeconds: Long = 0L) : IslandState()
    data class MusicPlaying(val trackTitle: String, val artistName: String, val albumArt: android.graphics.Bitmap? = null, val isPlaying: Boolean = true, val progress: Float = 0f) : IslandState()
    data class Notification(val appName: String, val title: String, val text: String, val icon: android.graphics.Bitmap? = null, val packageName: String = "") : IslandState()
    data class Timer(val label: String = "Temporizador", val remainingSeconds: Long, val totalSeconds: Long) : IslandState()
    data class BatteryCharging(val level: Int, val isPlugged: Boolean) : IslandState()
    data class RingerMode(val mode: Int) : IslandState()
    object Dismissing : IslandState()
}
