# HRMS Attendance Insights

A premium, glassmorphic attendance dashboard and insights utility designed for `apps.pal.tech/hrms`. It features a floating desktop widget (Chrome Extension / Tampermonkey user script) and a native Android wrapper app with a matching home-screen widget.

---

## 🚀 Key Features

### 💻 Draggable Desktop Widget
*   **Real-time Swipes Sync**: Automatically fetches your biometric logs from the backend and calculates your stats on the fly.
*   **Dual Modes**: Can be collapsed into a compact floating badge or expanded into a rich stats card.
*   **Full Draggability**: Drag the widget anywhere on your screen. Positions are remembered across browser sessions using `localStorage` and `PointerEvents`.
*   **Automatic Work From Home (WFH) Detection**: Automatically queries `GetEmployeeStatus` to check for official WFH approval (`WFH: 1`). Displays a sleek WFH status capsule with a vector home glyph in both the floating card and collapsed pill.
*   **Smart Target Settings**: Enter your shift goals using hours/minutes selectors or a custom text field supporting raw values, formulas (e.g. `8*60+30`), and patterns like `5 * 10 * 60`.
*   **Premium Themes**: Glassmorphic styling with switchable Dark and Light modes.

### 📱 Android Application & Widget
*   **Desktop Site Mode Toggle**: Switch between responsive mobile view and full 1280px desktop site layout to access exclusive desktop features (attendance punch regularization, detailed swipe tables, approval grids, and report exports) with smooth pinch-to-zoom.
*   **Quick HRMS Shortcuts**: Direct one-tap jump shortcuts to Timesheet & Swipes, Leave Portal, Payslips & Compensation, Regularization, and Shift Target Hours configuration.
*   **Smart Shift Alerts**: Push notifications for both completed shifts and pre-shift "Pack-Up" warnings (~15-20 minutes remaining).
*   **Fullscreen High-Resolution Image Viewer**: Tap any image on the portal to view in an immersive fullscreen modal with multi-touch pinch-to-zoom (up to 5x), smooth panning, double-tap zoom, single tap to toggle top/bottom controls for edge-to-edge viewing, original high-resolution photo ID resolution, and direct download to your phone's gallery.
*   **Smart Link Dispatching**: Intelligently opens external links and company documents in your system browser without disrupting your portal session. Device action protocols (`mailto:`, `tel:`, `whatsapp:`) dispatch directly to native dialers and apps.
*   **Built-in Download Manager**: Seamlessly download salary payslips, Form 16, and attendance reports directly to Android's `Downloads` folder.
*   **Hardware & On-Screen Back Navigation**: Smooth in-portal web history navigation without accidental app exits.
*   **Loading Progress & Error Recovery**: Integrated progress bar and sleek offline connection retry layout.
*   **Self-Contained Login**: Opens the official login screen securely. Once logged in, it automatically captures authentication tokens for background work.
*   **Automatic 24/7 Session Token Rotation**: Native background token manager proactively and reactively rotates JWT tokens via SSO before expiration, keeping shift alerts and home-screen widgets alive without manual app launches.
*   **Minimalist Home-Screen Widgets**:
    *   **Multiple Responsive Sizes (Large 4x2 Command Center, Medium 4x1 Split Pane, Small 2x1 Badge)**: Automatically adapts layout and fills the widget space with crisp tabular typography and zero awkward truncation.
    *   **Bespoke WFH Status Capsule**: Dedicated vector home icon and warm amber glass capsule (`bg_widget_wfh_badge.xml`) replacing unstyled emojis, with automatic background sync against employee status API.
    *   **Obsidian Glass & Material 3 Expressive System**: Deep dark glass surfaces (`#0D1117`), elevated hero pods (`#161B27`), hairline glass borders, and luminous emerald-to-cyan gradient progress capsules (`#10B981` → `#06B6D4`).
    *   **Unified Metrics Shelf**: High-legibility First In, Total Break, and Estimated Exit tiles paired with subtle micro-icons (`ic_login`, `ic_coffee`, `ic_exit`).
    *   **Immediate Asynchronous Placement**: Uses `goAsync()` to reliably fetch and show real-time stats immediately when placing the widget on the homescreen without falling into offline mode.
    *   **Dynamic Status Feedback**: Sky cyan celebration highlights on shift completion (`#38BDF8`), warm honey amber during breaks & WFH (`#FBBF24`), vibrant emerald when clocked in (`#34D399`), and clean slate gray when synced.
    *   **1-Tap Home-Screen Refresh**: Tactile frosted circular button with clean white vector icon (`@drawable/ic_refresh`) to trigger biometric background sync without opening the app.
    *   **State synchronization**: Auto-updates on device boot, app launch, home-screen refresh, login/logout, and background work checks.

---

## 🛠️ Project Structure

```text
├── android/            # Native Kotlin Android app with home-screen widget
├── hrms-extension/     # Unpacked Chrome Extension files (Manifest V3)
├── hrms-attendance-tracker.js               # Tampermonkey desktop user script
└── hrms-attendance-tracker-extension.zip    # Packaged extension for distribution
```

---

## 💻 Desktop Setup

### Option A: Chrome Extension (Recommended)
1.  Download the repository.
2.  Open Google Chrome and navigate to `chrome://extensions/`.
3.  Enable **Developer mode** using the toggle in the top right.
4.  Click **Load unpacked** in the top left.
5.  Select the `hrms-extension` folder from your downloaded project directory.

### Option B: Tampermonkey Script
1.  Install the Tampermonkey browser extension.
2.  Create a new user script.
3.  Copy and paste the entire contents of [hrms-attendance-tracker.js](file:///c:/Users/kamal.thiruveedhula/Training/InnovateX/hrms-tracker/hrms-attendance-tracker.js) and save it.

---

## 📱 Android Setup

### 1. Install the APK
Download the compiled APK directly:
* **Direct Download**: Download [`builds/hrms-insights-v3.2.apk`](builds/hrms-insights-v3.2.apk) (or [`builds/hrms-tracker-latest.apk`](builds/hrms-tracker-latest.apk))
* Or download from the **Actions** tab in GitHub: Click the latest **Build Android APK** workflow run > Download `hrms-insights-apk`.
* Install the APK on your Android device (ensure "Install from Unknown Sources" is enabled in settings).

### 2. Configure the Home-Screen Widget
1.  Long-press on your mobile home screen and select **Widgets**.
2.  Find **HRMS Attendance Insights** under the app widgets list.
3.  Drag and drop the widget onto your home screen.
4.  Open the app and log in. The widget will automatically sync, fetch your real-time hours, and display the shift progress bar immediately!
