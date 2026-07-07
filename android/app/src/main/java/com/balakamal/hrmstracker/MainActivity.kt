package com.balakamal.hrmstracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
        
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        webView = WebView(this)
        setContentView(webView)

        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT

        // Register bidirectional bridge JavaScript Interface
        webView.addJavascriptInterface(WebAppInterface(), "AndroidApp")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                if (url != null && url.contains("apps.pal.tech/hrms")) {
                    // Extract tokens silently for background notifications if they log in
                    if (url.contains("dashboard") || url.contains("time-sheet") || url.contains("me/timesheet")) {
                        extractTokensForBackgroundWorker()
                    }
                    
                    // Inject the floating widget script
                    injectScriptFromAssets()
                }
            }
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
                    
                    val currentToken = sharedPrefs.getString(KEY_ACCESS_TOKEN, null)
                    if (currentToken != accessToken) {
                        sharedPrefs.edit()
                            .putString(KEY_ACCESS_TOKEN, accessToken)
                            .putString(KEY_REFRESH_TOKEN, refreshToken)
                            .apply()
                            
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Background notifications synced!", Toast.LENGTH_SHORT).show()
                            scheduleBackgroundWorker()
                        }
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
        }

        @JavascriptInterface
        fun saveSettings(hours: Double) {
            sharedPrefs.edit()
                .putFloat(KEY_TARGET_HOURS, hours.toFloat())
                .apply()
        }
    }
}
