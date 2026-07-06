// ==UserScript==
// @name         HRMS Attendance Tracker
// @namespace    http://tampermonkey.net/
// @version      1.0
// @description  A premium, customizable floating dashboard to track biometric office hours, breaks, and exit time in real-time.
// @author       Antigravity
// @match        https://apps.pal.tech/hrms/*
// @grant        none
// ==UserScript==

/* jshint esversion: 8 */
/* eslint-env es8, browser */

(function () {
  'use strict';

  // --- STYLES INJECTION ---
  const styles = `
    /* Widget Container */
    #at-widget-container {
      position: fixed;
      bottom: 24px;
      right: 24px;
      z-index: 99999;
      font-family: 'Outfit', 'Inter', system-ui, -apple-system, sans-serif;
      color: #f3f4f6;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }

    /* Floating Badge Mode */
    .at-badge {
      display: flex;
      align-items: center;
      gap: 8px;
      background: linear-gradient(135deg, rgba(20, 20, 25, 0.85) 0%, rgba(30, 30, 35, 0.85) 100%);
      backdrop-filter: blur(12px) saturate(180%);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 50px;
      padding: 10px 18px;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.4);
      cursor: pointer;
      user-select: none;
      transition: all 0.2s ease;
    }
    .at-badge:hover {
      transform: translateY(-2px);
      border-color: rgba(255, 255, 255, 0.2);
      box-shadow: 0 12px 35px rgba(0, 0, 0, 0.5);
    }
    .at-badge svg {
      width: 20px;
      height: 20px;
      fill: #00f2fe;
    }
    .at-badge-text {
      font-weight: 600;
      font-size: 14px;
      letter-spacing: 0.5px;
    }

    /* Main Dashboard Card */
    .at-card {
      width: 320px;
      background: linear-gradient(135deg, rgba(15, 15, 20, 0.9) 0%, rgba(25, 25, 30, 0.9) 100%);
      backdrop-filter: blur(16px) saturate(180%);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 20px;
      padding: 20px;
      box-shadow: 0 15px 40px rgba(0, 0, 0, 0.5);
      position: relative;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      gap: 16px;
      transition: all 0.3s ease;
    }

    /* Header */
    .at-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .at-title-group {
      display: flex;
      flex-direction: column;
    }
    .at-title {
      font-weight: 700;
      font-size: 16px;
      letter-spacing: -0.2px;
      background: linear-gradient(135deg, #00f2fe 0%, #4facfe 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    .at-subtitle {
      font-size: 11px;
      color: #9ca3af;
      font-weight: 500;
      margin-top: 2px;
    }
    .at-header-actions {
      display: flex;
      gap: 8px;
      align-items: center;
    }
    .at-btn {
      background: transparent;
      border: none;
      color: #9ca3af;
      cursor: pointer;
      padding: 4px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s ease;
    }
    .at-btn:hover {
      background: rgba(255, 255, 255, 0.08);
      color: #ffffff;
    }
    .at-btn svg {
      width: 18px;
      height: 18px;
      fill: currentColor;
    }

    /* Status Banner */
    .at-status-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      background: rgba(255, 255, 255, 0.04);
      padding: 8px 12px;
      border-radius: 10px;
      font-size: 12px;
      font-weight: 600;
    }
    .at-status-indicator {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      display: inline-block;
    }
    .at-status-in { background-color: #10b981; box-shadow: 0 0 8px #10b981; }
    .at-status-out { background-color: #f59e0b; box-shadow: 0 0 8px #f59e0b; }
    .at-status-done { background-color: #3b82f6; box-shadow: 0 0 8px #3b82f6; }

    /* Stats Grid */
    .at-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }
    .at-item {
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid rgba(255, 255, 255, 0.03);
      padding: 12px;
      border-radius: 12px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .at-label {
      font-size: 10px;
      color: #9ca3af;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .at-label svg {
      width: 12px;
      height: 12px;
      fill: currentColor;
    }
    .at-val {
      font-size: 15px;
      font-weight: 700;
      color: #f3f4f6;
    }
    .at-val.highlight-work {
      color: #00f2fe;
    }
    .at-val.highlight-exit {
      color: #a78bfa;
    }

    /* Progress Segment */
    .at-progress-sec {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .at-progress-track {
      height: 6px;
      background: rgba(255, 255, 255, 0.08);
      border-radius: 10px;
      overflow: hidden;
    }
    .at-progress-fill {
      height: 100%;
      width: 0%;
      background: linear-gradient(90deg, #00f2fe 0%, #4facfe 100%);
      border-radius: 10px;
      transition: width 0.5s ease;
    }
    .at-progress-labels {
      display: flex;
      justify-content: space-between;
      font-size: 11px;
      font-weight: 600;
      color: #9ca3af;
    }

    /* Settings Panel */
    .at-settings {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: #111115;
      border-radius: 20px;
      padding: 20px;
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
      gap: 14px;
      z-index: 10;
      transition: all 0.3s ease;
      transform: translateY(100%);
    }
    .at-settings.active {
      transform: translateY(0);
    }
    .at-settings-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);
      padding-bottom: 8px;
    }
    .at-settings-title {
      font-weight: 700;
      font-size: 14px;
      color: #ffffff;
    }
    .at-form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .at-form-group.row {
      flex-direction: row;
      align-items: center;
      justify-content: space-between;
      margin-top: 4px;
    }
    .at-form-group label {
      font-size: 11px;
      color: #9ca3af;
      font-weight: 600;
    }
    .at-input {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 8px;
      padding: 8px 12px;
      color: #ffffff;
      font-size: 13px;
      outline: none;
      font-family: inherit;
    }
    .at-input:focus {
      border-color: #00f2fe;
    }
    .at-toggle {
      position: relative;
      display: inline-block;
      width: 36px;
      height: 20px;
    }
    .at-toggle input {
      opacity: 0;
      width: 0;
      height: 0;
    }
    .at-slider {
      position: absolute;
      cursor: pointer;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: #374151;
      transition: .3s;
      border-radius: 20px;
    }
    .at-slider:before {
      position: absolute;
      content: "";
      height: 14px;
      width: 14px;
      left: 3px;
      bottom: 3px;
      background-color: white;
      transition: .3s;
      border-radius: 50%;
    }
    input:checked + .at-slider {
      background-color: #00f2fe;
    }
    input:checked + .at-slider:before {
      transform: translateX(16px);
    }
    .at-btn-save {
      background: linear-gradient(135deg, #00f2fe 0%, #4facfe 100%);
      color: #000000;
      border: none;
      border-radius: 8px;
      padding: 10px;
      font-weight: 700;
      font-size: 12px;
      cursor: pointer;
      margin-top: auto;
      transition: opacity 0.2s;
    }
    .at-btn-save:hover {
      opacity: 0.9;
    }

    /* Sync Panel */
    .at-sync {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: #111115;
      border-radius: 20px;
      padding: 16px;
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
      gap: 10px;
      z-index: 12;
      transition: all 0.3s ease;
      transform: translateY(100%);
    }
    .at-sync.active {
      transform: translateY(0);
    }
    .at-sync-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);
      padding-bottom: 6px;
    }
    .at-sync-title {
      font-weight: 700;
      font-size: 14px;
      color: #ffffff;
    }
    .at-qr-container {
      display: flex;
      justify-content: center;
      align-items: center;
      background: #ffffff;
      padding: 6px;
      border-radius: 10px;
      width: 112px;
      height: 112px;
      margin: 0 auto;
    }
    .at-qr-container img {
      width: 100px;
      height: 100px;
    }
    .at-sync-link-row {
      display: flex;
      gap: 6px;
      align-items: center;
    }
    .at-sync-text {
      font-size: 11px;
      color: #9ca3af;
      text-align: center;
      line-height: 1.3;
    }
  `;

  // --- SVG ICONS ---
  const ICONS = {
    clock: `<svg viewBox="0 0 24 24"><path d="M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm1 10.586l-2.793-2.793a1 1 0 0 1 1.414-1.414L13 9.586l4.207-4.207a1 1 0 0 1 1.414 1.414L13 12.586z"/></svg>`,
    login: `<svg viewBox="0 0 24 24"><path d="M19 3H5c-1.1 0-2 .9-2 2v4h2V5h14v14H5v-4H3v4c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-8 11.586l3.293-3.293a1 1 0 0 0 0-1.414L11 6.586 9.586 8l2.293 2.293H3v2h8.879L9.586 14.586 11 16z"/></svg>`,
    work: `<svg viewBox="0 0 24 24"><path d="M20 6h-4V4c0-1.11-.89-2-2-2h-4c-1.11 0-2 .89-2 2v2H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-6 0h-4V4h4v2z"/></svg>`,
    coffee: `<svg viewBox="0 0 24 24"><path d="M2 21h18v-2H2v2zM20 8h-2V5h2v3zm-4-5H4c-1.1 0-2 .9-2 2v8c0 2.2 1.8 4 4 4h8c2.2 0 4-1.8 4-4v-3h2c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"/></svg>`,
    exit: `<svg viewBox="0 0 24 24"><path d="M10.09 15.59L11.5 17l5-5-5-5-1.41 1.41L12.67 11H3v2h9.67l-2.58 2.59zM19 3H5c-1.11 0-2 .9-2 2v4h2V5h14v14H5v-4H3v4c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"/></svg>`,
    gear: `<svg viewBox="0 0 24 24"><path d="M19.43 12.98c.04-.32.07-.64.07-.98s-.03-.66-.07-.98l2.11-1.65c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.3-.61-.22l-2.49 1c-.52-.4-1.08-.73-1.69-.98l-.38-2.65C14.46 2.18 14.25 2 14 2h-4c-.25 0-.46.18-.49.42l-.38 2.65c-.61.25-1.17.59-1.69.98l-2.49-1c-.23-.09-.49 0-.61.22l-2 3.46c-.13.22-.07.49.12.64l2.11 1.65c-.04.32-.07.65-.07.98s.03.66.07.98l-2.11 1.65c-.19.15-.24.42-.12.64l2 3.46c.12.22.39.3.61.22l2.49-1c.52.4 1.08.73 1.69.98l.38 2.65c.03.24.24.42.49.42h4c.25 0 .46-.18.49-.42l.38-2.65c.61-.25 1.17-.59 1.69-.98l2.49 1c.23.09.49 0 .61-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.65zM12 15.5c-1.93 0-3.5-1.57-3.5-3.5s1.57-3.5 3.5-3.5 3.5 1.57 3.5 3.5-1.57 3.5-3.5 3.5z"/></svg>`,
    minimize: `<svg viewBox="0 0 24 24"><path d="M19 13H5v-2h14v2z"/></svg>`,
    phone: `<svg viewBox="0 0 24 24"><path d="M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z"/></svg>`
  };

  // --- STATE MANAGEMENT ---
  let state = {
    userId: localStorage.getItem('at_userid_override') || null,
    userName: 'User',
    targetHours: parseFloat(localStorage.getItem('at_target_hours') || '8.5'),
    notifyEnabled: localStorage.getItem('at_notify_enabled') !== 'false',
    pwaUrl: localStorage.getItem('at_pwa_url') || 'https://balakamal.github.io/hrms-tracker/hrms-pwa/',
    isMinimized: localStorage.getItem('at_is_minimized') === 'true',
    attendanceData: null,
    lastNotificationTime: 0,
    notificationInterval: 5 * 60 * 1000 // 5 minutes in milliseconds
  };

  // --- UTILS ---
  const formatMinutes = (totalMin) => {
    const h = Math.floor(totalMin / 60);
    const m = Math.floor(totalMin % 60);
    return `${h}h ${m}m`;
  };

  const getTodayLocalDateStr = () => {
    const today = new Date();
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  };

  const getTodayISO = () => {
    return `${getTodayLocalDateStr()}T00:00:00.000Z`;
  };

  // --- SERVICE METHODS ---
  async function fetchUserIdentity() {
    if (state.userId) return; // Custom ID override already set
    const url = "https://apps.pal.tech/hrms-backend/api/Employee/GetMyDetails";
    const token = localStorage.getItem("AccessToken");
    if (!token) return;

    try {
      const response = await fetch(url, {
        headers: { "Authorization": `Bearer ${token}`, "Accept": "application/json" }
      });
      const resData = await response.json();
      if (resData && resData.data) {
        state.userId = resData.data.id;
        state.userName = resData.data.name || resData.data.firstName || 'User';
        const subtitleEl = document.querySelector('.at-subtitle');
        if (subtitleEl) subtitleEl.innerText = state.userName;
      }
    } catch (e) {
      console.error("Error fetching user details in tracker script:", e);
    }
  }

  async function fetchAttendanceLogs() {
    if (!state.userId) await fetchUserIdentity();
    if (!state.userId) return;

    const todayDate = getTodayISO();
    const url = `https://apps.pal.tech/hrms-backend/api/Attendance/GetDailyLog?date=${todayDate}&userId=${state.userId}`;
    const token = localStorage.getItem("AccessToken");
    if (!token) return;

    try {
      const response = await fetch(url, {
        headers: { "Authorization": `Bearer ${token}`, "Accept": "application/json" }
      });
      const resData = await response.json();
      if (resData && resData.data) {
        state.attendanceData = resData.data;
        updateUI();
      }
    } catch (e) {
      console.error("Error fetching attendance logs in tracker script:", e);
    }
  }

  // Calculate work metrics in real-time
  function calculateMetrics() {
    if (!state.attendanceData) return null;

    const logs = state.attendanceData.attendanceDailyLogs || [];
    if (logs.length === 0) return null;

    const todayStr = getTodayLocalDateStr();
    const parseTime = (timeStr) => new Date(`${todayStr}T${timeStr}`);

    const parsedLogs = logs.map(l => ({
      time: parseTime(l.time),
      isIn: l.isIn, // 0 = In, 1 = Out
      location: l.location
    })).sort((a, b) => a.time - b.time);

    const firstClockIn = parsedLogs[0].time;
    let workDurationMs = 0;
    let lastInTime = null;
    let lastOutTime = null;
    let lastLocation = 'Office';

    for (let i = 0; i < parsedLogs.length; i++) {
      const log = parsedLogs[i];
      if (log.isIn === 0) {
        lastInTime = log.time;
        lastLocation = log.location || lastLocation;
      } else if (log.isIn === 1 && lastInTime) {
        workDurationMs += (log.time - lastInTime);
        lastOutTime = log.time;
        lastInTime = null;
      }
    }

    const isClockedIn = lastInTime !== null;
    const now = new Date();

    if (isClockedIn) {
      workDurationMs += (now - lastInTime);
    }

    const totalWorkMinutes = workDurationMs / 60000;
    const targetMinutes = state.targetHours * 60;
    const remainingMinutes = Math.max(0, targetMinutes - totalWorkMinutes);
    const completed = totalWorkMinutes >= targetMinutes;

    // Break time: elapsed time up to now (or last clock-out) minus work time
    const endTimestamp = isClockedIn ? now : (lastOutTime || firstClockIn);
    const totalElapsedMs = endTimestamp - firstClockIn;
    const totalBreakMinutes = Math.max(0, (totalElapsedMs - workDurationMs) / 60000);

    let exitTime = 'Completed';
    if (!completed) {
      const exitDate = new Date(now.getTime() + remainingMinutes * 60000);
      exitTime = exitDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    return {
      firstClockIn: firstClockIn.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      workMinutes: totalWorkMinutes,
      breakMinutes: totalBreakMinutes,
      remainingMinutes: remainingMinutes,
      exitTime: exitTime,
      isClockedIn: isClockedIn,
      completed: completed,
      lastLocation: lastLocation
    };
  }

  // --- UI CONTROLLER ---
  let elements = {};

  function buildUI() {
    // Inject style sheet
    const styleEl = document.createElement('style');
    styleEl.innerHTML = styles;
    document.head.appendChild(styleEl);

    // Create Main Container
    const container = document.createElement('div');
    container.id = 'at-widget-container';
    document.body.appendChild(container);
    elements.container = container;

    // 1. Badge View (Collapsed)
    const badge = document.createElement('div');
    badge.className = 'at-badge';
    badge.innerHTML = `${ICONS.clock}<span class="at-badge-text" id="at-badge-work-time">--h --m</span>`;
    badge.addEventListener('click', toggleCollapse);
    container.appendChild(badge);
    elements.badge = badge;

    // 2. Card View (Expanded)
    const card = document.createElement('div');
    card.className = 'at-card';
    card.style.display = 'none';
    card.innerHTML = `
      <div class="at-header">
        <div class="at-title-group">
          <span class="at-title">Attendance Insights</span>
          <span class="at-subtitle">${state.userName}</span>
        </div>
        <div class="at-header-actions">
          <button class="at-btn" id="at-btn-settings" title="Settings">${ICONS.gear}</button>
          <button class="at-btn" id="at-btn-minimize" title="Minimize">${ICONS.minimize}</button>
        </div>
      </div>

      <div class="at-status-banner">
        <span class="at-status-indicator at-status-out" id="at-status-indicator"></span>
        <span id="at-status-text">Determining status...</span>
      </div>

      <div class="at-grid">
        <div class="at-item">
          <span class="at-label">${ICONS.login} First In</span>
          <span class="at-val" id="at-val-first-in">--:--</span>
        </div>
        <div class="at-item">
          <span class="at-label">${ICONS.work} Work Time</span>
          <span class="at-val highlight-work" id="at-val-work-time">--h --m</span>
        </div>
        <div class="at-item">
          <span class="at-label">${ICONS.coffee} Total Break</span>
          <span class="at-val" id="at-val-break-time">--h --m</span>
        </div>
        <div class="at-item">
          <span class="at-label">${ICONS.exit} Est. Exit</span>
          <span class="at-val highlight-exit" id="at-val-exit-time">--:--</span>
        </div>
      </div>

      <div class="at-progress-sec">
        <div class="at-progress-track">
          <div class="at-progress-fill" id="at-progress-fill"></div>
        </div>
        <div class="at-progress-labels">
          <span id="at-progress-percentage">0% Completed</span>
          <span id="at-progress-remaining">--h --m left</span>
        </div>
      </div>

      <!-- Settings Subpanel -->
      <div class="at-settings" id="at-settings-panel">
        <div class="at-settings-header">
          <span class="at-settings-title">Tracker Settings</span>
          <button class="at-btn" id="at-btn-close-settings">${ICONS.minimize}</button>
        </div>
        <div class="at-form-group">
          <label>Target Work (Hours)</label>
          <input type="number" class="at-input" id="at-setting-hours" step="0.5" min="1" max="24" value="${state.targetHours}">
        </div>
        <div class="at-form-group row">
          <label>Enable Notifications</label>
          <label class="at-toggle">
            <input type="checkbox" id="at-setting-notify" ${state.notifyEnabled ? 'checked' : ''}>
            <span class="at-slider"></span>
          </label>
        </div>
        <div class="at-form-group">
          <label>User ID (Override)</label>
          <input type="text" class="at-input" id="at-setting-userid" value="${localStorage.getItem('at_userid_override') || ''}" placeholder="Leave blank for Auto">
        </div>
        <div class="at-form-group">
          <label>Mobile PWA URL</label>
          <input type="text" class="at-input" id="at-setting-pwaurl" value="${state.pwaUrl}" placeholder="PWA URL for mobile sync">
        </div>
        <button class="at-btn-save" id="at-btn-save-settings">Save & Apply</button>
      </div>

      <!-- Mobile Sync Subpanel -->
      <div class="at-sync" id="at-sync-panel">
        <div class="at-sync-header">
          <span class="at-sync-title">Sync to Mobile PWA</span>
          <button class="at-btn" id="at-btn-close-sync">${ICONS.minimize}</button>
        </div>
        <p class="at-sync-text">Scan this QR code with your phone's camera, or copy the link below to open the mobile PWA with your token pre-synced.</p>
        <div class="at-qr-container">
          <img id="at-sync-qr-img" src="" alt="Sync QR Code">
        </div>
        <div class="at-sync-link-row">
          <input type="text" class="at-input" id="at-sync-link-input" style="flex: 1; font-size: 11px; padding: 6px;" readonly>
          <button class="at-btn-save" id="at-btn-copy-sync-link" style="margin: 0; padding: 6px 10px; font-size: 11px;">Copy</button>
        </div>
      </div>
    `;
    container.appendChild(card);
    elements.card = card;

    // Apply layout state
    if (state.isMinimized) {
      elements.badge.style.display = 'flex';
      elements.card.style.display = 'none';
    } else {
      elements.badge.style.display = 'none';
      elements.card.style.display = 'flex';
    }

    // Bind event listeners
    card.querySelector('#at-btn-minimize').addEventListener('click', toggleCollapse);
    card.querySelector('#at-btn-settings').addEventListener('click', openSettings);
    card.querySelector('#at-btn-close-settings').addEventListener('click', closeSettings);
    card.querySelector('#at-btn-save-settings').addEventListener('click', saveSettings);
    card.querySelector('#at-btn-sync').addEventListener('click', openSync);
    card.querySelector('#at-btn-close-sync').addEventListener('click', closeSync);
    card.querySelector('#at-btn-copy-sync-link').addEventListener('click', copySyncLink);
  }

  function toggleCollapse() {
    state.isMinimized = !state.isMinimized;
    localStorage.setItem('at_is_minimized', state.isMinimized);

    if (state.isMinimized) {
      elements.card.style.opacity = '0';
      setTimeout(() => {
        elements.card.style.display = 'none';
        elements.badge.style.display = 'flex';
        elements.badge.style.opacity = '1';
      }, 150);
    } else {
      elements.badge.style.opacity = '0';
      setTimeout(() => {
        elements.badge.style.display = 'none';
        elements.card.style.display = 'flex';
        elements.card.style.opacity = '1';
        updateUI();
      }, 150);
    }
  }

  function openSettings() {
    elements.card.querySelector('#at-settings-panel').classList.add('active');
  }

  function closeSettings() {
    elements.card.querySelector('#at-settings-panel').classList.remove('active');
  }

  function openSync() {
    const token = localStorage.getItem('AccessToken') || '';
    const refreshToken = localStorage.getItem('RefreshToken') || '';
    
    // Construct sync URL
    const pwaBase = state.pwaUrl.endsWith('/') ? state.pwaUrl : (state.pwaUrl + '/');
    const syncUrl = `${pwaBase}?token=${encodeURIComponent(token)}&refresh=${encodeURIComponent(refreshToken)}`;
    
    // Update inputs
    const linkInput = elements.card.querySelector('#at-sync-link-input');
    if (linkInput) linkInput.value = syncUrl;
    
    // Generate QR code using public api.qrserver.com
    const qrImg = elements.card.querySelector('#at-sync-qr-img');
    if (qrImg) {
      qrImg.src = `https://api.qrserver.com/v1/create-qr-code/?size=100x100&data=${encodeURIComponent(syncUrl)}`;
    }
    
    elements.card.querySelector('#at-sync-panel').classList.add('active');
  }

  function closeSync() {
    elements.card.querySelector('#at-sync-panel').classList.remove('active');
  }

  function copySyncLink() {
    const linkInput = elements.card.querySelector('#at-sync-link-input');
    if (linkInput) {
      linkInput.select();
      linkInput.setSelectionRange(0, 99999);
      navigator.clipboard.writeText(linkInput.value).then(() => {
        const copyBtn = elements.card.querySelector('#at-btn-copy-sync-link');
        if (copyBtn) {
          const oldText = copyBtn.innerText;
          copyBtn.innerText = 'Copied!';
          setTimeout(() => { copyBtn.innerText = oldText; }, 1500);
        }
      }).catch(err => {
        console.error('Failed to copy text: ', err);
      });
    }
  }

  function saveSettings() {
    const hoursVal = parseFloat(elements.card.querySelector('#at-setting-hours').value);
    const notifyVal = elements.card.querySelector('#at-setting-notify').checked;
    const userIdVal = elements.card.querySelector('#at-setting-userid').value.trim();
    const pwaurlVal = elements.card.querySelector('#at-setting-pwaurl').value.trim();

    state.targetHours = isNaN(hoursVal) ? 8.5 : hoursVal;
    state.notifyEnabled = notifyVal;

    localStorage.setItem('at_target_hours', state.targetHours);
    localStorage.setItem('at_notify_enabled', state.notifyEnabled);

    if (userIdVal) {
      state.userId = userIdVal;
      localStorage.setItem('at_userid_override', userIdVal);
    } else {
      state.userId = null;
      localStorage.removeItem('at_userid_override');
    }

    if (pwaurlVal) {
      state.pwaUrl = pwaurlVal;
      localStorage.setItem('at_pwa_url', pwaurlVal);
    }

    closeSettings();
    fetchAttendanceLogs();
  }

  function updateUI() {
    const metrics = calculateMetrics();
    if (!metrics) {
      // No logs yet today
      const statusTextEl = elements.card.querySelector('#at-status-text');
      if (statusTextEl) statusTextEl.innerText = 'No attendance logs today';
      return;
    }

    // Update floating collapsed badge
    const badgeTimeEl = document.getElementById('at-badge-work-time');
    if (badgeTimeEl) badgeTimeEl.innerText = formatMinutes(metrics.workMinutes);

    // Update status badge
    const indicator = elements.card.querySelector('#at-status-indicator');
    const textNode = elements.card.querySelector('#at-status-text');

    if (indicator && textNode) {
      indicator.className = 'at-status-indicator';
      if (metrics.completed) {
        indicator.classList.add('at-status-done');
        textNode.innerText = 'Completed shift! Time to leave.';
      } else if (metrics.isClockedIn) {
        indicator.classList.add('at-status-in');
        textNode.innerText = `Clocked In (${metrics.lastLocation})`;
      } else {
        indicator.classList.add('at-status-out');
        textNode.innerText = 'Clocked Out / Break';
      }
    }

    // Update main grid values
    const firstInEl = elements.card.querySelector('#at-val-first-in');
    const workTimeEl = elements.card.querySelector('#at-val-work-time');
    const breakTimeEl = elements.card.querySelector('#at-val-break-time');
    const exitTimeEl = elements.card.querySelector('#at-val-exit-time');

    if (firstInEl) firstInEl.innerText = metrics.firstClockIn;
    if (workTimeEl) workTimeEl.innerText = formatMinutes(metrics.workMinutes);
    if (breakTimeEl) breakTimeEl.innerText = formatMinutes(metrics.breakMinutes);
    if (exitTimeEl) exitTimeEl.innerText = metrics.exitTime;

    // Update progress bar
    const targetMin = state.targetHours * 60;
    const progressPercent = Math.min(100, Math.floor((metrics.workMinutes / targetMin) * 100));
    
    const fillEl = elements.card.querySelector('#at-progress-fill');
    const pctEl = elements.card.querySelector('#at-progress-percentage');
    const remEl = elements.card.querySelector('#at-progress-remaining');

    if (fillEl) fillEl.style.width = `${progressPercent}%`;
    if (pctEl) pctEl.innerText = `${progressPercent}% Completed`;
    if (remEl) {
      remEl.innerText = metrics.completed
        ? '0m remaining'
        : `${formatMinutes(metrics.remainingMinutes)} remaining`;
    }

    // Handle Completed Notifications (shown every 5 minutes while shift is completed and user is still clocked in)
    if (metrics.completed && metrics.isClockedIn && state.notifyEnabled) {
      triggerNotificationOnce(metrics.workMinutes);
    }
  }

  function triggerNotificationOnce(workMinutes) {
    const now = Date.now();
    // Throttle notifications to run every 5 minutes (or whatever is in state.notificationInterval)
    if (now - state.lastNotificationTime > state.notificationInterval) {
      if (Notification.permission === 'default') {
        Notification.requestPermission();
      } else if (Notification.permission === 'granted') {
        const bodyText = `You have completed ${formatMinutes(workMinutes)} of biometric office time. Time to wrap up and head home!`;
        new Notification("Shift Completed!", {
          body: bodyText,
          icon: 'https://apps.pal.tech/hrms-backend/images/117091f8-1694-4fb6-8e9e-bf36cdf0b1a0' // reuse standard image
        });
        state.lastNotificationTime = now;
      }
    }
  }

  // --- INITIALIZATION ---
  function init() {
    // Request notification permissions early
    if (Notification.permission === 'default') {
      Notification.requestPermission();
    }

    buildUI();

    // Initial load
    fetchAttendanceLogs();

    // 1-minute poller for fetching new attendance punches
    setInterval(fetchAttendanceLogs, 60000);

    // 1-second ticker for smooth real-time work & break minutes accumulation
    setInterval(() => {
      const settingsPanel = elements.card.querySelector('#at-settings-panel');
      const isSettingsActive = settingsPanel && settingsPanel.classList.contains('active');
      if (!state.isMinimized && !isSettingsActive) {
        updateUI();
      }
    }, 1000);
  }

  // Start after DOM is loaded or if it's already loaded
  if (document.readyState === 'complete' || document.readyState === 'interactive') {
    init();
  } else {
    window.addEventListener('DOMContentLoaded', init);
  }

})();
