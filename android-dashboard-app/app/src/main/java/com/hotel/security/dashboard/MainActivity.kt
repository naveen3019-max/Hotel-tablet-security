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
            builtInZoomControls = false
            setSupportZoom(false)
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
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }
            
            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
                
                // Inject JS to bridge localStorage token to Android
                injectTokenBridgeScript()
            }
            
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    showErrorLayout()
                }
            }
        }
        
        webView.loadUrl(DASHBOARD_URL)
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
            // Opened from breach notification
            webView.loadUrl(DASHBOARD_URL)
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
}
