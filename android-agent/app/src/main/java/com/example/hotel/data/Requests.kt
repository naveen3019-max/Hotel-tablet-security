package com.example.hotel.data

data class RegisterRequest(
    val deviceId: String,
    val roomId: String
)

data class HeartbeatRequest(
    val deviceId: String,
    val roomId: String,
    val wifiBssid: String,
    val rssi: Int,
    val battery: Int
)

data class BatteryRequest(
    val deviceId: String,
    val level: Int
)

data class BreachRequest(
    val deviceId: String,
    val roomId: String,
    val rssi: Int
)

data class RoomUpsertRequest(
    val roomId: String,
    val bssid: String,
    val minRssi: Int
)

data class TamperRequest(
    val deviceId: String,
    val roomId: String,
    val threats: List<String>,
    val descriptions: List<String>
)
