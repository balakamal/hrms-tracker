package com.balakamal.hrmstracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.core.app.NotificationCompat
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var sharedPrefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "HRMS_PREFS"
        private const val KEY_ACCESS_TOKEN = "AccessToken"
        private const val KEY_REFRESH_TOKEN = "RefreshToken"
        private const val KEY_USER_ID = "UserId"
        private const val KEY_USER_NAME = "UserName"
        private const val KEY_TARGET_HOURS = "TargetHours"
        private const val PWA_URL = "https://balakamal.github.io/hrms-tracker/hrms-pwa/"
        private const val LOGIN_URL = "https://apps.pal.tech/hrms/login"
        private const val PERMISSION_REQUEST_CODE = 101
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_main)
        
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        webView = findViewById(R.id.webView)

        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Register bidirectional bridge JavaScript Interface
        webView.addJavascriptInterface(WebAppInterface(), "AndroidApp")

        // Add WebChromeClient for console logging and debugging
        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    android.util.Log.d("WebViewConsole", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                if (url != null && url.contains("apps.pal.tech")) {
                    // Extract tokens silently for background notifications if they log in
                    if (url.contains("dashboard") || url.contains("time-sheet") || url.contains("me/timesheet")) {
                        extractTokensForBackgroundWorker()
                    }
                    
                    // Inject the floating widget script
                    injectScriptFromAssets()
                }
            }
        }

        // Bind Refresh Button
        findViewById<android.widget.ImageButton>(R.id.btn_refresh).setOnClickListener {
            Toast.makeText(this, "Refreshing page...", Toast.LENGTH_SHORT).show()
            webView.reload()
        }

        // Bind Test Notification Button
        findViewById<Button>(R.id.btn_test_notification).setOnClickListener {
            Toast.makeText(this, "Notification scheduled: triggering in 10 seconds!", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                sendTestNotification()
            }, 10000)
        }

        requestNotificationPermissions()
        startAppFlow()
    }

    private fun startAppFlow() {
        // Always load the original HRMS portal homepage.
        // WebView manages cookies natively, so it will redirect to the dashboard if already logged in.
        webView.loadUrl("https://apps.pal.tech/hrms/")
        
        val token = sharedPrefs.getString(KEY_ACCESS_TOKEN, null)
        if (token != null) {
            scheduleBackgroundWorker()
            triggerWidgetRefresh()
        }
    }

    private fun injectScriptFromAssets() {
        try {
            val inputStream = assets.open("hrms-attendance-tracker.js")
            val script = inputStream.bufferedReader().use { it.readText() }
            webView.evaluateJavascript(script, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractTokensForBackgroundWorker() {
        webView.evaluateJavascript(
            "(function() { return localStorage.getItem('AccessToken'); })();"
        ) { accessTokenJson ->
            val accessToken = accessTokenJson.replace("\"", "").trim()
            if (accessToken.isNotEmpty() && accessToken != "null") {
                webView.evaluateJavascript(
                    "(function() { return localStorage.getItem('RefreshToken'); })();"
                ) { refreshTokenJson ->
                    val refreshToken = refreshTokenJson.replace("\"", "").trim()
                    
                    // Also extract user details and settings from localStorage as fallback
                    webView.evaluateJavascript("(function() { return localStorage.getItem('at_user_id'); })();") { uIdJson ->
                        val uId = uIdJson.replace("\"", "").trim()
                        if (uId.isNotEmpty() && uId != "null") {
                            sharedPrefs.edit().putString(KEY_USER_ID, uId).apply()
                        }
                    }
                    webView.evaluateJavascript("(function() { return localStorage.getItem('at_user_name'); })();") { uNameJson ->
                        val uName = uNameJson.replace("\"", "").trim()
                        if (uName.isNotEmpty() && uName != "null") {
                            sharedPrefs.edit().putString(KEY_USER_NAME, uName).apply()
                        }
                    }
                    webView.evaluateJavascript("(function() { return localStorage.getItem('at_target_hours'); })();") { hoursJson ->
                        val hoursStr = hoursJson.replace("\"", "").trim()
                        if (hoursStr.isNotEmpty() && hoursStr != "null") {
                            val hours = hoursStr.toFloatOrNull() ?: 8.5f
                            sharedPrefs.edit().putFloat(KEY_TARGET_HOURS, hours).apply()
                        }
                    }

                    val currentToken = sharedPrefs.getString(KEY_ACCESS_TOKEN, null)
                    if (currentToken != accessToken) {
                        sharedPrefs.edit()
                            .putString(KEY_ACCESS_TOKEN, accessToken)
                            .putString(KEY_REFRESH_TOKEN, refreshToken)
                            .apply()
                            
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Background notifications synced!", Toast.LENGTH_SHORT).show()
                            scheduleBackgroundWorker()
                            triggerWidgetRefresh()
                        }
                    } else {
                        // Even if token didn't change, trigger refresh to sync any newly loaded user details/settings
                        triggerWidgetRefresh()
                    }
                }
            }
        }
    }

    fun clearTokensAndShowLogin() {
        sharedPrefs.edit().clear().apply()
        WorkManager.getInstance(applicationContext).cancelUniqueWork("HRMS_ATTENDANCE_CHECK")
        
        // Clear WebView Cookies & Storage
        webView.clearCache(true)
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        
        webView.loadUrl(LOGIN_URL)
        triggerWidgetRefresh()
    }

    private fun scheduleBackgroundWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<AttendanceWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "HRMS_ATTENDANCE_CHECK",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun triggerWidgetRefresh() {
        val intent = Intent(this, AttendanceAppWidgetProvider::class.java).apply {
            action = AttendanceAppWidgetProvider.ACTION_REFRESH
        }
        sendBroadcast(intent)
    }

    private fun sendTestNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "hrms_tracker_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Shift Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("HRMS Shift Complete!")
            .setContentText("This is a test notification from HRMS Insights.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(99, notification)
    }

    private fun requestNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST_CODE)
            }
        }
    }

    // Bidirectional JS interface for the PWA
    inner class WebAppInterface {
        @JavascriptInterface
        fun onLogout() {
            runOnUiThread {
                clearTokensAndShowLogin()
            }
        }

        @JavascriptInterface
        fun saveUserData(userId: String, userName: String) {
            sharedPrefs.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_NAME, userName)
                .apply()
            triggerWidgetRefresh()
        }

        @JavascriptInterface
        fun saveSettings(hours: Double) {
            sharedPrefs.edit()
                .putFloat(KEY_TARGET_HOURS, hours.toFloat())
                .apply()
            triggerWidgetRefresh()
        }
    }
}
