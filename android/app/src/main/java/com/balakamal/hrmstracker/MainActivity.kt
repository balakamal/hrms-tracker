package com.balakamal.hrmstracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
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
    private lateinit var btnSettings: ImageButton
    private lateinit var appTitle: TextView
    private lateinit var layoutNowBar: View
    private lateinit var imgNowBarIcon: ImageView
    private lateinit var txtCountdown: TextView
    private lateinit var layoutWfhHub: View
    private lateinit var btnSwitchToOffice: Button
    private lateinit var btnDismissWfhCard: ImageButton
    private lateinit var layoutSummaryCard: View
    private lateinit var btnCloseSummary: ImageButton
    private lateinit var txtSummaryFirstIn: TextView
    private lateinit var txtSummaryWorkTime: TextView
    private lateinit var txtSummaryExitTime: TextView
    private lateinit var txtSummaryStatus: TextView
    private lateinit var layoutError: View
    private lateinit var btnRetry: Button

    private var isDesktopMode = false
    private var isWfhMode = false

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
        private const val KEY_LEAVE_CASUAL = "LeaveBalanceCasual"
        private const val KEY_LEAVE_SICK = "LeaveBalanceSick"
        private const val KEY_LEAVE_EARNED = "LeaveBalanceEarned"

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
        isWfhMode = sharedPrefs.getBoolean(KEY_WFH_MODE, false)

        // View initialization
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        layoutTopBar = findViewById(R.id.layout_top_bar)
        btnDesktopMode = findViewById(R.id.btn_desktop_mode)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnSettings = findViewById(R.id.btn_settings)
        appTitle = findViewById(R.id.app_title)
        layoutNowBar = findViewById(R.id.layout_now_bar)
        imgNowBarIcon = findViewById(R.id.img_now_bar_icon)
        txtCountdown = findViewById(R.id.txt_countdown)
        layoutWfhHub = findViewById(R.id.layout_wfh_hub)
        btnSwitchToOffice = findViewById(R.id.btn_switch_to_office)
        btnDismissWfhCard = findViewById(R.id.btn_dismiss_wfh_card)
        layoutSummaryCard = findViewById(R.id.layout_summary_card)
        btnCloseSummary = findViewById(R.id.btn_close_summary)
        txtSummaryFirstIn = findViewById(R.id.txt_summary_first_in)
        txtSummaryWorkTime = findViewById(R.id.txt_summary_work_time)
        txtSummaryExitTime = findViewById(R.id.txt_summary_exit_time)
        txtSummaryStatus = findViewById(R.id.txt_summary_status)
        layoutError = findViewById(R.id.layout_error)
        btnRetry = findViewById(R.id.btn_retry)

        // Dynamic System Window Insets: Pad header below cutouts & float summary card safely
        ViewCompat.setOnApplyWindowInsetsListener(layoutTopBar) { v, insets ->
            val statusBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                v.paddingLeft,
                statusBarInsets.top,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(layoutSummaryCard) { v, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val lp = v.layoutParams as ViewGroup.MarginLayoutParams
            lp.bottomMargin = (16 * resources.displayMetrics.density).toInt() + navInsets.bottom
            v.layoutParams = lp
            insets
        }

        updateWfhHubState(animate = false)

        setupWebView()
        setupTopBarListeners()
        setupWfhHubListeners()
        setupSummaryCard()
        setupBackNavigation()

        requestNotificationPermissions()

        val shortcutUrl = intent?.getStringExtra("shortcut_url")
        startAppFlow(shortcutUrl)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = intent?.getStringExtra("shortcut_url")
        if (!url.isNullOrBlank()) {
            webView.loadUrl(url)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            TokenManager.syncTokensToWebView(webView, applicationContext)
        }
        countdownHandler.removeCallbacks(countdownRunnable)
        countdownHandler.post(countdownRunnable)
    }

    override fun onPause() {
        super.onPause()
        countdownHandler.removeCallbacks(countdownRunnable)
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
        swipeRefresh.setColorSchemeColors(Color.parseColor("#2563EB"))
        swipeRefresh.setProgressBackgroundColorSchemeColor(Color.parseColor("#1A1A26"))
        swipeRefresh.setOnRefreshListener {
            webView.reload()
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
                    // Push rotated tokens from native storage into WebView localStorage
                    TokenManager.syncTokensToWebView(webView, applicationContext)

                    // Inject localStorage hook to catch silent Angular token refreshes in real time
                    injectTokenRefreshObserver()

                    // Extract tokens silently for background notifications if they log in
                    if (url.contains("dashboard") || url.contains("time-sheet") || url.contains("me/timesheet")) {
                        extractTokensForBackgroundWorker()
                    }
                    
                    // Inject the floating widget script
                    injectScriptFromAssets()

                    // Extract leave balances if on the leave page
                    if (url.contains("leave")) {
                        injectLeaveBalanceExtractor()
                    }
                }

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
        }

        // Dedicated Settings & Preferences button with One UI micro-rotation
        btnSettings.setOnClickListener {
            btnSettings.animate()
                .scaleX(0.88f).scaleY(0.88f)
                .rotationBy(45f)
                .setDuration(120)
                .withEndAction {
                    btnSettings.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
                .start()
            showSettingsDialog()
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
        // 1-Tap Switch to Office Mode Button
        btnSwitchToOffice.setOnClickListener {
            btnSwitchToOffice.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).withEndAction {
                btnSwitchToOffice.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()
            toggleWfhMode()
        }

        // Dismiss / Minimize WFH Card
        btnDismissWfhCard.setOnClickListener {
            layoutWfhHub.animate()
                .alpha(0f)
                .translationY(-30f)
                .setDuration(250)
                .withEndAction { layoutWfhHub.visibility = View.GONE }
                .start()
        }

        // Tap on Now Bar Live Activity Capsule opens Workspace Mode Switcher
        layoutNowBar.setOnClickListener {
            showWorkspaceModeSheet()
        }
    }

    private fun updateWfhHubState(animate: Boolean) {
        if (!::layoutWfhHub.isInitialized) return
        if (isWfhMode) {
            layoutWfhHub.visibility = View.VISIBLE
            if (animate) {
                layoutWfhHub.alpha = 0f
                layoutWfhHub.translationY = -40f
                layoutWfhHub.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(350)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
                    .start()
            } else {
                layoutWfhHub.alpha = 1f
                layoutWfhHub.translationY = 0f
            }
        } else {
            if (animate && layoutWfhHub.visibility == View.VISIBLE) {
                layoutWfhHub.animate()
                    .alpha(0f)
                    .translationY(-40f)
                    .setDuration(250)
                    .withEndAction { layoutWfhHub.visibility = View.GONE }
                    .start()
            } else {
                layoutWfhHub.visibility = View.GONE
            }
        }
    }

    private fun showWorkspaceModeSheet() {
        val options = arrayOf(
            if (isWfhMode) "Switch to In-Office Mode (Biometrics Active)" else "Switch to Remote Workspace (WFH Active)",
            "View Settings and Preferences"
        )
        AlertDialog.Builder(this, R.style.Theme_HRMS_Dialog)
            .setTitle("Workspace Mode")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> toggleWfhMode()
                    1 -> showSettingsDialog()
                }
            }
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
        val status = sharedPrefs.getString("WidgetStatusText", "Clocked In") ?: "Clocked In"

        txtSummaryFirstIn.text = firstIn
        txtSummaryWorkTime.text = workTime
        txtSummaryExitTime.text = exitTime
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
        if (isWfhMode) {
            imgNowBarIcon.setImageResource(R.drawable.ic_wfh_home)
            imgNowBarIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_amber_gold))
            txtCountdown.text = "Remote Workspace"
            txtCountdown.setTextColor(ContextCompat.getColor(this, R.color.accent_amber_gold))
            return
        }

        val exitTime = sharedPrefs.getString("WidgetExitTime", "--:--") ?: "--:--"
        val progressPercent = sharedPrefs.getInt("WidgetProgressPercent", 0)

        if (exitTime == "Completed" || progressPercent >= 100) {
            imgNowBarIcon.setImageResource(R.drawable.ic_check_circle)
            imgNowBarIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_emerald))
            txtCountdown.text = "Shift Goal Met"
            txtCountdown.setTextColor(ContextCompat.getColor(this, R.color.accent_emerald))
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

        val options = arrayOf(
            "Shift Target Hours (Current: ${targetHours}h)",
            if (notifComplete) "Shift Complete Alerts: Enabled" else "Shift Complete Alerts: Disabled",
            if (notifPre) "15-Min Pre-Exit Alerts: Enabled" else "15-Min Pre-Exit Alerts: Disabled",
            if (wfh) "Workspace Mode: Remote (Tap for In-Office)" else "Workspace Mode: In-Office (Tap for Remote)",
            "View Leave Balances",
            "Re-inject Insights Widget",
            "Clear Cache & Relogin",
            "About HRMS"
        )

        AlertDialog.Builder(this, R.style.Theme_HRMS_Dialog)
            .setTitle("Settings and Preferences")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTargetHoursDialog()
                    1 -> {
                        val nextState = !notifComplete
                        sharedPrefs.edit().putBoolean(KEY_NOTIF_SHIFT_COMPLETE, nextState).apply()
                        Toast.makeText(
                            this,
                            if (nextState) "Shift Complete alerts enabled" else "Shift Complete alerts disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    2 -> {
                        val nextState = !notifPre
                        sharedPrefs.edit().putBoolean(KEY_NOTIF_PRE_EXIT, nextState).apply()
                        Toast.makeText(
                            this,
                            if (nextState) "Pre-exit packup alerts enabled" else "Pre-exit alerts disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    3 -> toggleWfhMode()
                    4 -> showLeaveBalanceDialog()
                    5 -> {
                        injectScriptFromAssets()
                        Toast.makeText(this, "Insights Widget re-injected!", Toast.LENGTH_SHORT).show()
                    }
                    6 -> clearTokensAndShowLogin()
                    7 -> showAboutDialog()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun toggleWfhMode() {
        isWfhMode = !isWfhMode
        sharedPrefs.edit().putBoolean(KEY_WFH_MODE, isWfhMode).apply()
        updateWfhHubState(animate = true)
        triggerWidgetRefresh()
        updateCountdown()
        val msg = if (isWfhMode) "Remote Workspace activated. Biometrics paused." else "In-Office Mode activated. Biometrics active."
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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
            .setTitle("HRMS App")
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

        // Show Daily Summary Card overlay on app open
        showSummaryCard()
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
            sharedPrefs.edit().putBoolean(KEY_WFH_MODE, active).apply()
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
}
