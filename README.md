# HRMS Attendance Tracker & PWA

A premium, glassmorphic attendance dashboard and insights utility designed for `apps.pal.tech/hrms`. It features a floating desktop widget (Chrome Extension / Tampermonkey user script), a mobile Progressive Web App (PWA), a secure desktop-to-mobile token sync flow, and a native Android wrapper app.

## 🚀 Key Features

*   **Draggable Desktop Widget:** Displays First In, Work Time, Break Time, and Est. Exit Time. The widget can be clicked and dragged anywhere on the screen (both collapsed badge and expanded card) and persists its position using `localStorage`.
*   **Flexible Target Settings:** Synced Hours/Minutes dropdown selectors and a Minutes/Formula text field. The text field parses raw values, standard mathematical expressions (e.g. `8*60+30`), and user-friendly patterns like `5 * 10 * 60` (for 5 hours and 10 minutes).
*   **Dark & Light Themes:** Premium Dark and Light modes switchable via a toggle in the settings panel and persisted in `localStorage`.
*   **Chrome Extension:** Packaged Manifest V3 extension runs out-of-the-box in Chrome without Tampermonkey.
*   **Mobile PWA:** Fully responsive, dark-mode dashboard featuring a circular progress chart, daily swipes timeline, and direct API synchronization.
*   **Seamless Token Sync:** Instantly sync credentials from the desktop widget to the PWA using a secure QR code or link—no manual token copying required.
*   **Android Wrapper App:** Kotlin-based WebView app wrapper compiled directly via GitHub Actions.

---

## 🛠️ Project Structure

```text
├── android/            # Native Kotlin Android wrapper app
├── hrms-extension/     # Unpacked Chrome Extension files (Manifest V3)
├── hrms-pwa/           # Mobile PWA source (HTML, CSS, JS, manifest, SW)
├── hrms-attendance-tracker.js               # Tampermonkey desktop user script
├── hrms-attendance-tracker-extension.zip    # Packaged extension for distribution
└── walkthrough.md                           # Detailed setup guide
```

---

## 💻 Desktop Setup

### Options:
*   **Option A: Chrome Extension (Recommended)**
    1.  Go to `chrome://extensions/` in Google Chrome.
    2.  Enable **Developer mode** (top right).
    3.  Click **Load unpacked** (top left).
    4.  Select the `hrms-extension` directory.
*   **Option B: Tampermonkey Script**
    *   Install the Tampermonkey browser extension and load `hrms-attendance-tracker.js`.

---

## 📱 Mobile PWA & Android Setup

### 1. Enable GitHub Pages
1.  Navigate to your repository **Settings -> Pages**.
2.  Set the build source to the **main** branch and `/` (root), then click **Save**.
3.  The PWA will deploy to: `https://balakamal.github.io/hrms-tracker/hrms-pwa/`

### 2. Desktop-to-Mobile Token Sync
1.  Open the HRMS dashboard on your desktop.
2.  Click the **Mobile Sync (phone icon)** button in the header of the insights card.
3.  Scan the **QR Code** using your phone's camera (or copy the generated sync link).
4.  The PWA will open, securely import your authentication tokens, and load your live metrics.

### 3. Native Android APK
A GitHub Actions workflow builds a native Android APK automatically on every push:
1.  Go to the **Actions** tab in this GitHub repository.
2.  Click on the latest completed **Build Android APK** workflow run.
3.  Scroll down to the **Artifacts** section at the bottom and download `hrms-insights-apk`.
