package com.hotel.security.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import com.hotel.security.dashboard.R
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.widget.TextView
import android.provider.Settings
import android.net.Uri
import android.os.PowerManager
import android.content.Context
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.SystemClock

import android.app.DownloadManager
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private val keepAliveHandler = Handler(Looper.getMainLooper())
    
    private val DASHBOARD_URL = "https://hotel-tablet-security-final.vercel.app"
    private val BACKEND_URL = "https://hotel-tablet-security.onrender.com"
    
    // Keepalive ping every 2 minutes
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            pingBackend()
            keepAliveHandler.postDelayed(this, 2 * 60 * 1000L)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Request notification permission Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
        
        setupWebView()
        handleNotificationIntent(intent)
        requestBatteryOptimizationExempt()
        
        // ← Start alarm-based backup polling
        BreachAlarmReceiver.scheduleNextAlarm(this)
        
        // ← Start polling service
        startPollingService()
    }
    
    private fun requestBatteryOptimizationExempt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    Log.i("MainActivity", "Requesting battery optimization exemption")
                } catch (e: Exception) {
                    Log.w("MainActivity", "Battery opt request failed: $e")
                }
            } else {
                Log.i("MainActivity", "✅ Already exempt from battery optimization")
            }
        }
    }

    private fun startPollingService() {
        val intent = Intent(this, BreachPollingService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Start service: $e")
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            textZoom = 100
            builtInZoomControls = false
            setSupportZoom(false)
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        
        // Allow cookies
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        
        // Add JavaScript bridge so web app can save token for background polling service
        webView.addJavascriptInterface(
            TokenBridge(this),
            "HotelSecurityBridge"
        )
        
        // Bug 2 Fix: Wire up DownloadListener for PDF reports
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloadPdf(url, userAgent, contentDisposition, mimeType)
        }

        // Handle window.open / target="_blank" link popups
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val newWebView = WebView(this@MainActivity)
                newWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (url.contains("/report.pdf") || url.endsWith(".pdf")) {
                            downloadPdf(url, webView.settings.userAgentString, null, "application/pdf")
                        } else {
                            webView.loadUrl(url)
                        }
                        return true
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newWebView
                resultMsg?.sendToTarget()
                return true
            }
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }
            
            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
                
                // Inject JS to bridge localStorage token to Android
                injectTokenBridgeScript()
                injectBridgeWithRetry()
            }
            
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.contains("/report.pdf") || url.endsWith(".pdf")) {
                    downloadPdf(url, view.settings.userAgentString, null, "application/pdf")
                    return true
                }
                return super.shouldOverrideUrlLoading(view, request)
            }
            
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    showErrorLayout()
                }
            }
        }
        
        webView.loadUrl(DASHBOARD_URL)
    }

    private fun downloadPdf(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        try {
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                Toast.makeText(this, "Generating PDF report...", Toast.LENGTH_SHORT).show()
                return
            }

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                val resolvedMime = if (!mimeType.isNullOrEmpty() && mimeType != "application/octet-stream") mimeType else "application/pdf"
                setMimeType(resolvedMime)

                val ua = if (!userAgent.isNullOrEmpty()) userAgent else webView.settings.userAgentString
                addRequestHeader("User-Agent", ua)

                // Forward session cookies from CookieManager
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrEmpty()) {
                    addRequestHeader("Cookie", cookies)
                }

                // Forward auth token if saved in SharedPreferences
                val token = getSharedPreferences("hotel_dashboard_prefs", MODE_PRIVATE)
                    .getString("auth_token", null)
                if (!token.isNullOrEmpty()) {
                    addRequestHeader("Authorization", "Bearer $token")
                }

                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setTitle("Device Security Report")
                setDescription("Downloading PDF Report...")

                val fileName = URLUtil.guessFileName(url, contentDisposition, resolvedMime)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)

            Toast.makeText(this, "📥 Downloading PDF report... Check notifications or Downloads folder.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "DownloadManager failed: ${e.message}, attempting external browser fallback")
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Could not download PDF report: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun injectTokenBridgeScript() {
        // This JS reads localStorage and passes token to Android bridge. Called after every page load
        val script = """
            (function() {
                var token = localStorage.getItem('dashboard_token');
                var hotelId = localStorage.getItem('dashboard_hotel_id') || 'default';
                var username = localStorage.getItem('dashboard_username') || '';
                
                if (token && username) {
                    // Pass to Android bridge
                    HotelSecurityBridge.saveToken(token, hotelId, username);
                } else {
                    // No token = logged out
                    HotelSecurityBridge.clearToken();
                }
                
                // Watch for localStorage changes (catches login/logout events)
                var originalSetItem = localStorage.setItem.bind(localStorage);
                localStorage.setItem = function(key, value) {
                    originalSetItem(key, value);
                    if (key === 'dashboard_token') {
                        var hId = localStorage.getItem('dashboard_hotel_id') || 'default';
                        var uName = localStorage.getItem('dashboard_username') || '';
                        HotelSecurityBridge.saveToken(value, hId, uName);
                    }
                };
                
                // Watch for logout (token removal)
                var originalRemoveItem = localStorage.removeItem.bind(localStorage);
                localStorage.removeItem = function(key) {
                    originalRemoveItem(key);
                    if (key === 'dashboard_token') {
                        HotelSecurityBridge.clearToken();
                    }
                };
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script, null)
    }
    
    private fun injectBridgeWithRetry() {
        // ← Try immediately
        injectWebSocketBridge()
        
        // ← Retry after 3 seconds
        // in case page not fully loaded
        webView.postDelayed({
            injectWebSocketBridge()
        }, 3000L)
        
        // ← Retry after 8 seconds
        webView.postDelayed({
            injectWebSocketBridge()
        }, 8000L)
    }
    
    private fun injectWebSocketBridge() {
        // ← Wait 2 seconds for page to fully load
        // then inject the bridge
        webView.postDelayed({
            val script = """
            (function() {
                if (window._hotelBridgeInjected) {
                    return;
                }
                window._hotelBridgeInjected = true;
                
                // ← Method 1: Intercept fetch for
                // WebSocket alternative
                var _origFetch = window.fetch;
                window.fetch = function() {
                    return _origFetch.apply(
                        this, arguments);
                };
                
                // ← Method 2: Monitor localStorage
                // Dashboard updates localStorage
                // when breach occurs
                var lastBreachCheck = 
                    localStorage.getItem(
                        'last_breach_time') || '0';
                
                setInterval(function() {
                    var currentBreach = 
                        localStorage.getItem(
                            'last_breach_time') || '0';
                    if (currentBreach !== 
                        lastBreachCheck) {
                        lastBreachCheck = currentBreach;
                        var deviceId = localStorage
                            .getItem(
                                'last_breach_device')
                            || '';
                        var roomId = localStorage
                            .getItem(
                                'last_breach_room')
                            || '';
                        var message = localStorage
                            .getItem(
                                'last_breach_message')
                            || 'Breach detected';
                        if (window.HotelSecurityBridge && 
                            deviceId) {
                            window.HotelSecurityBridge
                                .onBreachDetected(
                                deviceId,
                                roomId,
                                message,
                                -127
                            );
                        }
                    }
                }, 2000); // Check every 2 seconds
                
                console.log(
                    'Hotel breach bridge ready');
            })();
            """.trimIndent()
            
            webView.evaluateJavascript(script, null)
            Log.i("MainActivity",
                "✅ WebSocket bridge injected")
        }, 2000L)
    }
    
    private fun pingBackend() {
        Thread {
            try {
                val url = URL("$BACKEND_URL/health")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                Log.d("KeepAlive", "✅ Render ping: $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.w("KeepAlive", "Ping failed: ${e.message}")
            }
        }.start()
    }
    
    private fun showErrorLayout() {
        webView.visibility = View.GONE
        val errorLayout = findViewById<View>(R.id.errorLayout)
        errorLayout?.visibility = View.VISIBLE
        
        findViewById<Button>(R.id.retryButton)?.setOnClickListener {
            errorLayout?.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.loadUrl(DASHBOARD_URL)
        }
    }
    
    private fun handleNotificationIntent(intent: Intent?) {
        val deviceId = intent?.getStringExtra("deviceId")
        if (deviceId != null) {
            // ← Opened from breach notification
            // Stop the alarm sound
            stopAlarmFromActivity()
            
            // Opened from breach notification
            webView.loadUrl(DASHBOARD_URL)
            
            val alertId = intent.getStringExtra("alertId")
            val roomId = intent.getStringExtra("roomId") ?: ""
            if (alertId != null) {
                showBreachBanner(alertId, deviceId, roomId)
            }
        }
    }
    
    private fun showBreachBanner(alertId: String, deviceId: String, roomId: String) {
        val banner = layoutInflater.inflate(R.layout.breach_banner, null)
        val dialog = AlertDialog.Builder(this)
            .setView(banner)
            .setCancelable(false)   // cannot dismiss without acknowledging
            .create()

        banner.findViewById<TextView>(R.id.breachDeviceText).text =
            "🚨 BREACH: Room $roomId — $deviceId"
        banner.findViewById<Button>(R.id.acknowledgeButton).setOnClickListener {
            // Stop alarm + dismiss dialog
            BreachAlarmManager(this).stopBreachAlarm()
            dialog.dismiss()
            // Call backend acknowledge
            acknowledgeAlert(alertId)
        }
        dialog.show()
    }

    private fun acknowledgeAlert(alertId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = getSharedPreferences("hotel_dashboard_prefs", MODE_PRIVATE)
                    .getString("auth_token", null) ?: return@launch
                // POST to backend acknowledge endpoint
                val url = URL("https://hotel-tablet-security.onrender.com/api/alerts/$alertId/acknowledge")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 10_000; readTimeout = 10_000
                }
                Log.d("Ack", "Alert $alertId acknowledged → ${conn.responseCode}")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("Ack", "Failed: ${e.message}")
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        webView.onResume()
        keepAliveHandler.post(keepAliveRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        webView.onPause()
        keepAliveHandler.removeCallbacks(keepAliveRunnable)
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun stopAlarmFromActivity() {
        try {
            val intent = Intent(
                this,
                AlarmSoundService::class.java
            ).apply {
                action = "STOP_ALARM"
            }
            startService(intent)
            Log.i("MainActivity", "🔕 Alarm stopped from activity")
        } catch (e: Exception) {
            Log.w("MainActivity", "Stop alarm failed: $e")
        }
    }
}
