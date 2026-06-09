package com.example.hotel.service

import android.content.Context
import android.util.Log
import com.example.hotel.data.OfflineDatabase
import com.example.hotel.data.QueuedAlert
import com.example.hotel.data.AgentRepository
import com.example.hotel.data.TamperRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Offline Queue Manager
 * Handles queuing and syncing of alerts when network is unavailable
 */
class OfflineQueueManager(private val context: Context) {
    
    private val database = OfflineDatabase.getDatabase(context)
    private val dao = database.queuedAlertDao()
    private val repository = AgentRepository.default(context)
    
    /**
     * Queue an alert for later sync
     */
    suspend fun queueAlert(
        type: String,
        deviceId: String,
        roomId: String?,
        payload: Map<String, Any>
    ) {
        withContext(Dispatchers.IO) {
            try {
                val alert = QueuedAlert(
                    type = type,
                    deviceId = deviceId,
                    roomId = roomId,
                    payload = JSONObject(payload).toString(),
                    timestamp = System.currentTimeMillis()
                )
                
                dao.insert(alert)
                Log.d("OfflineQueue", "Queued $type alert (offline)")
            } catch (e: Exception) {
                Log.e("OfflineQueue", "Failed to queue alert", e)
            }
        }
    }
    
    /**
     * Attempt to sync all queued alerts
     */
    suspend fun syncQueuedAlerts(): SyncResult {
        return withContext(Dispatchers.IO) {
            val unsynced = dao.getAllUnsynced()
            var synced = 0
            var failed = 0
            
            Log.d("OfflineQueue", "Syncing ${unsynced.size} queued alerts")
            
            for (alert in unsynced) {
                try {
                    // Attempt to send to backend
                    val success = sendAlertToBackend(alert)
                    
                    if (success) {
                        dao.markSynced(alert.id)
                        synced++
                        Log.d("OfflineQueue", "Synced alert ${alert.id}")
                    } else {
                        // Increment retry count
                        dao.updateRetryCount(alert.id, alert.retryCount + 1)
                        failed++
                        
                        // Delete if too many retries (>10)
                        if (alert.retryCount >= 10) {
                            Log.w("OfflineQueue", "Alert ${alert.id} failed 10 times, dropping")
                            dao.markSynced(alert.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OfflineQueue", "Sync failed for alert ${alert.id}", e)
                    failed++
                }
            }
            
            // Clean up old synced alerts (older than 7 days)
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            dao.deleteOldSynced(sevenDaysAgo)
            
            SyncResult(synced, failed)
        }
    }
    
    /**
     * Get count of unsynced alerts
     */
    suspend fun getUnsyncedCount(): Int {
        return withContext(Dispatchers.IO) {
            dao.getUnsyncedCount()
        }
    }
    
    /**
     * Send alert to backend API
     */
    private suspend fun sendAlertToBackend(alert: QueuedAlert): Boolean {
        return try {
            val prefs = context.getSharedPreferences("agent", Context.MODE_PRIVATE)
            val jwtToken = prefs.getString("jwt_token", null) ?: return false
            val auth = "Bearer $jwtToken"
            
            val payload = JSONObject(alert.payload)
            
            when (alert.type) {
                "breach" -> {
                    repository.alerts.breach(
                        auth,
                        com.example.hotel.data.BreachRequest(
                            deviceId = alert.deviceId,
                            roomId = alert.roomId ?: "UNKNOWN",
                            rssi = payload.optInt("rssi", -100)
                        )
                    )
                }
                "battery" -> {
                    repository.alerts.battery(
                        auth,
                        com.example.hotel.data.BatteryRequest(
                            deviceId = alert.deviceId,
                            level = payload.optInt("level", 0)
                        )
                    )
                }
                "heartbeat" -> {
                    repository.alerts.heartbeat(
                        auth,
                        com.example.hotel.data.HeartbeatRequest(
                            deviceId = alert.deviceId,
                            roomId = alert.roomId ?: "UNKNOWN",
                            wifiBssid = payload.optString("bssid", ""),
                            rssi = payload.optInt("rssi", -100),
                            battery = payload.optInt("battery", 0)
                        )
                    )
                }
                "tamper" -> {
                    // Extract threats and descriptions from payload
                    val threatsList = mutableListOf<String>()
                    val threatsArray = payload.optJSONArray("threats")
                    if (threatsArray != null) {
                        for (i in 0 until threatsArray.length()) {
                            threatsList.add(threatsArray.getString(i))
                        }
                    }

                    val descriptionsList = mutableListOf<String>()
                    val descriptionsArray = payload.optJSONArray("descriptions")
                    if (descriptionsArray != null) {
                        for (i in 0 until descriptionsArray.length()) {
                            descriptionsList.add(descriptionsArray.getString(i))
                        }
                    }

                    repository.alerts.tamper(
                        auth,
                        TamperRequest(
                            deviceId = alert.deviceId,
                            roomId = alert.roomId ?: "UNKNOWN",
                            threats = threatsList,
                            descriptions = descriptionsList
                        )
                    )
                }
                else -> {
                    Log.w("OfflineQueue", "Unknown alert type: ${alert.type}")
                    return false
                }
            }
            true
        } catch (e: Exception) {
            Log.e("OfflineQueue", "Failed to send alert to backend", e)
            false
        }
    }
    
    data class SyncResult(val synced: Int, val failed: Int)

    companion object {
        @Volatile
        private var INSTANCE: OfflineQueueManager? = null

        fun getInstance(context: Context): OfflineQueueManager {
            return INSTANCE ?: synchronized(this) {
                val instance = OfflineQueueManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
