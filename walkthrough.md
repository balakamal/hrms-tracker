# Walkthrough & User Guide - HRMS Attendance Tracker

This guide details how to set up, customize, and share the new real-time floating attendance dashboard.

## Features Implemented
1. **First Clock-In Tracking:** Displays the exact time of your first swipe-in of the day.
2. **Biometric Hours (Work Time):** Shows the exact sum of time spent in the office (excluding breaks), calculated in real-time if currently clocked in.
3. **Total Break Time:** Dynamically computes total time spent out of the office between work sessions.
4. **Estimated Exit Time:** Displays when you can clock out based on your customized target work hours and remaining biometric minutes.
5. **Real-time Notifications:** Alerts you every 5 minutes when your target is reached, until you either clock out, log out, or toggle off notifications.
6. **Fully Customizable Settings:** Settings panel directly built-in to toggle notifications, change target hours, or manually override the user ID.

---

## Installation Instructions

You can inject this code using any user-script manager extension (like **Tampermonkey** or **Violentmonkey**), or using the **User JavaScript and CSS** Chrome extension.

### Method 1: Via Tampermonkey / Violentmonkey (Recommended)
1. Install [Tampermonkey](https://chromewebstore.google.com/detail/tampermonkey/dhdgffkkebhmkfjojejmpbldmpobfkfo) from the Chrome Web Store.
2. Click the Tampermonkey extension icon and choose **Create a new script**.
3. Clear the template and copy-paste the entire contents of [hrms-attendance-tracker.js](file:///c:/Users/kamal.thiruveedhula/Training/InnovateX/Vectors%20in%20Postgress/hrms-attendance-tracker.js) into the editor.
4. Click **File -> Save** (or `Ctrl + S`).
5. Open/Reload your HRMS dashboard: `https://apps.pal.tech/hrms/me/time-sheet` or `https://apps.pal.tech/hrms/dashboard`.
6. You will see the floating dashboard widget in the bottom-right corner!

### Method 2: Via "User JavaScript and CSS" Extension
1. Install [User JavaScript and CSS](https://chromewebstore.google.com/detail/user-javascript-and-css/nbhcbdgdggolhdglhlbgglhannlbocle) from the Chrome Web Store.
2. Navigate to `https://apps.pal.tech/hrms/me/time-sheet`.
3. Click the extension icon and select **Add New**.
4. Paste the entire JavaScript code (excluding the `// ==UserScript==` header lines if desired) into the JS pane.
5. Leave the CSS pane blank (since CSS is injected automatically by the script).
6. Click **Save** in the extension editor.

---

## How to Customize

1. Click the **Settings Gear** icon in the header of the floating card.
2. **Target Work:** Adjust your daily office hours requirement (default is `8.5` hours).
3. **Enable Notifications:** Toggle whether desktop notifications are sent every 5 minutes once target hours are completed.
4. **User ID:** This is auto-detected. If you wish to track another colleague's status (or override the auto-detection), you can enter their numeric User ID and click **Save & Apply**.
5. Save settings. Configurations are stored locally in the browser's `localStorage`.

---

## Packaging as a Chrome Extension (Manifest V3)

I have fully packaged this tool as a standalone Chrome Extension and saved it to your workspace:
* **Extension ZIP Package:** [hrms-attendance-tracker-extension.zip](file:///C:/Users/kamal.thiruveedhula/Training/InnovateX/hrms-tracker/hrms-attendance-tracker-extension.zip)
* **Unpacked Folder:** [hrms-extension](file:///C:/Users/kamal.thiruveedhula/Training/InnovateX/hrms-tracker/hrms-extension)

### How to Install and Test Locally (Developer Mode)
1. Open Google Chrome and navigate to `chrome://extensions/`.
2. Enable **Developer mode** (toggle in the top-right corner).
3. Click **Load unpacked** (top-left button).
4. Select the `hrms-extension` directory: `C:\Users\kamal.thiruveedhula\Training\InnovateX\hrms-tracker\hrms-extension`.
5. The extension is now loaded and will automatically run on the HRMS pages without needing Tampermonkey!

### How to Publish to the Chrome Web Store
To share the extension publicly or within your organization via the Chrome Web Store:
1. Go to the [Chrome Web Store Developer Console](https://chrome.google.com/webstore/devconsole).
2. Sign in with a developer account (requires a one-time $5 registration fee from Google).
3. Click **Add new item**.
4. Upload the packaged ZIP file: `C:\Users\kamal.thiruveedhula\Training\InnovateX\hrms-tracker\hrms-attendance-tracker-extension.zip`.
5. Complete the store listing details (icons, description, screenshots).
6. Submit for review!

---

### 📱 Native Android App (In-App Login, Splash Screen, Custom Icons & Push Notifications)

We upgraded the Android Kotlin app wrapper to fully support mobile-only operations and comply with modern Android standards:
1. **Target SDK 34 Upgrade:** Upgraded compile and target SDKs to **34** (Android 14) to satisfy OS installation/privacy guidelines and prevent device blocks.
2. **Custom Branding:** Created standard and round app launcher icons (`ic_launcher.png` and `ic_launcher_round.png`) across all density buckets using the PWA logo.
3. **Premium Splash Screen:** Integrated the AndroidX Splash Screen library to render a dark-mode splash screen (`#0f0f14`) displaying the app logo on launch.
4. **Self-Contained Login:** Automatically opens `https://apps.pal.tech/hrms/login` if no credentials exist.
5. **Automatic Token Extraction:** Extracts the authentication tokens from `localStorage` using JS injection once logged in.
6. **Background Push Notifications:** Employs Android `WorkManager` to run periodic background checks and alert you when your shift targets are completed.

---

## Live Injection Verification (Desktop Widget)

I have successfully injected the script into your active browser tab `Me | Timesheet` (`53839B6E2AF82BE43507DA8A00236A19`) and captured a screenshot. You will see the floating glassmorphic **Attendance Insights** widget at the bottom right of your screen:

![Attendance Insights Injected Widget UI](C:/Users/kamal.thiruveedhula/.gemini/antigravity-ide/brain/29258d3e-1a79-437d-ac08-96d9a71b1440/injected_screenshot.png)


