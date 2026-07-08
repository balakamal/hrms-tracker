# HRMS Attendance Insights

A premium, glassmorphic attendance dashboard and insights utility designed for `apps.pal.tech/hrms`. It features a floating desktop widget (Chrome Extension / Tampermonkey user script) and a native Android wrapper app with a matching home-screen widget.

---

## 🚀 Key Features

### 💻 Draggable Desktop Widget
*   **Real-time Swipes Sync**: Automatically fetches your biometric logs from the backend and calculates your stats on the fly.
*   **Dual Modes**: Can be collapsed into a compact floating badge or expanded into a rich stats card.
*   **Full Draggability**: Drag the widget anywhere on your screen. Positions are remembered across browser sessions using `localStorage` and `PointerEvents`.
*   **Smart Target Settings**: Enter your shift goals using hours/minutes selectors or a custom text field supporting raw values, formulas (e.g. `8*60+30`), and patterns like `5 * 10 * 60`.
*   **Premium Themes**: Glassmorphic styling with switchable Dark and Light modes.

### 📱 Android Application & Widget
*   **Self-Contained Login**: Opens the official login screen securely. Once logged in, it automatically captures authentication tokens for background work.
*   **Background Notifications**: Uses Android `WorkManager` to periodically check your attendance logs and post a push alert when your shift is completed.
*   **Home-Screen Widget**: A premium, rounded layout featuring:
    *   Cyan-to-blue gradient progress bar matching the desktop widget.
    *   Real-time stats: First In, Work Time, Break Time, and Est. Exit Time.
    *   State synchronization: Auto-updates on device boot, app launch, login/logout, settings updates, and periodically in the background.
    *   Timezone safety: Automatically uses the device's local calendar timezone to fetch correct biometric entries.

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
A GitHub Actions pipeline compiles the APK automatically on every push:
1.  Navigate to the **Actions** tab in this GitHub repository.
2.  Click on the latest completed **Build Android APK** workflow run.
3.  Scroll to the **Artifacts** section at the bottom and download `hrms-insights-apk`.
4.  Extract the ZIP and install the APK on your Android device (ensure "Install from Unknown Sources" is enabled in settings).

### 2. Configure the Home-Screen Widget
1.  Long-press on your mobile home screen and select **Widgets**.
2.  Find **HRMS Attendance Insights** under the app widgets list.
3.  Drag and drop the widget onto your home screen.
4.  Open the app and log in. The widget will automatically sync, fetch your real-time hours, and display the shift progress bar immediately!
