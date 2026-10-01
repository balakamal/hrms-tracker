package com.balakamal.hrmstracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var btnDesktopMode: ImageButton
    private lateinit var btnShortcuts: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnMore: ImageButton
    private lateinit var layoutError: View
    private lateinit var btnRetry: Button

    private var isDesktopMode = false

    companion object {
        private const val PREFS_NAME = "HRMS_PREFS"
        private const val KEY_ACCESS_TOKEN = "AccessToken"
        private const val KEY_REFRESH_TOKEN = "RefreshToken"
        private const val KEY_USER_ID = "UserId"
        private const val KEY_USER_NAME = "UserName"
        private const val KEY_TARGET_HOURS = "TargetHours"
        private const val KEY_DESKTOP_MODE = "DesktopMode"
        private const val LOGIN_URL = "https://apps.pal.tech/hrms/login"
        private const val HOME_URL = "https://apps.pal.tech/hrms/"
        private const val PERMISSION_REQUEST_CODE = 101

        // Desktop User Agent matching standard Chrome on Linux/Windows
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_main)
        
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDesktopMode = sharedPrefs.getBoolean(KEY_DESKTOP_MODE, false)

        // View initialization
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        btnDesktopMode = findViewById(R.id.btn_desktop_mode)
        btnShortcuts = findViewById(R.id.btn_shortcuts)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnMore = findViewById(R.id.btn_more)
        layoutError = findViewById(R.id.layout_error)
        btnRetry = findViewById(R.id.btn_retry)

        setupWebView()
        setupTopBarListeners()
        setupBackNavigation()

        requestNotificationPermissions()
        startAppFlow()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT

        // Enable zoom controls for desktop site navigation
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Apply saved Desktop Mode state
        applyDesktopMode(isDesktopMode, reload = false)

        // Register bidirectional bridge JavaScript Interface
        webView.addJavascriptInterface(WebAppInterface(), "AndroidApp")

        // Add WebChromeClient for loading progress and console logging
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    android.util.Log.d("WebViewConsole", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                }
                return true
            }
        }

        // Configure WebViewClient for page flow, error handling, and script injection
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                layoutError.visibility = View.GONE
                webView.visibility = View.VISIBLE
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE

                // Force desktop viewport meta override if Desktop Mode is enabled
                if (isDesktopMode) {
                    injectDesktopViewport()
                }

                if (url != null && url.contains("apps.pal.tech")) {
                    // Extract tokens silently for background notifications if they log in
                    if (url.contains("dashboard") || url.contains("time-sheet") || url.contains("me/timesheet")) {
                        extractTokensForBackgroundWorker()
                    }
                    
                    // Inject the floating widget script
                    injectScriptFromAssets()
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    layoutError.visibility = View.VISIBLE
                    webView.visibility = View.GONE
                    progressBar.visibility = View.GONE
                }
            }
        }

        // Download Listener to handle Payslips, Tax forms, and Attendance Reports
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            handleFileDownload(url, userAgent, contentDisposition, mimetype)
        }
    }

    private fun setupTopBarListeners() {
        // Desktop Site Toggle
        btnDesktopMode.setOnClickListener {
            val newMode = !isDesktopMode
            sharedPrefs.edit().putBoolean(KEY_DESKTOP_MODE, newMode).apply()
            applyDesktopMode(newMode, reload = true)

            val statusMsg = if (newMode) "Desktop Site Mode Enabled (1280px)" else "Mobile Site Mode Enabled"
            Toast.makeText(this, statusMsg, Toast.LENGTH_SHORT).show()
        }

        // HRMS Shortcuts Menu
        btnShortcuts.setOnClickListener {
            showShortcutsDialog()
        }

        // Refresh Page
        btnRefresh.setOnClickListener {
            Toast.makeText(this, "Refreshing page...", Toast.LENGTH_SHORT).show()
            layoutError.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.reload()
        }

        // More Options Menu
        btnMore.setOnClickListener {
            showMoreOptionsMenu()
        }

        // Retry Button on Error Layout
        btnRetry.setOnClickListener {
            layoutError.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.reload()
        }
    }

    private fun setupBackNavigation() {
        // Intercept Android hardware/gesture back to navigate web history
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * Applies Desktop Site or Mobile Site configurations to WebView settings
     */
    private fun applyDesktopMode(enabled: Boolean, reload: Boolean) {
        isDesktopMode = enabled
        val webSettings = webView.settings

        if (enabled) {
            webSettings.userAgentString = DESKTOP_USER_AGENT
            webSettings.useWideViewPort = true
            webSettings.loadWithOverviewMode = true
            btnDesktopMode.setBackgroundResource(R.drawable.bg_active_pill)
        } else {
            webSettings.userAgentString = null // Reverts to system default mobile UA
            webSettings.useWideViewPort = true
            webSettings.loadWithOverviewMode = true
            btnDesktopMode.setBackgroundResource(android.R.color.transparent)
        }

        if (reload) {
            webView.reload()
        }
    }

    /**
     * Injects a fixed 1280px viewport meta tag to force responsive web apps to render
     * the full desktop layout with all exclusive desktop menus and tables.
     */
    private fun injectDesktopViewport() {
        val js = """
            (function() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head.appendChild(meta);
                }
                meta.setAttribute('content', 'width=1280, initial-scale=0.35, maximum-scale=3.0, user-scalable=yes');
                if (document.body) {
                    document.body.style.minWidth = '1280px';
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    /**
     * Handles downloads for payslips, tax certificates, and attendance reports
     */
    private fun handleFileDownload(url: String, userAgent: String, contentDisposition: String, mimetype: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimetype)
            
            val cookies = CookieManager.getInstance().getCookie(url)
            if (cookies != null) {
                request.addRequestHeader("cookie", cookies)
            }
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Downloading file from HRMS...")

            val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
            request.setTitle(filename)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "Downloading: $filename", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Displays a Quick HRMS Shortcuts dialog to jump directly to key modules
     */
    private fun showShortcutsDialog() {
        val options = arrayOf(
            "🕒 My Swipes & Timesheet",
            "📅 Leave Application & Balance",
            "💰 Payslips & Compensation",
            "✍️ Attendance Regularization",
            "🏠 HRMS Portal Dashboard",
            "🎯 Adjust Shift Target Hours"
        )

        AlertDialog.Builder(this)
            .setTitle("Quick HRMS Shortcuts")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> webView.loadUrl("https://apps.pal.tech/hrms/me/timesheet")
                    1 -> webView.loadUrl("https://apps.pal.tech/hrms/leave")
                    2 -> webView.loadUrl("https://apps.pal.tech/hrms/payroll")
                    3 -> webView.loadUrl("https://apps.pal.tech/hrms/regularization")
                    4 -> webView.loadUrl(HOME_URL)
                    5 -> showTargetHoursDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Quick dialog to update daily target hours (syncs to widget & background notifications)
     */
    private fun showTargetHoursDialog() {
        val currentTarget = sharedPrefs.getFloat(KEY_TARGET_HOURS, 8.5f)
        val options = arrayOf(
            "8.5 Hours (Standard Full-Day)",
            "4.25 Hours (Half-Day)",
            "9.0 Hours (Extended Shift)",
            "Custom Value..."
        )

        AlertDialog.Builder(this)
            .setTitle("Daily Target Hours (Current: ${currentTarget}h)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setTargetHours(8.5f)
                    1 -> setTargetHours(4.25f)
                    2 -> setTargetHours(9.0f)
                    3 -> promptCustomTargetHours(currentTarget)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptCustomTargetHours(current: Float) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(current.toString())
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Enter Target Hours (e.g. 8.5)")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val entered = input.text.toString().toFloatOrNull()
                if (entered != null && entered > 0) {
                    setTargetHours(entered)
                } else {
                    Toast.makeText(this, "Invalid number entered", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setTargetHours(hours: Float) {
        sharedPrefs.edit().putFloat(KEY_TARGET_HOURS, hours).apply()
        
        // Push target hours update into the web localStorage and in-page widget
        val jsSync = "try { localStorage.setItem('at_target_hours', '$hours'); } catch(e){}"
        webView.evaluateJavascript(jsSync, null)

        scheduleBackgroundWorker()
        triggerWidgetRefresh()
        Toast.makeText(this, "Target set to ${hours}h. Widget updated!", Toast.LENGTH_SHORT).show()
    }

    /**
     * Displays secondary options menu
     */
    private fun showMoreOptionsMenu() {
        val modeText = if (isDesktopMode) "Switch to Mobile Mode" else "Switch to Desktop Mode"
        val options = arrayOf(
            modeText,
            "🔔 Test Notification (in 10s)",
            "🔄 Re-inject Insights Widget",
            "🚪 Clear Cache & Relogin",
            "ℹ️ About HRMS Insights"
        )

        AlertDialog.Builder(this)
            .setTitle("More Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> btnDesktopMode.performClick()
                    1 -> {
                        Toast.makeText(this, "Notification scheduled in 10s!", Toast.LENGTH_SHORT).show()
                        Handler(Looper.getMainLooper()).postDelayed({
                            sendTestNotification()
                        }, 10000)
                    }
                    2 -> {
                        injectScriptFromAssets()
                        Toast.makeText(this, "Insights Widget re-injected!", Toast.LENGTH_SHORT).show()
                    }
                    3 -> clearTokensAndShowLogin()
                    4 -> {
                        AlertDialog.Builder(this)
                            .setTitle("HRMS Insights App")
                            .setMessage("Version 1.0\n\n• Desktop & Mobile Viewports\n• Real-time Biometric Tracking\n• Home-Screen Widgets\n• Shift Completion & Pack-up Alerts\n• Secure Local Token Storage")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun startAppFlow() {
        webView.loadUrl(HOME_URL)
        
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
        val jsQuery = """
            (function() {
                try {
                    return JSON.stringify({
                        accessToken: localStorage.getItem('AccessToken') || '',
                        refreshToken: localStorage.getItem('RefreshToken') || '',
                        userId: localStorage.getItem('at_user_id') || '',
                        userName: localStorage.getItem('at_user_name') || '',
                        targetHours: localStorage.getItem('at_target_hours') || ''
                    });
                } catch(e) {
                    return '{}';
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsQuery) { resultJson ->
            try {
                if (resultJson != null && resultJson != "null") {
                    val unescaped = org.json.JSONTokener(resultJson).nextValue() as String
                    val jsonObject = org.json.JSONObject(unescaped)
                    
                    val accessToken = jsonObject.optString("accessToken").trim()
                    val refreshToken = jsonObject.optString("refreshToken").trim()
                    val userId = jsonObject.optString("userId").trim()
                    val userName = jsonObject.optString("userName").trim()
                    val targetHoursStr = jsonObject.optString("targetHours").trim()

                    if (accessToken.isNotEmpty() && accessToken != "null") {
                        val editor = sharedPrefs.edit()
                        editor.putString(KEY_ACCESS_TOKEN, accessToken)
                        
                        if (refreshToken.isNotEmpty() && refreshToken != "null") {
                            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
                        }
                        if (userId.isNotEmpty() && userId != "null") {
                            editor.putString(KEY_USER_ID, userId)
                        }
                        if (userName.isNotEmpty() && userName != "null") {
                            editor.putString(KEY_USER_NAME, userName)
                        }
                        if (targetHoursStr.isNotEmpty() && targetHoursStr != "null") {
                            val targetHours = targetHoursStr.toFloatOrNull() ?: 8.5f
                            editor.putFloat(KEY_TARGET_HOURS, targetHours)
                        }
                        
                        editor.apply()
                        
                        scheduleBackgroundWorker()
                        triggerWidgetRefresh()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HRMSWidget", "Error parsing extracted JSON: ${e.message}", e)
            }
        }
    }

    fun clearTokensAndShowLogin() {
        sharedPrefs.edit().clear().apply()
        WorkManager.getInstance(applicationContext).cancelUniqueWork("HRMS_ATTENDANCE_CHECK")
        
        // Clear WebView Cookies & Storage
        webView.clearCache(true)
        CookieManager.getInstance().removeAllCookies(null)
        
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
            .setSmallIcon(R.mipmap.ic_launcher)
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

    // Bidirectional JS interface for the PWA or in-page script
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

        @JavascriptInterface
        fun saveTokens(accessToken: String, refreshToken: String) {
            sharedPrefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply()
            
            runOnUiThread {
                scheduleBackgroundWorker()
                triggerWidgetRefresh()
            }
        }
    }
}
