package com.balakamal.hrmstracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var layoutTopBar: View
    private lateinit var btnDesktopMode: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var appTitle: TextView
    private lateinit var layoutNowBar: View
    private lateinit var imgNowBarIcon: ImageView
    private lateinit var txtCountdown: TextView
    private lateinit var layoutSummaryCard: View
    private lateinit var btnCloseSummary: ImageButton
    private lateinit var txtSummaryFirstIn: TextView
    private lateinit var txtSummaryWorkTime: TextView
    private lateinit var txtSummaryExitTime: TextView
    private lateinit var txtSummaryActualExitTime: TextView
    private lateinit var txtSummaryStatus: TextView
    private lateinit var layoutError: View
    private lateinit var btnRetry: Button
    private lateinit var layoutShiftNudgeOverlay: View
    private lateinit var btnDismissNudgeCard: ImageButton
    private lateinit var btnStopNudge: Button
    private lateinit var txtNudgeTitle: TextView
    private lateinit var txtNudgeMessage: TextView
    private lateinit var layoutUpdateBadge: View
    private lateinit var viewUpdateBeacon: View
    private lateinit var txtUpdateVersion: TextView
    private var beaconAnimator: android.animation.ObjectAnimator? = null

    private val nudgeStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ShiftAlertManager.ACTION_NUDGE_STATE_CHANGED) {
                runOnUiThread {
                    updateNudgeOverlayState(animate = true)
                }
            }
        }
    }

    private var isDesktopMode = false
    private var isWfhMode = false
    private var isSwipeRefreshAllowed = true
    private var isWidgetFullscreen = false

    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
            countdownHandler.postDelayed(this, 60000)
        }
    }

    companion object {
        private const val PREFS_NAME = "HRMS_PREFS"
        private const val KEY_ACCESS_TOKEN = "AccessToken"
        private const val KEY_REFRESH_TOKEN = "RefreshToken"
        private const val KEY_USER_ID = "UserId"
        private const val KEY_USER_NAME = "UserName"
        private const val KEY_TARGET_HOURS = "TargetHours"
        private const val KEY_DESKTOP_MODE = "DesktopMode"
        private const val KEY_NOTIF_SHIFT_COMPLETE = "NotifShiftComplete"
        private const val KEY_NOTIF_PRE_EXIT = "NotifPreExit"
        private const val KEY_WFH_MODE = "WfhMode"
        private const val KEY_WFH_DATE = "WfhDate"
        private const val KEY_LEAVE_CASUAL = "LeaveBalanceCasual"
        private const val KEY_LEAVE_SICK = "LeaveBalanceSick"
        private const val KEY_LEAVE_EARNED = "LeaveBalanceEarned"
        private const val KEY_LAST_UPDATE_CHECK = "LastUpdateCheckTime"
        private const val KEY_REMOTE_VERSION_CODE = "RemoteVersionCode"
        private const val KEY_REMOTE_VERSION_NAME = "RemoteVersionName"
        private const val KEY_REMOTE_APK_URL = "RemoteApkUrl"
        private const val KEY_REMOTE_RELEASE_NOTES = "RemoteReleaseNotes"
        private const val KEY_REMOTE_APK_SIZE = "RemoteApkSize"
        private const val KEY_REMOTE_RELEASE_DATE = "RemoteReleaseDate"
        private const val UPDATE_CHECK_THROTTLE_MS = 4 * 60 * 60 * 1000L // 4 Hours
        private const val VERSION_JSON_URL = "https://raw.githubusercontent.com/balakamal/hrms-tracker/main/version.json"

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

        // Full Edge-to-Edge window immersion with display cutout support
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        setContentView(R.layout.activity_main)

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDesktopMode = sharedPrefs.getBoolean(KEY_DESKTOP_MODE, false)

        val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedWfhDate = sharedPrefs.getString(KEY_WFH_DATE, "")
        if (savedWfhDate != todayDateStr) {
            sharedPrefs.edit().putBoolean(KEY_WFH_MODE, false).putString(KEY_WFH_DATE, todayDateStr).apply()
            isWfhMode = false
        } else {
            isWfhMode = sharedPrefs.getBoolean(KEY_WFH_MODE, false)
        }

        // View initialization
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        layoutTopBar = findViewById(R.id.layout_top_bar)
        btnDesktopMode = findViewById(R.id.btn_desktop_mode)
        btnRefresh = findViewById(R.id.btn_refresh)
        appTitle = findViewById(R.id.app_title)
        layoutNowBar = findViewById(R.id.layout_now_bar)
        imgNowBarIcon = findViewById(R.id.img_now_bar_icon)
        txtCountdown = findViewById(R.id.txt_countdown)
        layoutSummaryCard = findViewById(R.id.layout_summary_card)
        btnCloseSummary = findViewById(R.id.btn_close_summary)
        txtSummaryFirstIn = findViewById(R.id.txt_summary_first_in)
        txtSummaryWorkTime = findViewById(R.id.txt_summary_work_time)
        txtSummaryExitTime = findViewById(R.id.txt_summary_exit_time)
        txtSummaryActualExitTime = findViewById(R.id.txt_summary_actual_exit_time)
        txtSummaryStatus = findViewById(R.id.txt_summary_status)
        layoutError = findViewById(R.id.layout_error)
        btnRetry = findViewById(R.id.btn_retry)
        layoutShiftNudgeOverlay = findViewById(R.id.layout_shift_nudge_overlay)
        btnDismissNudgeCard = findViewById(R.id.btn_dismiss_nudge_card)
        btnStopNudge = findViewById(R.id.btn_stop_nudge)
        txtNudgeTitle = findViewById(R.id.txt_nudge_title)
        txtNudgeMessage = findViewById(R.id.txt_nudge_message)
        layoutUpdateBadge = findViewById(R.id.layout_update_badge)
        viewUpdateBeacon = findViewById(R.id.view_update_beacon)
        txtUpdateVersion = findViewById(R.id.txt_update_version)
        setupUpdateBadge()

        // Dynamic System Window Insets: Attach to root layout to guarantee dispatch
        val rootLayout = findViewById<View>(R.id.root_layout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            val defaultStatusBarHeight = (28 * resources.displayMetrics.density).toInt()
            val topInset = if (statusBarInsets.top > 0) statusBarInsets.top else defaultStatusBarHeight
            val extraSpacing = (6 * resources.displayMetrics.density).toInt()

            layoutTopBar.setPadding(
                layoutTopBar.paddingLeft,
                topInset + extraSpacing,
                layoutTopBar.paddingRight,
                (8 * resources.displayMetrics.density).toInt()
            )

            if (::layoutSummaryCard.isInitialized) {
                val lp = layoutSummaryCard.layoutParams as? ViewGroup.MarginLayoutParams
                if (lp != null) {
                    lp.bottomMargin = (16 * resources.displayMetrics.density).toInt() + navInsets.bottom
                    layoutSummaryCard.layoutParams = lp
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(rootLayout)

        updateWfhHubState(animate = false)
        updateNudgeOverlayState(animate = false)

        setupWebView()
        setupTopBarListeners()
        setupWfhHubListeners()
        setupShiftNudgeListeners()
        setupSummaryCard()
        setupBackNavigation()

        NotificationHelper.initChannels(this)
        requestNotificationPermissions()

        // Initialize / schedule Daily Timesheet Submission Reminder (default 9:00 PM IST)
        TimesheetReminderWorker.schedule(applicationContext)

        val shortcutUrl = intent?.getStringExtra("shortcut_url")
        startAppFlow(shortcutUrl)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = intent?.getStringExtra("shortcut_url")
        if (!url.isNullOrBlank()) {
            webView.loadUrl(url)
        } else {
            ensureTrackerScriptInjected()
        }
        updateNudgeOverlayState(animate = true)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            TokenManager.syncTokensToWebView(webView, applicationContext)
            ensureTrackerScriptInjected()
        }
        countdownHandler.removeCallbacks(countdownRunnable)
        countdownHandler.post(countdownRunnable)

        try {
            val filter = android.content.IntentFilter(ShiftAlertManager.ACTION_NUDGE_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(nudgeStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(nudgeStateReceiver, filter)
            }
        } catch (e: Exception) {
            // Ignore
        }
        updateNudgeOverlayState(animate = false)
        LockScreenLiveCardManager.updateLiveCard(this)
        checkUpdateWithThrottle(force = false)
    }

    override fun onPause() {
        super.onPause()
        countdownHandler.removeCallbacks(countdownRunnable)
        stopBeaconAnimation()
        try {
            unregisterReceiver(nudgeStateReceiver)
        } catch (e: Exception) {
            // Ignore
        }
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
        webSettings.setSupportMultipleWindows(true)
        webSettings.javaScriptCanOpenWindowsAutomatically = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Setup SwipeRefreshLayout pull-to-refresh
        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.primary_royal))
        swipeRefresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.bg_surface_card))
        swipeRefresh.setOnRefreshListener {
            webView.reload()
            checkForAppUpdate(force = true, isManualClick = false)
        }
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            if (!isSwipeRefreshAllowed) {
                true // Child cannot scroll down for refresh; suppresses pull-to-refresh
            } else {
                webView.scrollY > 0
            }
        }

        // Apply saved Desktop Mode state
        applyDesktopMode(isDesktopMode, reload = false)

        // Register bidirectional bridge JavaScript Interface
        webView.addJavascriptInterface(WebAppInterface(), "AndroidApp")

        // Add WebChromeClient for loading progress, console logging, and multi-window popups
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                    ensureTrackerScriptInjected()
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    android.util.Log.d("WebViewConsole", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                }
                return true
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val hrefMsg = view?.handler?.obtainMessage()
                view?.requestFocusNodeHref(hrefMsg)
                val targetUrl = hrefMsg?.data?.getString("url")
                if (!targetUrl.isNullOrBlank()) {
                    handleLinkDispatch(targetUrl)
                    return true
                }
                return false
            }
        }

        // Configure WebViewClient for page flow, error handling, link interception, and script injection
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleLinkDispatch(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                return handleLinkDispatch(url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                layoutError.visibility = View.GONE
                webView.visibility = View.VISIBLE
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false

                // Force desktop viewport meta override if Desktop Mode is enabled
                if (isDesktopMode) {
                    injectDesktopViewport()
                }

                if (url != null && url.contains("apps.pal.tech")) {
                    // Extract SSO v2 cookies and tokens natively from CookieManager
                    syncCookiesAndTokensFromManager()

                    // Push active tokens from native storage into WebView localStorage
                    TokenManager.syncTokensToWebView(webView, applicationContext)

                    // Inject localStorage hook to catch silent Angular token refreshes in real time
                    injectTokenRefreshObserver()

                    // Extract tokens silently for background notifications if authenticated
                    if (!url.contains("/login")) {
                        extractTokensForBackgroundWorker()
                    }
                    
                    // Inject the floating widget script
                    injectScriptFromAssets()
                    view?.postDelayed({ ensureTrackerScriptInjected() }, 1200)

                    // Extract leave balances if on the leave page
                    if (url.contains("leave")) {
                        injectLeaveBalanceExtractor()
                    }
                }

            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                ensureTrackerScriptInjected()
                view?.postDelayed({ ensureTrackerScriptInjected() }, 1000)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    layoutError.visibility = View.VISIBLE
                    webView.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }
            }
        }

        // Download Listener to handle Payslips, Tax forms, and Attendance Reports
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            handleFileDownload(url, userAgent, contentDisposition, mimetype)
        }
    }

    /**
     * Legacy JS interface stub to ensure existing injected scripts don't fail,
     * while permanently stopping accidental icon fading/flickering.
     */
    fun toggleFullscreenBars() {
        // Safe no-op: Top bar remains stable without flickering
    }

    private fun setupTopBarListeners() {
        // Desktop Site Toggle with One UI spring micro-animation
        btnDesktopMode.setOnClickListener {
            btnDesktopMode.animate().scaleX(0.88f).scaleY(0.88f).setDuration(120).withEndAction {
                btnDesktopMode.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
            val newMode = !isDesktopMode
            sharedPrefs.edit().putBoolean(KEY_DESKTOP_MODE, newMode).apply()
            applyDesktopMode(newMode, reload = true)

            val statusMsg = if (newMode) "Desktop Site Mode Enabled" else "Mobile Site Mode Enabled"
            Toast.makeText(this, statusMsg, Toast.LENGTH_SHORT).show()
        }

        // Refresh Page with smooth 360-degree rotation animation
        btnRefresh.setOnClickListener {
            btnRefresh.animate()
                .rotationBy(360f)
                .setDuration(500)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
            Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show()
            layoutError.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.reload()
            checkForAppUpdate(force = true, isManualClick = false)
        }


        // Long click on Title opens Settings & Preferences Dialog
        appTitle.setOnLongClickListener {
            showSettingsDialog()
            true
        }

        // Retry Button on Error Layout
        btnRetry.setOnClickListener {
            layoutError.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.reload()
        }
    }

    private fun setupWfhHubListeners() {
        // Tap on Now Bar Live Activity Capsule opens Workspace Status Info
        layoutNowBar.setOnClickListener {
            showWorkspaceModeSheet()
        }
    }

    private fun updateWfhHubState(animate: Boolean) {
        updateCountdown()
    }

    private fun setupShiftNudgeListeners() {
        btnStopNudge.setOnClickListener {
            ShiftAlertManager.stopAlerts(this)
            Toast.makeText(this, "Safe travels! Reminders stopped for today.", Toast.LENGTH_SHORT).show()
            updateNudgeOverlayState(animate = true)
        }

        btnDismissNudgeCard.setOnClickListener {
            layoutShiftNudgeOverlay.animate()
                .alpha(0f)
                .translationY(-40f)
                .setDuration(250)
                .withEndAction { layoutShiftNudgeOverlay.visibility = View.GONE }
                .start()
            Toast.makeText(this, "Alert minimized. Tap notification when leaving to stop reminders.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateNudgeOverlayState(animate: Boolean) {
        if (!::layoutShiftNudgeOverlay.isInitialized) return
        val isActive = ShiftAlertManager.isNudgeActive(this)
        if (isActive) {
            val overtimeMins = ShiftAlertManager.getOvertimeMinutes(this)
            val overtimeStr = if (overtimeMins > 0) "+${overtimeMins}m Overtime" else "Shift Met"
            val interval = ShiftAlertManager.getIntervalMinutes(this)
            txtNudgeTitle.text = "SHIFT COMPLETE • $overtimeStr 🚀"
            txtNudgeMessage.text = "You've completed your daily shift! Step out and head home. Alerts repeat every $interval minutes until stopped."

            if (layoutShiftNudgeOverlay.visibility != View.VISIBLE) {
                layoutShiftNudgeOverlay.visibility = View.VISIBLE
                if (animate) {
                    layoutShiftNudgeOverlay.alpha = 0f
                    layoutShiftNudgeOverlay.translationY = -40f
                    layoutShiftNudgeOverlay.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(350)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
                        .start()
                } else {
                    layoutShiftNudgeOverlay.alpha = 1f
                    layoutShiftNudgeOverlay.translationY = 0f
                }
            }
        } else {
            if (animate && layoutShiftNudgeOverlay.visibility == View.VISIBLE) {
                layoutShiftNudgeOverlay.animate()
                    .alpha(0f)
                    .translationY(-40f)
                    .setDuration(250)
                    .withEndAction { layoutShiftNudgeOverlay.visibility = View.GONE }
                    .start()
            } else {
                layoutShiftNudgeOverlay.visibility = View.GONE
            }
        }
    }

    private fun showWorkspaceModeSheet() {
        val calendar = Calendar.getInstance()
        val isWeekend = calendar.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        val modeTitle = when {
            isWfhMode -> "Remote Workspace"
            isWeekend -> "Week Off (Weekend)"
            else -> "In-Office Biometric Mode"
        }
        val modeDesc = when {
            isWfhMode -> "Remote status is automatically synced from your official HRMS profile. Biometric tracking is paused."
            isWeekend -> "It's the weekend! Relax, recharge, and enjoy your time off."
            else -> "In-office biometric tracking is active. Punch records are computed in real-time."
        }
        AlertDialog.Builder(this, R.style.Theme_HRMS_Dialog)
            .setTitle(modeTitle)
            .setMessage(modeDesc)
            .setPositiveButton("Settings") { _, _ -> showSettingsDialog() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun setupSummaryCard() {
        btnCloseSummary.setOnClickListener {
            dismissSummaryCard()
        }
    }

    private fun showSummaryCard() {
        val firstIn = sharedPrefs.getString("WidgetFirstIn", "--:--") ?: "--:--"
        if (firstIn == "--:--" || firstIn.isBlank()) {
            return
        }
        val workTime = sharedPrefs.getString("WidgetWorkTime", "0h 00m") ?: "0h 00m"
        val exitTime = sharedPrefs.getString("WidgetExitTime", "--:--") ?: "--:--"
        val actualExitTime = sharedPrefs.getString("WidgetActualExitTime", "--:--") ?: "--:--"
        val status = sharedPrefs.getString("WidgetStatusText", "Clocked In") ?: "Clocked In"

        txtSummaryFirstIn.text = firstIn
        txtSummaryWorkTime.text = workTime
        txtSummaryExitTime.text = exitTime
        txtSummaryActualExitTime.text = actualExitTime
        txtSummaryStatus.text = status

        val statusColor = when {
            status.contains("Complete", ignoreCase = true) -> ContextCompat.getColor(this, R.color.accent_cyan)
            status.contains("WFH", ignoreCase = true) -> ContextCompat.getColor(this, R.color.accent_amber)
            status.contains("Clocked In", ignoreCase = true) || status.contains("Work", ignoreCase = true) -> ContextCompat.getColor(this, R.color.accent_emerald)
            else -> ContextCompat.getColor(this, R.color.accent_cyan)
        }
        txtSummaryStatus.setTextColor(statusColor)

        layoutSummaryCard.alpha = 0f
        layoutSummaryCard.translationY = 120f
        layoutSummaryCard.visibility = View.VISIBLE
        layoutSummaryCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .start()

        // Auto-dismiss summary card after 6 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            dismissSummaryCard()
        }, 6000)
    }

    private fun dismissSummaryCard() {
        if (::layoutSummaryCard.isInitialized && layoutSummaryCard.visibility == View.VISIBLE) {
            layoutSummaryCard.animate()
                .alpha(0f)
                .translationY(120f)
                .setDuration(300)
                .withEndAction {
                    layoutSummaryCard.visibility = View.GONE
                }
                .start()
        }
    }

    private fun updateCountdown() {
        if (!::txtCountdown.isInitialized) return
        val calendar = Calendar.getInstance()
        val isWeekend = calendar.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        val firstIn = sharedPrefs.getString("WidgetFirstIn", "--:--") ?: "--:--"
        val exitTime = sharedPrefs.getString("WidgetExitTime", "--:--") ?: "--:--"
        val progressPercent = sharedPrefs.getInt("WidgetProgressPercent", 0)

        if (isWfhMode) {
            imgNowBarIcon.setImageResource(R.drawable.ic_wfh_home)
            imgNowBarIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_amber_gold))
            txtCountdown.text = "Remote Workspace • Biometrics Paused"
            txtCountdown.setTextColor(ContextCompat.getColor(this, R.color.accent_amber_gold))
            return
        }

        // Weekend Week Off status when not actively clocked in
        if (isWeekend && (firstIn == "--:--" || firstIn.isBlank() || exitTime.contains("Weekend", ignoreCase = true) || exitTime.contains("Week Off", ignoreCase = true))) {
            imgNowBarIcon.setImageResource(R.drawable.ic_sparkle)
            imgNowBarIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_lavender))
            txtCountdown.text = "Week Off • Rest Day"
            txtCountdown.setTextColor(ContextCompat.getColor(this, R.color.accent_lavender))
            return
        }

        if (exitTime == "Completed" || progressPercent >= 100) {
            imgNowBarIcon.setImageResource(R.drawable.ic_check_circle)
            imgNowBarIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_emerald))
            txtCountdown.text = "Shift Goal Met"
            txtCountdown.setTextColor(ContextCompat.getColor(this, R.color.accent_emerald))
            updateNudgeOverlayState(animate = true)
            return
        }

        if (exitTime == "--:--" || exitTime.isBlank()) {
            imgNowBarIcon.setImageResource(R.drawable.ic_timer)
            imgNowBarIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_muted))
            txtCountdown.text = "--"
            txtCountdown.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            return
        }

        try {
            val sdf = SimpleDateFormat("hh:mm a", Locale.US)
            val parsed = sdf.parse(exitTime)
            if (parsed != null) {
                val now = Calendar.getInstance()
                val exitCal = Calendar.getInstance().apply {
                    time = parsed
                    set(Calendar.YEAR, now.get(Calendar.YEAR))
                    set(Calendar.MONTH, now.get(Calendar.MONTH))
                    set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
                }

                val diffMs = exitCal.timeInMillis - now.timeInMillis
                if (diffMs <= 0) {
                    imgNowBarIcon.setImageResource(R.drawable.ic_check_circle)
                    imgNowBarIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_emerald))
                    txtCountdown.text = "Shift Goal Met"
                    txtCountdown.setTextColor(ContextCompat.getColor(this, R.color.accent_emerald))
                } else {
                    val totalMinutes = (diffMs / 60000).toInt()
                    val h = totalMinutes / 60
                    val m = totalMinutes % 60
                    imgNowBarIcon.setImageResource(R.drawable.ic_timer)
                    imgNowBarIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_cyan))
                    txtCountdown.text = if (h > 0) "${h}h ${m}m left" else "${m}m left"
                    txtCountdown.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan))
                }
            } else {
                imgNowBarIcon.setImageResource(R.drawable.ic_timer)
                imgNowBarIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_cyan))
                txtCountdown.text = exitTime
                txtCountdown.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan))
            }
        } catch (e: Exception) {
            val remaining = sharedPrefs.getString("WidgetProgressRemaining", "--") ?: "--"
            imgNowBarIcon.setImageResource(R.drawable.ic_timer)
            imgNowBarIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            txtCountdown.text = remaining
            txtCountdown.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun setupBackNavigation() {
        // Intercept Android hardware/gesture back to navigate web history
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isWidgetFullscreen) {
                    webView.evaluateJavascript("if (typeof window.atExitFullscreen === 'function') { window.atExitFullscreen(); }") { null }
                    isWidgetFullscreen = false
                    isSwipeRefreshAllowed = true
                    swipeRefresh.isEnabled = true
                    return
                }
                if (::layoutShiftNudgeOverlay.isInitialized && layoutShiftNudgeOverlay.visibility == View.VISIBLE) {
                    layoutShiftNudgeOverlay.visibility = View.GONE
                    return
                }
                if (layoutSummaryCard.visibility == View.VISIBLE) {
                    dismissSummaryCard()
                    return
                }
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
            btnDesktopMode.setBackgroundResource(R.drawable.bg_header_action_btn)
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
     * Unified Settings & Preferences dialog (Accessible via header Settings button or Title)
     */
    private fun showSettingsDialog() {
        val targetHours = sharedPrefs.getFloat(KEY_TARGET_HOURS, 8.5f)
        val notifComplete = sharedPrefs.getBoolean(KEY_NOTIF_SHIFT_COMPLETE, true)
        val notifPre = sharedPrefs.getBoolean(KEY_NOTIF_PRE_EXIT, true)
        val wfh = sharedPrefs.getBoolean(KEY_WFH_MODE, false)
        val nudgeEnabled = ShiftAlertManager.isNudgeEnabled(this)
        val nudgeInterval = ShiftAlertManager.getIntervalMinutes(this)

        val timesheetEnabled = sharedPrefs.getBoolean(TimesheetReminderWorker.KEY_REMINDER_ENABLED, true)
        val timesheetHour = sharedPrefs.getInt(TimesheetReminderWorker.KEY_REMINDER_HOUR, 21)
        val timesheetMinute = sharedPrefs.getInt(TimesheetReminderWorker.KEY_REMINDER_MINUTE, 0)
        val timesheetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, timesheetHour)
            set(Calendar.MINUTE, timesheetMinute)
        }
        val timesheetFormatted = SimpleDateFormat("hh:mm a", Locale.US).format(timesheetCal.time)
        val timesheetStatus = if (timesheetEnabled) "Daily ($timesheetFormatted)" else "Disabled"

        val lockscreenLiveEnabled = LockScreenLiveCardManager.isLiveCardEnabled(this)
        val lockscreenOngoing = LockScreenLiveCardManager.isLiveCardOngoing(this)
        val liveStatusSummary = if (lockscreenLiveEnabled) {
            if (lockscreenOngoing) "Enabled • Pinned" else "Enabled • Dismissable"
        } else {
            "Disabled"
        }

        val options = arrayOf(
            "Shift Target Hours (Current: ${targetHours}h)",
            "Customize Home Screen Widgets (Theme & Transparency)",
            "Notification Categories & Permissions (Android System)",
            "Lock Screen Live Activity ($liveStatusSummary)",
            "Lock Screen Setup & Help (Samsung & Android)",
            "Daily Timesheet Reminder: $timesheetStatus",
            "Leave Office Alerts: Every ${nudgeInterval} Min (${if (nudgeEnabled) "Enabled" else "Disabled"})",
            if (notifComplete) "Shift Complete Alerts: Enabled" else "Shift Complete Alerts: Disabled",
            if (notifPre) "15-Min Pre-Exit Alerts: Enabled" else "15-Min Pre-Exit Alerts: Disabled",
            if (wfh) "Workspace: Remote (Synced from HRMS)" else "Workspace: In-Office (Synced from HRMS)",
            "View Leave Balances",
            "Check for App Updates",
            "Re-inject Insights Widget",
            "Clear Cache & Relogin",
            "About HRMS"
        )

        AlertDialog.Builder(this, R.style.Theme_HRMS_Dialog)
            .setTitle("Settings and Preferences")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTargetHoursDialog()
                    1 -> startActivity(Intent(this, WidgetConfigureActivity::class.java))
                    2 -> NotificationHelper.openAppNotificationSettings(this)
                    3 -> showLockScreenLiveActivitySettingsDialog()
                    4 -> showLockScreenHelpDialog()
                    5 -> showTimesheetReminderDialog()
                    6 -> showLeaveOfficeAlertDialog()
                    7 -> {
                        val nextState = !notifComplete
                        sharedPrefs.edit().putBoolean(KEY_NOTIF_SHIFT_COMPLETE, nextState).apply()
                        Toast.makeText(
                            this,
                            if (nextState) "Shift Complete alerts enabled" else "Shift Complete alerts disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    8 -> {
                        val nextState = !notifPre
                        sharedPrefs.edit().putBoolean(KEY_NOTIF_PRE_EXIT, nextState).apply()
                        Toast.makeText(
                            this,
                            if (nextState) "Pre-exit packup alerts enabled" else "Pre-exit alerts disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    9 -> {
                        val msg = if (wfh) {
                            "Your workspace is set to Remote (WFH) according to official HRMS records."
                        } else {
                            "Your workspace is set to In-Office according to official HRMS records."
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                    10 -> showLeaveBalanceDialog()
                    11 -> checkForAppUpdate(force = true, isManualClick = true)
                    12 -> {
                        injectScriptFromAssets()
                        Toast.makeText(this, "Insights Widget re-injected!", Toast.LENGTH_SHORT).show()
                    }
                    13 -> clearTokensAndShowLogin()
                    14 -> showAboutDialog()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showLockScreenLiveActivitySettingsDialog() {
        val enabled = LockScreenLiveCardManager.isLiveCardEnabled(this)
        val ongoing = LockScreenLiveCardManager.isLiveCardOngoing(this)
        val channelEnabled = NotificationHelper.isChannelEnabled(this, NotificationHelper.CHANNEL_LIVE_CARD)

        val items = arrayOf(
            if (enabled) "Live Status Notification: Enabled (Tap to turn OFF)" else "Live Status Notification: Disabled (Tap to turn ON)",
            if (ongoing) "Behavior: Pinned / Ongoing (Tap for Dismissable / Swipeable)" else "Behavior: Dismissable / Swipeable (Tap for Pinned)",
            if (channelEnabled) "Manage Category in Android Settings (Allowed)" else "Manage Category in Android Settings (⚠️ Blocked in System Settings)"
        )

        AlertDialog.Builder(this, R.style.Theme_HRMS_Dialog)
            .setTitle("Lock Screen Live Activity")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        val next = !enabled
                        LockScreenLiveCardManager.setLiveCardEnabled(this, next)
                        Toast.makeText(
                            this,
                            if (next) "Live lock screen activity enabled" else "Live lock screen activity disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    1 -> {
                        val nextOngoing = !ongoing
                        LockScreenLiveCardManager.setLiveCardOngoing(this, nextOngoing)
                        Toast.makeText(
                            this,
                            if (nextOngoing) "Live notification is now Pinned (Ongoing)" else "Live notification is now Dismissable (Swipe to Remove)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    2 -> {
                        NotificationHelper.openChannelSettings(this, NotificationHelper.CHANNEL_LIVE_CARD)
                    }
                }
            }
            .setNegativeButton("Done", null)
            .show()
    }

    /**
     * Apple-grade, polished dialog to configure Leave Office alerts & repeating frequency
     */
    private fun showLeaveOfficeAlertDialog() {
        val isEnabled = ShiftAlertManager.isNudgeEnabled(this)
        val currentInterval = ShiftAlertManager.getIntervalMinutes(this)

        val options = arrayOf(
            if (isEnabled) "Alerts: Enabled (Tap to Disable)" else "Alerts: Disabled (Tap to Enable)",
            "Every 2 Minutes" + if (currentInterval == 2) "  ✓" else "",
            "Every 5 Minutes (Default)" + if (currentInterval == 5) "  ✓" else "",
            "Every 10 Minutes" + if (currentInterval == 10) "  ✓" else "",
            "Every 15 Minutes" + if (currentInterval == 15) "  ✓" else "",
            "Send Test Alert (Preview)"
        )

        AlertDialog.Builder(this, R.style.Theme_HRMS_Dialog)
            .setTitle("Leave Office Repeating Alerts")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val next = !isEnabled
                        ShiftAlertManager.setNudgeEnabled(this, next)
                        Toast.makeText(
                            this,
                            if (next) "Leave Office alerts enabled (Every ${currentInterval}m)" else "Leave Office alerts disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                        updateNudgeOverlayState(animate = true)
                        showLeaveOfficeAlertDialog()
                    }
                    1 -> setLeaveAlertInterval(2)
                    2 -> setLeaveAlertInterval(5)
                    3 -> setLeaveAlertInterval(10)
                    4 -> setLeaveAlertInterval(15)
                    5 -> {
                        ShiftAlertManager.sendTestAlert(this)
                        Toast.makeText(this, "Test leave office alert sent! Check your notification panel.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun setLeaveAlertInterval(minutes: Int) {
        ShiftAlertManager.setIntervalMinutes(this, minutes)
        ShiftAlertManager.setNudgeEnabled(this, true)
        Toast.makeText(this, "Alert frequency set to every $minutes minutes", Toast.LENGTH_SHORT).show()
        updateNudgeOverlayState(animate = true)
    }

    private fun showLockScreenHelpDialog() {
        val message = "📱 Universal Lock Screen Live Activity:\n" +
                "• Displays your real-time work duration, departure countdown, and progress bar directly on your Lock Screen and Always On Display (AOD).\n" +
                "• Works natively out of the box on Samsung Galaxy, Google Pixel, OnePlus, Xiaomi, and all Android devices.\n" +
                "• Includes 1-tap lockscreen shortcuts to Refresh and submit Timesheets.\n\n" +
                "⭐ For Samsung Galaxy Users (Good Lock / LockStar):\n" +
                "• Samsung's default lockscreen sub-clock widget selector only allows Samsung system apps.\n" +
                "• To pin any HRMS AppWidget (such as our Lockscreen Pill, Pack-up Clock, or Dashboard) anywhere on your Galaxy lock screen, install 'Good Lock' from Galaxy Store, open 'LockStar', tap the lock screen preview, and tap '+' to add HRMS widgets!"

        AlertDialog.Builder(this, R.style.Theme_HRMS_Dialog)
            .setTitle("Lock Screen Setup & Help")
            .setMessage(message)
            .setPositiveButton("Test Live Card") { _, _ ->
                LockScreenLiveCardManager.setLiveCardEnabled(this, true)
                LockScreenLiveCardManager.updateLiveCard(this)
                Toast.makeText(this, "Live lock screen card refreshed! Lock your phone to view.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showTimesheetReminderDialog() {
        val isEnabled = sharedPrefs.getBoolean(TimesheetReminderWorker.KEY_REMINDER_ENABLED, true)
        val hour = sharedPrefs.getInt(TimesheetReminderWorker.KEY_REMINDER_HOUR, 21)
        val minute = sharedPrefs.getInt(TimesheetReminderWorker.KEY_REMINDER_MINUTE, 0)
        val weekdaysOnly = sharedPrefs.getBoolean(TimesheetReminderWorker.KEY_WEEKDAYS_ONLY, true)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val formattedTime = SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)

        val options = arrayOf(
            if (isEnabled) "Reminder Status: Enabled (Daily)" else "Reminder Status: Disabled",
            "Set Reminder Time: $formattedTime (Tap to Change)",
            "Quick Preset: 07:00 PM",
            "Quick Preset: 08:00 PM",
            "Quick Preset: 09:00 PM (Default IST)",
            "Quick Preset: 10:00 PM",
            if (weekdaysOnly) "Schedule: Weekdays Only (Mon–Fri)" else "Schedule: Every Day (Including Weekends)",
            "Send Test Reminder Notification"
        )

        AlertDialog.Builder(this, R.style.Theme_HRMS_Dialog)
            .setTitle("Daily Timesheet Reminder")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val nextState = !isEnabled
                        sharedPrefs.edit().putBoolean(TimesheetReminderWorker.KEY_REMINDER_ENABLED, nextState).apply()
                        TimesheetReminderWorker.schedule(applicationContext)
                        Toast.makeText(this, if (nextState) "Timesheet reminder enabled for $formattedTime" else "Timesheet reminder disabled", Toast.LENGTH_SHORT).show()
                        showTimesheetReminderDialog()
                    }
                    1 -> {
                        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                            sharedPrefs.edit()
                                .putInt(TimesheetReminderWorker.KEY_REMINDER_HOUR, selectedHour)
                                .putInt(TimesheetReminderWorker.KEY_REMINDER_MINUTE, selectedMinute)
                                .putBoolean(TimesheetReminderWorker.KEY_REMINDER_ENABLED, true)
                                .apply()
                            TimesheetReminderWorker.schedule(applicationContext)
                            val newCal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, selectedHour)
                                set(Calendar.MINUTE, selectedMinute)
                            }
                            val newTimeStr = SimpleDateFormat("hh:mm a", Locale.US).format(newCal.time)
                            Toast.makeText(this, "Reminder scheduled daily at $newTimeStr", Toast.LENGTH_SHORT).show()
                        }, hour, minute, false).show()
                    }
                    2 -> setTimesheetPreset(19, 0)
                    3 -> setTimesheetPreset(20, 0)
                    4 -> setTimesheetPreset(21, 0)
                    5 -> setTimesheetPreset(22, 0)
                    6 -> {
                        val nextWeekdays = !weekdaysOnly
                        sharedPrefs.edit().putBoolean(TimesheetReminderWorker.KEY_WEEKDAYS_ONLY, nextWeekdays).apply()
                        TimesheetReminderWorker.schedule(applicationContext)
                        Toast.makeText(this, if (nextWeekdays) "Reminders set to Weekdays only" else "Reminders set to all 7 days", Toast.LENGTH_SHORT).show()
                        showTimesheetReminderDialog()
                    }
                    7 -> {
                        TimesheetReminderWorker.sendTestReminder(applicationContext)
                        Toast.makeText(this, "Test timesheet reminder sent!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun setTimesheetPreset(hour: Int, minute: Int) {
        sharedPrefs.edit()
            .putInt(TimesheetReminderWorker.KEY_REMINDER_HOUR, hour)
            .putInt(TimesheetReminderWorker.KEY_REMINDER_MINUTE, minute)
            .putBoolean(TimesheetReminderWorker.KEY_REMINDER_ENABLED, true)
            .apply()
        TimesheetReminderWorker.schedule(applicationContext)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val formatted = SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)
        Toast.makeText(this, "Timesheet reminder set to $formatted", Toast.LENGTH_SHORT).show()
    }

    private fun injectLeaveBalanceExtractor() {
        val js = """
            (function() {
                try {
                    var text = document.body ? document.body.innerText : '';
                    var casual = '', sick = '', earned = '';
                    
                    var casualMatch = text.match(/Casual(?:\s+Leave)?\s*[:\n-]?\s*([0-9]+(?:\.[0-9]+)?)/i);
                    if (casualMatch) casual = casualMatch[1];
                    
                    var sickMatch = text.match(/Sick(?:\s+Leave)?\s*[:\n-]?\s*([0-9]+(?:\.[0-9]+)?)/i);
                    if (sickMatch) sick = sickMatch[1];
                    
                    var earnedMatch = text.match(/(?:Earned|Privilege)(?:\s+Leave)?\s*[:\n-]?\s*([0-9]+(?:\.[0-9]+)?)/i);
                    if (earnedMatch) earned = earnedMatch[1];

                    if ((casual || sick || earned) && window.AndroidApp && window.AndroidApp.saveLeaveBalances) {
                        window.AndroidApp.saveLeaveBalances(casual, sick, earned);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun showLeaveBalanceDialog() {
        val casual = sharedPrefs.getString(KEY_LEAVE_CASUAL, "—")
        val sick = sharedPrefs.getString(KEY_LEAVE_SICK, "—")
        val earned = sharedPrefs.getString(KEY_LEAVE_EARNED, "—")

        AlertDialog.Builder(this, R.style.Theme_HRMS_Dialog)
            .setTitle("Leave Balances")
            .setMessage("• Casual Leave: $casual days\n• Sick / Medical Leave: $sick days\n• Earned / Privilege Leave: $earned days\n\n(Balances update automatically when you visit the Leave section)")
            .setPositiveButton("Open Leave Tab") { _, _ ->
                webView.loadUrl("https://apps.pal.tech/hrms/leave")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAboutDialog() {
        val verName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "4.0"
        } catch (e: Exception) {
            "4.0"
        }
        AlertDialog.Builder(this, R.style.Theme_HRMS_Dialog)
            .setTitle("HRMS")
            .setMessage("Version $verName\n\n• Samsung One UI Edge-to-Edge Design\n• Real-time Biometric Tracking\n• Now Bar Live Activity Capsule\n• Home-Screen Widgets (Small, Medium, Large)\n• Desktop & Mobile Viewports\n• Shift Countdown Timer & Daily Summary\n• WFH Mode & Smart Shift Alerts\n• App Shortcuts & Leave Balance Quick View\n• Secure Local Token Storage")
            .setPositiveButton("OK", null)
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

        AlertDialog.Builder(this, R.style.Theme_HRMS_Dialog)
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
        val container = FrameLayout(this).apply {
            setPadding(50, 24, 50, 12)
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(current.toString())
            setSelection(text.length)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_muted))
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_countdown_pill)
            setPadding(32, 20, 32, 20)
        }
        container.addView(input)

        AlertDialog.Builder(this, R.style.Theme_HRMS_Dialog)
            .setTitle("Enter Target Hours (e.g. 8.5)")
            .setView(container)
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

    private fun startAppFlow(customUrl: String? = null) {
        val urlToLoad = if (!customUrl.isNullOrBlank()) customUrl else HOME_URL
        webView.loadUrl(urlToLoad)
        
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

    private fun ensureTrackerScriptInjected() {
        if (!::webView.isInitialized) return
        val curUrl = webView.url
        if (!curUrl.isNullOrBlank() && curUrl.contains("apps.pal.tech") && !curUrl.contains("/login")) {
            webView.evaluateJavascript(
                "(function() { return !!document.getElementById('at-widget-container'); })();"
            ) { result ->
                if (result == null || result == "false" || result == "null") {
                    injectScriptFromAssets()
                }
            }
        }
    }

    private fun injectTokenRefreshObserver() {
        val hookJs = """
            (function() {
                if (window.__hrmsTokenObserverInjected) return;
                window.__hrmsTokenObserverInjected = true;
                var origSetItem = localStorage.setItem;
                localStorage.setItem = function(key, val) {
                    origSetItem.apply(this, arguments);
                    if (key === 'AccessToken' || key === 'RefreshToken') {
                        try {
                            var a = localStorage.getItem('AccessToken') || '';
                            var r = localStorage.getItem('RefreshToken') || '';
                            if (a && window.AndroidApp) {
                                window.AndroidApp.saveTokens(a, r);
                            }
                        } catch(e) {}
                    }
                };
            })();
        """.trimIndent()
        webView.evaluateJavascript(hookJs, null)
    }

    private fun syncCookiesAndTokensFromManager() {
        try {
            val (accessToken, _, cookies) = TokenManager.extractCookiesAndTokensFromManager(applicationContext)
            val hasToken = !accessToken.isNullOrBlank()
            val currentUserId = sharedPrefs.getString(KEY_USER_ID, null)

            if (hasToken || !cookies.isNullOrBlank()) {
                if (currentUserId.isNullOrBlank()) {
                    Thread {
                        TokenManager.fetchUserIdentity(applicationContext, accessToken)
                        runOnUiThread {
                            triggerWidgetRefresh()
                        }
                    }.start()
                }
                scheduleBackgroundWorker()
                triggerWidgetRefresh()
            }
        } catch (e: Exception) {
            android.util.Log.e("HRMSWidget", "Error syncing cookies from manager: ${e.message}")
        }
    }

    private fun extractTokensForBackgroundWorker() {
        // Sync native cookies and tokens directly from CookieManager
        syncCookiesAndTokensFromManager()

        val jsQuery = """
            (function() {
                try {
                    var chatbotInfo = {};
                    try {
                        var rawChat = localStorage.getItem('CHATBOT_WIDGET_USER_INFO');
                        if (rawChat) chatbotInfo = JSON.parse(rawChat);
                    } catch(e) {}

                    return JSON.stringify({
                        accessToken: localStorage.getItem('AccessToken') || '',
                        refreshToken: localStorage.getItem('RefreshToken') || '',
                        userId: localStorage.getItem('at_user_id') || chatbotInfo.employeeId || '',
                        userName: localStorage.getItem('at_user_name') || chatbotInfo.name || '',
                        targetHours: localStorage.getItem('at_target_hours') || '',
                        policyWorkingRate: localStorage.getItem('at_policy_rate') || '',
                        avgBreakMinutes: localStorage.getItem('at_avg_break_min') || ''
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
                    val policyRateStr = jsonObject.optString("policyWorkingRate").trim()
                    val avgBreakStr = jsonObject.optString("avgBreakMinutes").trim()

                    val editor = sharedPrefs.edit()
                    if (accessToken.isNotEmpty() && accessToken != "null") {
                        editor.putString(KEY_ACCESS_TOKEN, accessToken)
                    }
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
                    if (policyRateStr.isNotEmpty() && policyRateStr != "null") {
                        val rate = policyRateStr.toIntOrNull()
                        if (rate != null) {
                            editor.putInt("PolicyWorkingRate", rate)
                        }
                    }
                    if (avgBreakStr.isNotEmpty() && avgBreakStr != "null") {
                        val avgBreak = avgBreakStr.toIntOrNull()
                        if (avgBreak != null) {
                            editor.putInt("AvgBreakMinutes", avgBreak)
                        }
                    }
                    editor.apply()

                    val currentUserId = sharedPrefs.getString(KEY_USER_ID, null)
                    if (currentUserId.isNullOrBlank()) {
                        Thread {
                            TokenManager.fetchUserIdentity(applicationContext)
                            runOnUiThread {
                                triggerWidgetRefresh()
                            }
                        }.start()
                    }

                    scheduleBackgroundWorker()
                    triggerWidgetRefresh()
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
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
        
        webView.loadUrl(LOGIN_URL)
        triggerWidgetRefresh()
    }

    private fun scheduleBackgroundWorker() {
        AttendanceWorker.schedule(applicationContext)
    }

    private fun triggerWidgetRefresh() {
        val intent = Intent(this, AttendanceAppWidgetProvider::class.java).apply {
            action = AttendanceAppWidgetProvider.ACTION_REFRESH
        }
        sendBroadcast(intent)
        updateCountdown()
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
            val targetMin = (hours * 60).toFloat()
            sharedPrefs.edit()
                .putFloat(KEY_TARGET_HOURS, hours.toFloat())
                .putFloat("WidgetTargetMinutes", targetMin)
                .apply()
            triggerWidgetRefresh()
        }

        @JavascriptInterface
        fun saveTokens(accessToken: String, refreshToken: String) {
            sharedPrefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply()
            
            val currentUserId = sharedPrefs.getString(KEY_USER_ID, null)
            if (currentUserId.isNullOrBlank() && accessToken.isNotBlank()) {
                Thread {
                    TokenManager.fetchUserIdentity(applicationContext, accessToken)
                    runOnUiThread {
                        triggerWidgetRefresh()
                    }
                }.start()
            }

            runOnUiThread {
                scheduleBackgroundWorker()
                triggerWidgetRefresh()
            }
        }

        @JavascriptInterface
        fun saveLeaveBalances(casual: String, sick: String, earned: String) {
            val editor = sharedPrefs.edit()
            if (casual.isNotBlank()) editor.putString(KEY_LEAVE_CASUAL, casual)
            if (sick.isNotBlank()) editor.putString(KEY_LEAVE_SICK, sick)
            if (earned.isNotBlank()) editor.putString(KEY_LEAVE_EARNED, earned)
            editor.apply()
        }

        @JavascriptInterface
        fun getWfhMode(): Boolean {
            return sharedPrefs.getBoolean(KEY_WFH_MODE, false)
        }

        @JavascriptInterface
        fun setWfhMode(active: Boolean) {
            val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            sharedPrefs.edit()
                .putBoolean(KEY_WFH_MODE, active)
                .putString(KEY_WFH_DATE, todayDateStr)
                .apply()
            isWfhMode = active
            runOnUiThread {
                updateWfhHubState(animate = true)
                updateCountdown()
                triggerWidgetRefresh()
            }
        }

        @JavascriptInterface
        fun saveImageMapping(optimizedId: String, originalId: String) {
            if (optimizedId.isNotBlank() && originalId.isNotBlank()) {
                sharedPrefs.edit().putString("IMG_MAP_$optimizedId", originalId).apply()
            }
        }

        @JavascriptInterface
        fun openImageFullscreen(imageUrl: String, previewUrl: String?, title: String?) {
            openImageViewer(imageUrl, previewUrl, title)
        }

        @JavascriptInterface
        fun openImageFullscreen(imageUrl: String, title: String?) {
            openImageViewer(imageUrl, null, title)
        }

        @JavascriptInterface
        fun openImageFullscreen(imageUrl: String) {
            openImageViewer(imageUrl, null, null)
        }

        @JavascriptInterface
        fun openExternalLink(url: String) {
            runOnUiThread {
                handleLinkDispatch(url)
            }
        }

        @JavascriptInterface
        fun toggleFullscreenBars() {
            this@MainActivity.toggleFullscreenBars()
        }

        @JavascriptInterface
        fun setSwipeRefreshEnabled(enabled: Boolean) {
            runOnUiThread {
                isSwipeRefreshAllowed = enabled
                swipeRefresh.isEnabled = enabled
            }
        }

        @JavascriptInterface
        fun setWidgetFullscreen(fullscreen: Boolean) {
            runOnUiThread {
                isWidgetFullscreen = fullscreen
                isSwipeRefreshAllowed = !fullscreen
                swipeRefresh.isEnabled = !fullscreen
            }
        }

        @JavascriptInterface
        fun getNativeSettings(): String {
            val nudgeEnabled = ShiftAlertManager.isNudgeEnabled(this@MainActivity)
            val nudgeInterval = ShiftAlertManager.getIntervalMinutes(this@MainActivity)
            val notifComplete = sharedPrefs.getBoolean(KEY_NOTIF_SHIFT_COMPLETE, true)
            val notifPre = sharedPrefs.getBoolean(KEY_NOTIF_PRE_EXIT, true)
            val liveCard = LockScreenLiveCardManager.isLiveCardEnabled(this@MainActivity)
            val liveOngoing = LockScreenLiveCardManager.isLiveCardOngoing(this@MainActivity)
            val channelLiveEnabled = NotificationHelper.isChannelEnabled(this@MainActivity, NotificationHelper.CHANNEL_LIVE_CARD)
            val timesheetEnabled = sharedPrefs.getBoolean(TimesheetReminderWorker.KEY_REMINDER_ENABLED, true)
            val timesheetHour = sharedPrefs.getInt(TimesheetReminderWorker.KEY_REMINDER_HOUR, 21)
            val timesheetMinute = sharedPrefs.getInt(TimesheetReminderWorker.KEY_REMINDER_MINUTE, 0)
            val casual = sharedPrefs.getString(KEY_LEAVE_CASUAL, "—") ?: "—"
            val sick = sharedPrefs.getString(KEY_LEAVE_SICK, "—") ?: "—"
            val earned = sharedPrefs.getString(KEY_LEAVE_EARNED, "—") ?: "—"

            val json = org.json.JSONObject().apply {
                put("leaveAlertEnabled", nudgeEnabled)
                put("leaveAlertInterval", nudgeInterval)
                put("notifShiftComplete", notifComplete)
                put("notifPreExit", notifPre)
                put("liveCardEnabled", liveCard)
                put("liveCardOngoing", liveOngoing)
                put("channelLiveEnabled", channelLiveEnabled)
                put("timesheetEnabled", timesheetEnabled)
                put("timesheetHour", timesheetHour)
                put("timesheetMinute", timesheetMinute)
                put("leaveCasual", casual)
                put("leaveSick", sick)
                put("leaveEarned", earned)
            }
            return json.toString()
        }

        @JavascriptInterface
        fun setLeaveAlerts(enabled: Boolean, interval: Int) {
            ShiftAlertManager.setIntervalMinutes(this@MainActivity, interval)
            ShiftAlertManager.setNudgeEnabled(this@MainActivity, enabled)
            runOnUiThread {
                updateNudgeOverlayState(animate = true)
            }
        }

        @JavascriptInterface
        fun setPreExitAlerts(enabled: Boolean) {
            sharedPrefs.edit().putBoolean(KEY_NOTIF_PRE_EXIT, enabled).apply()
        }

        @JavascriptInterface
        fun setShiftCompleteAlerts(enabled: Boolean) {
            sharedPrefs.edit().putBoolean(KEY_NOTIF_SHIFT_COMPLETE, enabled).apply()
        }

        @JavascriptInterface
        fun setLiveCardEnabled(enabled: Boolean) {
            LockScreenLiveCardManager.setLiveCardEnabled(this@MainActivity, enabled)
            LockScreenLiveCardManager.updateLiveCard(this@MainActivity)
        }

        @JavascriptInterface
        fun setLiveCardOngoing(ongoing: Boolean) {
            LockScreenLiveCardManager.setLiveCardOngoing(this@MainActivity, ongoing)
        }

        @JavascriptInterface
        fun openNotificationSettings() {
            runOnUiThread {
                NotificationHelper.openAppNotificationSettings(this@MainActivity)
            }
        }

        @JavascriptInterface
        fun openNotificationChannelSettings(channelId: String) {
            runOnUiThread {
                val target = if (channelId.isBlank()) NotificationHelper.CHANNEL_LIVE_CARD else channelId
                NotificationHelper.openChannelSettings(this@MainActivity, target)
            }
        }

        @JavascriptInterface
        fun setTimesheetReminder(enabled: Boolean, hour: Int, minute: Int) {
            sharedPrefs.edit()
                .putBoolean(TimesheetReminderWorker.KEY_REMINDER_ENABLED, enabled)
                .putInt(TimesheetReminderWorker.KEY_REMINDER_HOUR, hour)
                .putInt(TimesheetReminderWorker.KEY_REMINDER_MINUTE, minute)
                .apply()
            TimesheetReminderWorker.schedule(applicationContext)
        }

        @JavascriptInterface
        fun openWidgetConfig() {
            runOnUiThread {
                startActivity(Intent(this@MainActivity, WidgetConfigureActivity::class.java))
            }
        }

        @JavascriptInterface
        fun logout() {
            runOnUiThread {
                clearTokensAndShowLogin()
            }
        }
    }

    /**
     * Intelligently dispatches links:
     * - Non-HTTP schemes (mailto, tel, whatsapp, intent) -> system app
     * - Image URLs (.jpg, .png, etc.) -> Fullscreen ImageViewerDialog
     * - External domains -> external browser chooser
     * - Internal HRMS portal -> normal in-app navigation
     */
    fun handleLinkDispatch(url: String): Boolean {
        try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: ""

            // 1. Handle non-HTTP app schemes (e.g. mailto:, tel:, sms:, whatsapp:, intent:)
            if (scheme.isNotEmpty() && scheme != "http" && scheme != "https" && scheme != "file" && scheme != "data" && scheme != "about" && scheme != "javascript") {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "No app available to open this link ($scheme)", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            // 2. Direct Image URLs -> Open in Fullscreen Image Viewer
            if (isImageUrl(url)) {
                openImageViewer(url)
                return true
            }

            // 3. External Domain check -> Open in System Browser
            val host = uri.host?.lowercase() ?: ""
            val isInternal = host.isEmpty() ||
                    host.contains("apps.pal.tech") ||
                    host.contains("pal.tech") ||
                    host.contains("microsoftonline.com") ||
                    host.contains("live.com") ||
                    host.contains("login.") ||
                    host.contains("msftauth.")

            if (!isInternal) {
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(Intent.createChooser(browserIntent, "Open Link in Browser"))
                return true
            }

            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun isImageUrl(url: String): Boolean {
        val cleanUrl = url.split("?")[0].lowercase()
        return cleanUrl.endsWith(".jpg") ||
                cleanUrl.endsWith(".jpeg") ||
                cleanUrl.endsWith(".png") ||
                cleanUrl.endsWith(".webp") ||
                cleanUrl.endsWith(".gif") ||
                cleanUrl.endsWith(".svg") ||
                cleanUrl.endsWith(".bmp")
    }

    fun openImageViewer(url: String, previewUrl: String? = null, title: String? = null) {
        runOnUiThread {
            try {
                ImageViewerDialog(this, url, previewUrl, title).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Could not open image viewer", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openImageViewer(url: String, title: String?) {
        openImageViewer(url, null, title)
    }

    // ============================================================================================
    // Over-The-Air (OTA) App Update Logic with One UI 9 Frosted Glass Badge & 1-Tap Installer
    // ============================================================================================

    private fun setupUpdateBadge() {
        layoutUpdateBadge.setOnClickListener {
            val remoteVer = sharedPrefs.getString(KEY_REMOTE_VERSION_NAME, "") ?: ""
            val remoteNotes = sharedPrefs.getString(KEY_REMOTE_RELEASE_NOTES, "") ?: ""
            val remoteUrl = sharedPrefs.getString(KEY_REMOTE_APK_URL, "") ?: ""
            val remoteSize = sharedPrefs.getString(KEY_REMOTE_APK_SIZE, "") ?: ""
            val releaseDate = sharedPrefs.getString(KEY_REMOTE_RELEASE_DATE, "") ?: ""
            if (remoteUrl.isNotBlank()) {
                showUpdateDialog(remoteVer, remoteNotes, remoteUrl, remoteSize, releaseDate)
            }
        }
    }

    private fun startBeaconAnimation() {
        if (beaconAnimator == null) {
            beaconAnimator = android.animation.ObjectAnimator.ofFloat(viewUpdateBeacon, "alpha", 1f, 0.2f).apply {
                duration = 900
                repeatMode = android.animation.ValueAnimator.REVERSE
                repeatCount = android.animation.ValueAnimator.INFINITE
            }
        }
        if (beaconAnimator?.isStarted != true) {
            beaconAnimator?.start()
        }
    }

    private fun stopBeaconAnimation() {
        beaconAnimator?.cancel()
    }

    private fun checkUpdateWithThrottle(force: Boolean = false, isManualClick: Boolean = false) {
        val now = System.currentTimeMillis()
        val lastCheck = sharedPrefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        if (force || (now - lastCheck) >= UPDATE_CHECK_THROTTLE_MS) {
            checkForAppUpdate(force = force, isManualClick = isManualClick)
        } else {
            evaluateCachedUpdate()
        }
    }

    private fun evaluateCachedUpdate() {
        val remoteVersionCode = sharedPrefs.getInt(KEY_REMOTE_VERSION_CODE, 0)
        val remoteVersionName = sharedPrefs.getString(KEY_REMOTE_VERSION_NAME, "") ?: ""
        val localVersionCode = getAppVersionCode()
        val localVersionName = getAppVersionName()

        if (isUpdateAvailable(remoteVersionCode, remoteVersionName, localVersionCode, localVersionName)) {
            showUpdateBadgeOnUi(remoteVersionName)
        } else {
            layoutUpdateBadge.visibility = View.GONE
            stopBeaconAnimation()
        }
    }

    private fun checkForAppUpdate(force: Boolean = false, isManualClick: Boolean = false) {
        if (isManualClick) {
            Toast.makeText(this, "Checking for latest updates...", Toast.LENGTH_SHORT).show()
        }
        Thread {
            try {
                val url = java.net.URL(VERSION_JSON_URL)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.requestMethod = "GET"
                conn.useCaches = false
                conn.setRequestProperty("Cache-Control", "no-cache")

                if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(response)
                    val remoteVersionCode = json.optInt("versionCode", 0)
                    val remoteVersionName = json.optString("versionName", "")
                    val releaseDate = json.optString("releaseDate", "")
                    val releaseNotes = json.optString("releaseNotes", "")
                    val apkUrl = json.optString("apkUrl", "")
                    val apkSize = json.optString("apkSize", "")

                    val localVersionCode = getAppVersionCode()
                    val localVersionName = getAppVersionName()

                    val now = System.currentTimeMillis()
                    sharedPrefs.edit().putLong(KEY_LAST_UPDATE_CHECK, now).apply()

                    if (isUpdateAvailable(remoteVersionCode, remoteVersionName, localVersionCode, localVersionName)) {
                        sharedPrefs.edit()
                            .putInt(KEY_REMOTE_VERSION_CODE, remoteVersionCode)
                            .putString(KEY_REMOTE_VERSION_NAME, remoteVersionName)
                            .putString(KEY_REMOTE_RELEASE_DATE, releaseDate)
                            .putString(KEY_REMOTE_RELEASE_NOTES, releaseNotes)
                            .putString(KEY_REMOTE_APK_URL, apkUrl)
                            .putString(KEY_REMOTE_APK_SIZE, apkSize)
                            .apply()

                        runOnUiThread {
                            showUpdateBadgeOnUi(remoteVersionName)
                            if (isManualClick) {
                                showUpdateDialog(remoteVersionName, releaseNotes, apkUrl, apkSize, releaseDate)
                            }
                        }
                    } else {
                        runOnUiThread {
                            layoutUpdateBadge.visibility = View.GONE
                            stopBeaconAnimation()
                            if (isManualClick) {
                                Toast.makeText(this@MainActivity, "You're using the latest version ($localVersionName)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else if (isManualClick) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Update check failed (HTTP ${conn.responseCode})", Toast.LENGTH_SHORT).show()
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                if (isManualClick) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Update check failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun getAppVersionCode(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode
            }
        } catch (e: Exception) {
            21
        }
    }

    private fun getAppVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "4.2"
        } catch (e: Exception) {
            "4.2"
        }
    }

    private fun isUpdateAvailable(remoteCode: Int, remoteName: String, localCode: Int, localName: String): Boolean {
        if (remoteCode > localCode && remoteCode > 0) return true
        if (remoteName.isNotBlank() && remoteName != localName) {
            return compareSemVer(remoteName, localName) > 0
        }
        return false
    }

    private fun compareSemVer(v1: String, v2: String): Int {
        val parts1 = v1.replace(Regex("[^0-9.]"), "").split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.replace(Regex("[^0-9.]"), "").split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    private fun showUpdateBadgeOnUi(versionName: String) {
        val displayVer = if (versionName.startsWith("v", ignoreCase = true)) versionName else "v$versionName"
        txtUpdateVersion.text = "$displayVer Available"
        if (layoutUpdateBadge.visibility != View.VISIBLE) {
            layoutUpdateBadge.alpha = 0f
            layoutUpdateBadge.scaleX = 0.85f
            layoutUpdateBadge.scaleY = 0.85f
            layoutUpdateBadge.visibility = View.VISIBLE
            layoutUpdateBadge.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()
        }
        startBeaconAnimation()
    }

    private fun showUpdateDialog(
        remoteVersion: String,
        notes: String,
        apkUrl: String,
        apkSize: String,
        releaseDate: String
    ) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_HRMS_BottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_app_update, null)
        dialog.setContentView(view)

        val txtVersionMeta = view.findViewById<TextView>(R.id.txt_dialog_version_meta)
        val txtReleaseNotes = view.findViewById<TextView>(R.id.txt_dialog_release_notes)
        val btnClose = view.findViewById<ImageButton>(R.id.btn_dialog_close)
        val btnLater = view.findViewById<Button>(R.id.btn_dialog_later)
        val btnInstall = view.findViewById<Button>(R.id.btn_dialog_install)
        val txtBrowser = view.findViewById<TextView>(R.id.txt_dialog_browser_fallback)
        val layoutProgress = view.findViewById<View>(R.id.layout_download_progress)
        val pbProgress = view.findViewById<ProgressBar>(R.id.pb_download_progress)
        val txtStatus = view.findViewById<TextView>(R.id.txt_download_status)
        val txtPercent = view.findViewById<TextView>(R.id.txt_download_percent)

        val displayVer = if (remoteVersion.startsWith("v", ignoreCase = true)) remoteVersion else "v$remoteVersion"
        val metaParts = mutableListOf(displayVer)
        if (apkSize.isNotBlank()) metaParts.add(apkSize)
        if (releaseDate.isNotBlank()) metaParts.add(releaseDate)
        txtVersionMeta.text = metaParts.joinToString(" • ")

        if (notes.isNotBlank()) {
            txtReleaseNotes.text = notes
        } else {
            txtReleaseNotes.text = "Performance enhancements, widget refinements, and stability improvements."
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        btnLater.setOnClickListener { dialog.dismiss() }
        txtBrowser.setOnClickListener {
            dialog.dismiss()
            handleLinkDispatch(apkUrl)
        }

        btnInstall.setOnClickListener {
            startApkDownloadAndInstall(
                apkUrl = apkUrl,
                layoutProgress = layoutProgress,
                pbProgress = pbProgress,
                txtStatus = txtStatus,
                txtPercent = txtPercent,
                btnInstall = btnInstall,
                btnLater = btnLater
            )
        }

        dialog.show()
    }

    private fun startApkDownloadAndInstall(
        apkUrl: String,
        layoutProgress: View,
        pbProgress: ProgressBar,
        txtStatus: TextView,
        txtPercent: TextView,
        btnInstall: Button,
        btnLater: Button
    ) {
        btnInstall.isEnabled = false
        btnInstall.text = "Downloading..."
        btnLater.isEnabled = false
        layoutProgress.visibility = View.VISIBLE
        pbProgress.progress = 0
        txtPercent.text = "0%"
        txtStatus.text = "Connecting to CDN..."

        Thread {
            try {
                val url = java.net.URL(apkUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.connect()

                val fileLength = conn.contentLength
                val updatesDir = java.io.File(cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()
                val apkFile = java.io.File(updatesDir, "hrms-tracker-latest.apk")
                if (apkFile.exists()) apkFile.delete()

                val input = java.io.BufferedInputStream(conn.inputStream)
                val output = java.io.FileOutputStream(apkFile)

                val data = ByteArray(8192)
                var total: Long = 0
                var count: Int
                var lastProgressUpdate = 0L

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    output.write(data, 0, count)

                    val now = System.currentTimeMillis()
                    if (fileLength > 0 && now - lastProgressUpdate > 100) {
                        lastProgressUpdate = now
                        val percent = ((total * 100) / fileLength).toInt()
                        runOnUiThread {
                            pbProgress.progress = percent
                            txtPercent.text = "$percent%"
                            txtStatus.text = "Downloading package... ($percent%)"
                        }
                    }
                }

                output.flush()
                output.close()
                input.close()
                conn.disconnect()

                runOnUiThread {
                    pbProgress.progress = 100
                    txtPercent.text = "100%"
                    txtStatus.text = "Download complete. Starting installer..."
                    btnInstall.text = "Install Now"
                    btnInstall.isEnabled = true
                    btnLater.isEnabled = true
                    launchApkInstaller(apkFile)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    layoutProgress.visibility = View.GONE
                    btnInstall.isEnabled = true
                    btnInstall.text = "Retry Download"
                    btnLater.isEnabled = true
                    Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun launchApkInstaller(apkFile: java.io.File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(this, "Please allow HRMS to install app updates", Toast.LENGTH_LONG).show()
                    val permissionIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(permissionIntent)
                    return
                }
            }

            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
