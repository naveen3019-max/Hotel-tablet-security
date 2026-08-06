package com.hotel.security.dashboard

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
        
        if (action == "STOP_ALARM") {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }
        
        // ← Start playing alarm
        startAlarm()
        
        return START_NOT_STICKY
    }
    
    private fun startAlarm() {
        stopAlarm() // Stop any existing
        
        try {
            val alarmUri = RingtoneManager
                .getDefaultUri(
                    RingtoneManager.TYPE_ALARM
                ) ?: RingtoneManager
                .getDefaultUri(
                    RingtoneManager.TYPE_RINGTONE
                )
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(
                            AudioAttributes
                                .CONTENT_TYPE_SONIFICATION)
                        .setUsage(
                            AudioAttributes
                                .USAGE_ALARM)
                        .build()
                )
                setVolume(1.0f, 1.0f)
                setDataSource(
                    applicationContext,
                    alarmUri)
                isLooping = true
                prepare()
                start()
                Log.i(TAG,
                    "✅ Alarm playing")
            }
            
            startVibration()
            
            // ← Auto stop after 60 seconds
            // Prevents alarm running forever
            Handler(Looper.getMainLooper())
                .postDelayed({
                stopAlarm()
                stopSelf()
            }, 60_000L)
            
        } catch (e: Exception) {
            Log.e(TAG, "Alarm failed: $e")
            stopSelf()
        }
    }
    
    private fun startVibration() {
        try {
            val vib = if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.S) {
                (getSystemService(
                    VibratorManager::class.java
                )).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(
                    Vibrator::class.java)
            }
            vibrator = vib
            
            if (Build.VERSION.SDK_INT >= 
                Build.VERSION_CODES.O) {
                val pattern = longArrayOf(
                    0, 500, 500)
                vib?.vibrate(
                    VibrationEffect
                        .createWaveform(
                            pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib?.vibrate(
                    longArrayOf(0, 500, 500), 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration: $e")
        }
    }
    
    private fun stopAlarm() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {}
        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {}
        Log.i(TAG, "🔕 Alarm stopped")
    }
    
    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
    
    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
