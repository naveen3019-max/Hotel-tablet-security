package com.example.hotel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class WrongNetworkOverlayService : Service() {
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val TAG = "WrongNetworkOverlay"
    
    companion object {
        var isShowing = false
        
        fun show(
            context: Context,
            wrongSsid: String,
            authorizedSsid: String
        ) {
            val intent = Intent(
                context,
                WrongNetworkOverlayService::class.java
            ).apply {
                action = "SHOW"
                putExtra("wrongSsid", wrongSsid)
                putExtra("authorizedSsid", authorizedSsid)
            }
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun hide(context: Context) {
            context.startService(
                Intent(
                    context,
                    WrongNetworkOverlayService::class.java
                ).apply {
                    action = "HIDE"
                }
            )
        }
    }
    
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        // ← Must call startForeground immediately for Android 8+
        startForeground(9001, buildForegroundNotification())
        
        when (intent?.action) {
            "SHOW" -> {
                val wrongSsid = intent.getStringExtra("wrongSsid") ?: "Unknown Network"
                val authSsid = intent.getStringExtra("authorizedSsid") ?: "Hotel WiFi"
                showOverlay(wrongSsid, authSsid)
            }
            "HIDE" -> {
                hideOverlay()
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun showOverlay(
        wrongSsid: String,
        authorizedSsid: String
    ) {
        if (isShowing) {
            Log.d(TAG, "Overlay already showing")
            return
        }
        
        // ← Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "No overlay permission!")
            stopSelf()
            return
        }
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // ← Create overlay view programmatically
        // No XML needed
        overlayView = createOverlayView(wrongSsid, authorizedSsid)
        
        // ← Window layout params
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            // ← Flags: show on top, keep screen on
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        
        try {
            windowManager?.addView(overlayView, params)
            isShowing = true
            Log.i(TAG, "✅ Wrong network overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Overlay error: $e")
        }
    }
    
    private fun createOverlayView(
        wrongSsid: String,
        authorizedSsid: String
    ): View {
        // ← Create full screen view
        // Dark red background
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#CC1A0000"))
            setPadding(60, 60, 60, 60)
        }
        
        // ← Warning icon (text emoji)
        val iconView = TextView(this).apply {
            text = "⚠️"
            textSize = 80f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 40 }
        }
        
        // ← Main title
        val titleView = TextView(this).apply {
            text = "NETWORK CHANGED!"
            textSize = 32f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 30 }
        }
        
        // ← Subtitle
        val subtitleView = TextView(this).apply {
            text = "Unauthorized WiFi network detected!\nPlease reconnect to the correct network."
            textSize = 18f
            setTextColor(Color.parseColor("#FFCCCC"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 50 }
        }
        
        // ← Wrong network info box
        val wrongNetworkBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#44FF0000"))
            setPadding(40, 30, 40, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }
        
        val wrongLabel = TextView(this).apply {
            text = "Connected to:"
            textSize = 14f
            setTextColor(Color.parseColor("#FFAAAA"))
            gravity = Gravity.CENTER
        }
        
        val wrongName = TextView(this).apply {
            text = wrongSsid
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        
        wrongNetworkBox.addView(wrongLabel)
        wrongNetworkBox.addView(wrongName)
        
        // ← Authorized network info box
        val authNetworkBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#4400AA00"))
            setPadding(40, 30, 40, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 50 }
        }
        
        val authLabel = TextView(this).apply {
            text = "Should be connected to:"
            textSize = 14f
            setTextColor(Color.parseColor("#AAFFAA"))
            gravity = Gravity.CENTER
        }
        
        val authName = TextView(this).apply {
            text = authorizedSsid
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        
        authNetworkBox.addView(authLabel)
        authNetworkBox.addView(authName)
        
        // ← Pulsing alert text
        val alertText = TextView(this).apply {
            text = "🔴 SECURITY ALERT SENT TO MANAGEMENT"
            textSize = 14f
            setTextColor(Color.parseColor("#FF6666"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // ← Add pulsing animation
        val pulseAnim = AlphaAnimation(1.0f, 0.3f).apply {
            duration = 800
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        alertText.startAnimation(pulseAnim)
        
        rootLayout.addView(iconView)
        rootLayout.addView(titleView)
        rootLayout.addView(subtitleView)
        rootLayout.addView(wrongNetworkBox)
        rootLayout.addView(authNetworkBox)
        rootLayout.addView(alertText)
        
        return rootLayout
    }
    
    private fun hideOverlay() {
        try {
            overlayView?.let {
                windowManager?.removeView(it)
            }
            overlayView = null
            isShowing = false
            Log.i(TAG, "✅ Wrong network overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Hide overlay: $e")
        }
    }
    
    private fun buildForegroundNotification(): android.app.Notification {
        val channelId = "overlay_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Security Overlay",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Hotel Security")
            .setContentText("Security monitoring active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
