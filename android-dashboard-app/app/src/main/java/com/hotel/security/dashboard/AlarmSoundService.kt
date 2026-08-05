package com.hotel.security.dashboard

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class AlarmSoundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val TAG = "AlarmSoundService"

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val action = intent?.action
        
        when (action) {
            "STOP_ALARM" -> {
                // ← Stop alarm when dismissed
                stopAlarm()
                stopSelf()
            }
            else -> {
                // ← Start playing alarm
                startAlarm()
            }
        }
        
        return START_NOT_STICKY
    }

    private fun startAlarm() {
        Log.i(TAG, "🔔 Starting alarm sound")
        
        try {
            stopAlarm() // Stop any existing
            
            // ← Get alarm sound URI
            val alarmUri = RingtoneManager
                .getDefaultUri(
                    RingtoneManager.TYPE_ALARM
                ) ?: RingtoneManager
                .getDefaultUri(
                    RingtoneManager
                        .TYPE_RINGTONE
                )
            
            // ← Create MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(
                            AudioAttributes
                                .CONTENT_TYPE_SONIFICATION
                        )
                        .setUsage(
                            // ← ALARM usage
                            // Plays even on
                            // silent/DND mode!
                            AudioAttributes
                                .USAGE_ALARM
                        )
                        .build()
                )
                
                // ← Set volume to MAXIMUM
                setVolume(1.0f, 1.0f)
                
                // ← Set data source
                setDataSource(
                    applicationContext,
                    alarmUri
                )
                
                // ← LOOP continuously
                isLooping = true
                
                prepare()
                start()
                
                Log.i(TAG,
                    "✅ MediaPlayer started " +
                    "looping alarm")
            }
            
            // ← Start continuous vibration
            startVibration()
            
        } catch (e: Exception) {
            Log.e(TAG,
                "MediaPlayer failed: $e " +
                "trying fallback")
            startFallbackAlarm()
        }
    }

    private fun startFallbackAlarm() {
        // ← Fallback using Ringtone API
        try {
            val alarmUri = RingtoneManager
                .getDefaultUri(
                    RingtoneManager.TYPE_ALARM
                )
            val ringtone = RingtoneManager
                .getRingtone(
                    applicationContext,
                    alarmUri
                )
            
            if (Build.VERSION.SDK_INT >= 
                Build.VERSION_CODES.P) {
                ringtone.isLooping = true
                ringtone.audioAttributes =
                    AudioAttributes.Builder()
                        .setUsage(
                            AudioAttributes
                                .USAGE_ALARM)
                        .build()
            }
            
            ringtone.play()
            Log.i(TAG, "✅ Fallback ringtone started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Fallback also failed: $e")
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (
                Build.VERSION.SDK_INT >= 
                Build.VERSION_CODES.S) {
                val vm = getSystemService(
                    VibratorManager::class.java
                )
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(
                    Vibrator::class.java)
            }
            
            if (Build.VERSION.SDK_INT >= 
                Build.VERSION_CODES.O) {
                // ← Repeating vibration pattern
                // 500ms on 500ms off repeat
                val pattern = longArrayOf(
                    0, 500, 500,
                    500, 500, 500)
                val waveform = VibrationEffect
                    .createWaveform(
                        pattern,
                        0 // ← repeat index 0
                    )
                vibrator?.vibrate(waveform)
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(
                    0, 500, 500)
                vibrator?.vibrate(pattern, 0)
            }
            
            Log.i(TAG, "✅ Vibration started")
            
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: $e")
        }
    }

    private fun stopAlarm() {
        Log.i(TAG, "🔕 Stopping alarm")
        
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer stop: $e")
        }
        
        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.w(TAG, "Vibration stop: $e")
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
