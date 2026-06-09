package com.example.hotel.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy

/**
 * Local offline queue database
 * Stores alerts when network is unavailable
 */

@Entity(tableName = "queued_alerts")
data class QueuedAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,              // "breach", "battery", "heartbeat", "tamper"
    val deviceId: String,
    val roomId: String?,
    val payload: String,           // JSON payload
    val timestamp: Long,           // Unix timestamp
    val retryCount: Int = 0,
    val synced: Boolean = false
)

@Dao
interface QueuedAlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: QueuedAlert): Long
    
    @Query("SELECT * FROM queued_alerts WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getAllUnsynced(): List<QueuedAlert>
    
    @Query("UPDATE queued_alerts SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)
    
    @Query("UPDATE queued_alerts SET retryCount = :count WHERE id = :id")
    suspend fun updateRetryCount(id: Long, count: Int)
    
    @Query("DELETE FROM queued_alerts WHERE synced = 1 AND timestamp < :olderThan")
    suspend fun deleteOldSynced(olderThan: Long)
    
    @Query("SELECT COUNT(*) FROM queued_alerts WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int
}

@Database(entities = [QueuedAlert::class], version = 1, exportSchema = false)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun queuedAlertDao(): QueuedAlertDao
    
    companion object {
        @Volatile
        private var INSTANCE: OfflineDatabase? = null
        
        fun getDatabase(context: Context): OfflineDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    "hotel_offline_queue"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
