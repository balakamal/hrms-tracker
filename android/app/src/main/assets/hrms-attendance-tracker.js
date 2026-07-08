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
  "use strict";

  // --- STYLES INJECTION ---
  const styles = `
    /* Widget Container */
    #at-widget-container {
      --at-bg-card: linear-gradient(135deg, rgba(15, 15, 20, 0.9) 0%, rgba(25, 25, 30, 0.9) 100%);
      --at-bg-badge: linear-gradient(135deg, rgba(20, 20, 25, 0.85) 0%, rgba(30, 30, 35, 0.85) 100%);
      --at-bg-panels: #111115;
      --at-text-primary: #f3f4f6;
      --at-text-secondary: #9ca3af;
      --at-border: rgba(255, 255, 255, 0.08);
      --at-border-input: rgba(255, 255, 255, 0.1);
      --at-bg-input: rgba(255, 255, 255, 0.05);
      --at-bg-item: rgba(255, 255, 255, 0.03);
      --at-shadow: rgba(0, 0, 0, 0.4);
      --at-title-color: #ffffff;
      --at-panel-border: rgba(255, 255, 255, 0.08);
      --at-accent-work: #00f2fe;
      --at-accent-exit: #a78bfa;
      --at-bg-header: #14141c;

      position: fixed;
      bottom: 24px;
      right: 24px;
      z-index: 99999;
      font-family: 'Outfit', 'Inter', system-ui, -apple-system, sans-serif;
      color: var(--at-text-primary);
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }
    #at-widget-container.at-theme-light {
      --at-bg-card: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(251, 207, 232, 0.25) 100%);
      --at-bg-badge: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(251, 207, 232, 0.3) 100%);
      --at-bg-panels: #ffffff;
      --at-text-primary: #1e293b;
      --at-text-secondary: #64748b;
      --at-border: rgba(244, 114, 182, 0.15);
      --at-border-input: rgba(244, 114, 182, 0.2);
      --at-bg-input: rgba(244, 114, 182, 0.05);
      --at-bg-item: rgba(255, 255, 255, 0.7);
      --at-shadow: rgba(244, 114, 182, 0.1);
      --at-title-color: #064173;
      --at-panel-border: rgba(244, 114, 182, 0.12);
      --at-accent-work: #db2777;
      --at-accent-exit: #7c3aed;
      --at-bg-header: #fce7f3;
    }
    #at-widget-container.at-dragging {
      transition: none !important;
    }

    /* Floating Badge Mode */
    .at-badge {
      display: flex;
      align-items: center;
      gap: 8px;
      background: var(--at-bg-badge);
      backdrop-filter: blur(12px) saturate(180%);
      border: 1px solid var(--at-border);
      border-radius: 50px;
      padding: 10px 18px;
      box-shadow: 0 10px 30px var(--at-shadow);
      cursor: pointer;
      user-select: none;
      transition: all 0.2s ease;
      color: var(--at-text-primary);
      touch-action: none;
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
      background: var(--at-bg-card);
      backdrop-filter: blur(16px) saturate(180%);
      border: 1px solid var(--at-border);
      border-radius: 20px;
      padding: 20px;
      box-shadow: 0 15px 40px var(--at-shadow);
      position: relative;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      gap: 16px;
      transition: all 0.3s ease;
      color: var(--at-text-primary);
    }

    /* Header */
    .at-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      cursor: move;
      user-select: none;
      background: var(--at-bg-header);
      margin: -20px -20px 0 -20px;
      padding: 16px 20px;
      border-bottom: 1px solid var(--at-panel-border);
    }
    .at-title-group {
      display: flex;
      flex-direction: column;
    }
    .at-title {
      font-weight: 700;
      font-size: 16px;
      letter-spacing: -0.2px;
      background: var(--at-title-color);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    .at-subtitle {
      font-size: 11px;
      color: var(--at-text-secondary);
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
      color: var(--at-text-secondary);
      cursor: pointer;
      padding: 4px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s ease;
    }
    .at-btn:hover {
      background: var(--at-bg-input);
      color: var(--at-title-color);
    }
    .at-btn svg {
      width: 18px;
      height: 18px;
      fill: currentColor;
    }
    .at-drag-handle {
      cursor: move;
      color: var(--at-text-secondary);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 4px;
      border-radius: 6px;
      transition: all 0.2s ease;
      touch-action: none;
    }
    .at-drag-handle:hover {
      background: var(--at-bg-input);
      color: var(--at-title-color);
    }
    .at-drag-handle svg {
      width: 18px;
      height: 18px;
      fill: currentColor;
    }

    /* Status Banner */
    .at-status-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      background: var(--at-bg-input);
      padding: 8px 12px;
      border-radius: 10px;
      font-size: 12px;
      font-weight: 600;
      color: var(--at-text-primary);
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
      background: var(--at-bg-item);
      border: 1px solid var(--at-border);
      padding: 12px;
      border-radius: 12px;
      display: flex;
      flex-direction: column;
      gap: 4px;
      color: var(--at-text-primary);
    }
    .at-label {
      font-size: 10px;
      color: var(--at-text-secondary);
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
      color: var(--at-text-primary);
    }
    .at-val.highlight-work {
      color: var(--at-accent-work);
    }
    .at-val.highlight-exit {
      color: var(--at-accent-exit);
    }

    /* Progress Segment */
    .at-progress-sec {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .at-progress-track {
      height: 6px;
      background: var(--at-bg-input);
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
      color: var(--at-text-secondary);
    }

    /* Settings Panel */
    .at-settings {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: var(--at-bg-panels);
      border-radius: 20px;
      padding: 20px;
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
      gap: 14px;
      z-index: 10;
      transition: all 0.3s ease;
      transform: translateY(100%);
      overflow-y: auto;
      color: var(--at-text-primary);
    }
    .at-settings.active {
      transform: translateY(0);
    }
    .at-settings-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid var(--at-panel-border);
      padding-bottom: 8px;
    }
    .at-settings-title {
      font-weight: 700;
      font-size: 14px;
      color: var(--at-title-color);
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
      color: var(--at-text-secondary);
      font-weight: 600;
    }
    .at-input {
      background: var(--at-bg-input);
      border: 1px solid var(--at-border-input);
      border-radius: 8px;
      padding: 8px 12px;
      color: var(--at-text-primary);
      font-size: 13px;
      outline: none;
      font-family: inherit;
    }
    .at-input option {
      background: var(--at-bg-panels);
      color: var(--at-text-primary);
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
      background: var(--at-bg-panels);
      border-radius: 20px;
      padding: 16px;
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
      gap: 10px;
      z-index: 12;
      transition: all 0.3s ease;
      transform: translateY(100%);
      overflow-y: auto;
      color: var(--at-text-primary);
    }
    .at-sync.active {
      transform: translateY(0);
    }
    .at-sync-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid var(--at-panel-border);
      padding-bottom: 6px;
    }
    .at-sync-title {
      font-weight: 700;
      font-size: 14px;
      color: var(--at-title-color);
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
      color: var(--at-text-secondary);
      text-align: center;
      line-height: 1.3;
    }

    /* Loading Overlay */
    .at-loading-overlay {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(15, 15, 20, 0.85);
      backdrop-filter: blur(8px);
      z-index: 15;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 12px;
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.3s ease;
    }
    #at-widget-container.at-theme-light .at-loading-overlay {
      background: rgba(255, 255, 255, 0.85);
    }
    .at-loading-overlay.active {
      opacity: 1;
      pointer-events: auto;
    }
    .at-spinner {
      width: 32px;
      height: 32px;
      border: 3px solid rgba(0, 242, 254, 0.1);
      border-top-color: #00f2fe;
      border-radius: 50%;
      animation: at-spin 0.8s linear infinite;
    }
    #at-widget-container.at-theme-light .at-spinner {
      border: 3px solid rgba(219, 39, 119, 0.1);
      border-top-color: #db2777;
    }
    .at-loading-text {
      font-size: 12px;
      font-weight: 600;
      color: var(--at-text-secondary);
      letter-spacing: 0.5px;
    }
    @keyframes at-spin {
      to { transform: rotate(360deg); }
    }
    
    @media (max-width: 600px) {
      #at-widget-container {
        right: 12px !important;
        bottom: 12px !important;
        left: 12px !important;
        width: calc(100% - 24px) !important;
      }
      .at-card {
        width: 100% !important;
      }
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
    phone: `<svg viewBox="0 0 24 24"><path d="M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z"/></svg>`,
    drag: `<svg viewBox="0 0 24 24"><path d="M11 18c0 1.1-.9 2-2 2s-2-.9-2-2 .9-2 2-2 2 .9 2 2zm-2-8c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0-6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm6 4c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/></svg>`,
  };

  // Migration for old PWA URL cache
  const cachedPwaUrl = localStorage.getItem("at_pwa_url");
  if (!cachedPwaUrl || cachedPwaUrl.includes("kamal-thiruveedhula")) {
    localStorage.setItem(
      "at_pwa_url",
      "https://balakamal.github.io/hrms-tracker/hrms-pwa/",
    );
  }

  // --- STATE MANAGEMENT ---
  let state = {
    userId: localStorage.getItem("at_user_id") || null,
    userName: localStorage.getItem("at_user_name") || "User",
    targetHours: parseFloat(localStorage.getItem("at_target_hours") || "8.5"),
    notifyEnabled: localStorage.getItem("at_notify_enabled") !== "false",
    pwaUrl:
      localStorage.getItem("at_pwa_url") ||
      "https://balakamal.github.io/hrms-tracker/hrms-pwa/",
    isMinimized: localStorage.getItem("at_is_minimized") === "true",
    theme: localStorage.getItem("at_theme") || "dark",
    userAvatar: localStorage.getItem("at_user_avatar") || null,
    attendanceData: null,
    lastNotificationTime: 0,
    notificationInterval: 5 * 60 * 1000, // 5 minutes in milliseconds
    loading: false,
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
    const mm = String(today.getMonth() + 1).padStart(2, "0");
    const dd = String(today.getDate()).padStart(2, "0");
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

    state.loading = true;
    updateLoadingUI();
    try {
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "application/json",
        },
      });
      const resData = await response.json();
      if (resData && resData.data) {
        state.userId = resData.data.id;
        state.userName = resData.data.name || resData.data.firstName || "User";

        // Cache details
        localStorage.setItem("at_user_id", state.userId);
        localStorage.setItem("at_user_name", state.userName);
        if (window.AndroidApp) {
          window.AndroidApp.saveUserData(state.userId, state.userName);
        }

        // Extract user avatar photo UUID if present, or query from page DOM
        const photoId = resData.data.photo || resData.data.photoId;
        if (photoId) {
          state.userAvatar = `https://apps.pal.tech/hrms-backend/images/${photoId}`;
        } else {
          const domAvatar = document.querySelector(
            'img[src*="hrms-backend/images/"]',
          );
          if (domAvatar) {
            state.userAvatar = domAvatar.src;
          }
        }
        if (state.userAvatar) {
          localStorage.setItem("at_user_avatar", state.userAvatar);
        }

        const subtitleEl = document.querySelector(".at-subtitle");
        if (subtitleEl) subtitleEl.innerText = state.userName;
      }
    } catch (e) {
      console.error("Error fetching user details in tracker script:", e);
    } finally {
      state.loading = false;
      updateLoadingUI();
    }
  }

  async function fetchAttendanceLogs() {
    if (!state.userId) await fetchUserIdentity();
    if (!state.userId) return;

    const todayDate = getTodayISO();
    const url = `https://apps.pal.tech/hrms-backend/api/Attendance/GetDailyLog?date=${todayDate}&userId=${state.userId}`;
    const token = localStorage.getItem("AccessToken");
    if (!token) return;

    state.loading = true;
    updateLoadingUI();
    try {
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "application/json",
        },
      });
      if (response.status === 401) {
        state.userId = null;
        return;
      }
      const resData = await response.json();
      if (resData && resData.data) {
        state.attendanceData = resData.data;
        updateUI();
      }
    } catch (e) {
      console.error("Error fetching attendance logs in tracker script:", e);
    } finally {
      state.loading = false;
      updateLoadingUI();
    }
  }

  // Calculate work metrics in real-time
  function calculateMetrics() {
    if (!state.attendanceData) return null;

    const logs = state.attendanceData.attendanceDailyLogs || [];
    if (logs.length === 0) return null;

    const todayStr = getTodayLocalDateStr();
    const parseTime = (timeStr) => new Date(`${todayStr}T${timeStr}`);

    const parsedLogs = logs
      .map((l) => ({
        time: parseTime(l.time),
        isIn: l.isIn, // 0 = In, 1 = Out
        location: l.location,
      }))
      .sort((a, b) => a.time - b.time);

    const firstClockIn = parsedLogs[0].time;
    let workDurationMs = 0;
    let lastInTime = null;
    let lastOutTime = null;
    let lastLocation = "Office";

    for (let i = 0; i < parsedLogs.length; i++) {
      const log = parsedLogs[i];
      if (log.isIn === 0) {
        lastInTime = log.time;
        lastLocation = log.location || lastLocation;
      } else if (log.isIn === 1 && lastInTime) {
        workDurationMs += log.time - lastInTime;
        lastOutTime = log.time;
        lastInTime = null;
      }
    }

    const isClockedIn = lastInTime !== null;
    const now = new Date();

    if (isClockedIn) {
      workDurationMs += now - lastInTime;
    }

    const totalWorkMinutes = workDurationMs / 60000;
    const targetMinutes = state.targetHours * 60;
    const remainingMinutes = Math.max(0, targetMinutes - totalWorkMinutes);
    const completed = totalWorkMinutes >= targetMinutes;

    // Break time: elapsed time up to now (or last clock-out) minus work time
    const endTimestamp = isClockedIn ? now : lastOutTime || firstClockIn;
    const totalElapsedMs = endTimestamp - firstClockIn;
    const totalBreakMinutes = Math.max(
      0,
      (totalElapsedMs - workDurationMs) / 60000,
    );

    let exitTime = "Completed";
    if (!completed) {
      const exitDate = new Date(now.getTime() + remainingMinutes * 60000);
      exitTime = exitDate.toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
      });
    }

    return {
      firstClockIn: firstClockIn.toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
      }),
      workMinutes: totalWorkMinutes,
      breakMinutes: totalBreakMinutes,
      remainingMinutes: remainingMinutes,
      exitTime: exitTime,
      isClockedIn: isClockedIn,
      completed: completed,
      lastLocation: lastLocation,
    };
  }

  // --- UI CONTROLLER ---
  let elements = {};
  let wasDragging = false;

  function buildUI() {
    if (document.getElementById("at-widget-container")) return;

    // Inject style sheet
    if (!document.getElementById("at-widget-styles")) {
      const styleEl = document.createElement("style");
      styleEl.id = "at-widget-styles";
      styleEl.innerHTML = styles;
      document.head.appendChild(styleEl);
    }

    // Create Main Container
    const container = document.createElement("div");
    container.id = "at-widget-container";
    if (state.theme === "light") {
      container.classList.add("at-theme-light");
    }
    document.body.appendChild(container);
    elements.container = container;

    // 1. Badge View (Collapsed)
    const badge = document.createElement("div");
    badge.className = "at-badge";
    badge.innerHTML = `${ICONS.clock}<span class="at-badge-text" id="at-badge-work-time">--h --m</span>`;
    badge.addEventListener("click", toggleCollapse);
    container.appendChild(badge);
    elements.badge = badge;

    // 2. Card View (Expanded)
    const card = document.createElement("div");
    card.className = "at-card";
    card.style.display = "none";
    card.innerHTML = `
      <!-- Loading Overlay -->
      <div class="at-loading-overlay" id="at-loading-overlay">
        <div class="at-spinner"></div>
        <span class="at-loading-text">Fetching Live Logs...</span>
      </div>

      <div class="at-header">
        <div class="at-title-group">
          <span class="at-title">Attendance Insights</span>
          <span class="at-subtitle">${state.userName}</span>
        </div>
        <div class="at-header-actions">
          <div class="at-drag-handle" id="at-drag-handle" title="Drag Widget">${ICONS.drag}</div>
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
          <label>Target Work (Hours / Minutes)</label>
          <div style="display: flex; gap: 8px; align-items: center;">
            <select class="at-input" id="at-setting-hours-select" style="flex: 1; padding: 6px; background: var(--at-bg-input); color: var(--at-text-primary); border: 1px solid var(--at-border-input); border-radius: 8px; font-size: 13px;">
              ${Array.from({ length: 25 }, (_, i) => `<option value="${i}" ${Math.floor(state.targetHours) === i ? "selected" : ""}>${i}h</option>`).join("")}
            </select>
            <select class="at-input" id="at-setting-minutes-select" style="flex: 1; padding: 6px; background: var(--at-bg-input); color: var(--at-text-primary); border: 1px solid var(--at-border-input); border-radius: 8px; font-size: 13px;">
              ${Array.from({ length: 60 }, (_, i) => `<option value="${i}" ${Math.round((state.targetHours % 1) * 60) === i ? "selected" : ""}>${i}m</option>`).join("")}
            </select>
          </div>
        </div>
        <div class="at-form-group">
          <label>Target Work (Minutes / Formula)</label>
          <input type="text" class="at-input" id="at-setting-minutes-input" value="${Math.round(state.targetHours * 60)}" placeholder="e.g. 510 or 8*60+30">
        </div>
        <div class="at-form-group row">
          <label>Enable Notifications</label>
          <label class="at-toggle">
            <input type="checkbox" id="at-setting-notify" ${state.notifyEnabled ? "checked" : ""}>
            <span class="at-slider"></span>
          </label>
        </div>
        <div class="at-form-group row">
          <label>Light Theme</label>
          <label class="at-toggle">
            <input type="checkbox" id="at-setting-theme" ${state.theme === "light" ? "checked" : ""}>
            <span class="at-slider"></span>
          </label>
        </div>

        <button class="at-btn-save" id="at-btn-save-settings">Save & Apply</button>
      </div>
    `;
    container.appendChild(card);
    elements.card = card;

    // Apply layout state
    if (state.isMinimized) {
      elements.badge.style.display = "flex";
      elements.card.style.display = "none";
    } else {
      elements.badge.style.display = "none";
      elements.card.style.display = "flex";
    }

    // Restore dragged position
    const savedX = localStorage.getItem("at_widget_x");
    const savedBottom = localStorage.getItem("at_widget_bottom");
    if (savedX !== null && savedBottom !== null) {
      container.style.left = savedX;
      container.style.bottom = savedBottom;
      container.style.top = "auto";
      container.style.right = "auto";
    }

    setupDragging();

    // Bind event listeners
    card
      .querySelector("#at-btn-minimize")
      .addEventListener("click", toggleCollapse);
    card
      .querySelector("#at-btn-settings")
      .addEventListener("click", openSettings);
    card
      .querySelector("#at-btn-close-settings")
      .addEventListener("click", closeSettings);
    card
      .querySelector("#at-btn-save-settings")
      .addEventListener("click", saveSettings);

    // Sync dropdowns with formula input
    const hoursSelect = card.querySelector("#at-setting-hours-select");
    const minutesSelect = card.querySelector("#at-setting-minutes-select");
    const minutesInput = card.querySelector("#at-setting-minutes-input");

    function syncDropdownsToInput() {
      const h = parseInt(hoursSelect.value, 10);
      const m = parseInt(minutesSelect.value, 10);
      minutesInput.value = h * 60 + m;
    }

    function syncInputToDropdowns() {
      const parsed = parseWorkTimeInput(minutesInput.value);
      if (parsed !== null && parsed >= 0) {
        const h = Math.min(24, Math.floor(parsed / 60));
        const m = Math.min(59, Math.round(parsed % 60));
        hoursSelect.value = h;
        minutesSelect.value = m;
      }
    }

    hoursSelect.addEventListener("change", syncDropdownsToInput);
    minutesSelect.addEventListener("change", syncDropdownsToInput);
    minutesInput.addEventListener("input", syncInputToDropdowns);
  }

  function setupDragging() {
    const container = elements.container;
    const badge = elements.badge;
    const dragHandle = elements.card.querySelector("#at-drag-handle");

    let active = false;
    let currentX;
    let currentY;
    let initialX;
    let initialY;
    let xOffset = 0;
    let yOffset = 0;
    let dragThresholdPassed = false;

    function dragStart(e) {
      // Don't drag if clicking buttons inside the header/handle
      if (e.target.closest("button") || e.target.closest("a")) {
        return;
      }

      const rect = container.getBoundingClientRect();
      initialX = e.clientX;
      initialY = e.clientY;

      const styleLeft = container.style.left;
      const styleBottom = container.style.bottom;

      if (styleLeft && styleBottom) {
        xOffset = parseFloat(styleLeft);
        yOffset = parseFloat(styleBottom);
      } else {
        xOffset = rect.left;
        yOffset = window.innerHeight - rect.bottom;
      }

      active = true;
      dragThresholdPassed = false;
      wasDragging = false;
      container.classList.add("at-dragging");
    }

    function dragEnd(e) {
      if (!active) return;

      active = false;
      container.classList.remove("at-dragging");

      if (dragThresholdPassed) {
        constrainToViewport();
      }
    }

    function drag(e) {
      if (!active) return;

      const dx = e.clientX - initialX;
      const dy = e.clientY - initialY;

      if (!dragThresholdPassed && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
        dragThresholdPassed = true;
        wasDragging = true;
      }

      if (dragThresholdPassed) {
        if (e.cancelable) e.preventDefault();

        currentX = xOffset + dx;
        currentY = yOffset - dy;

        const rect = container.getBoundingClientRect();
        const maxX = window.innerWidth - rect.width;
        const maxY = window.innerHeight - rect.height;

        currentX = Math.max(0, Math.min(currentX, maxX));
        currentY = Math.max(0, Math.min(currentY, maxY));

        container.style.left = `${currentX}px`;
        container.style.bottom = `${currentY}px`;
        container.style.top = "auto";
        container.style.right = "auto";
      }
    }

    [badge, dragHandle].forEach((el) => {
      if (el) {
        el.addEventListener("pointerdown", dragStart);
      }
    });

    window.addEventListener("pointermove", drag);
    window.addEventListener("pointerup", dragEnd);
    window.addEventListener("pointercancel", dragEnd);
  }

  function constrainToViewport() {
    const container = elements.container;
    if (!container) return;
    const rect = container.getBoundingClientRect();

    let left = rect.left;
    let width = rect.width || container.offsetWidth || 320;
    let height = rect.height || container.offsetHeight || 300;

    // Check bounds
    if (left < 0) {
      left = 0;
    } else if (left + width > window.innerWidth) {
      left = window.innerWidth - width;
    }

    let top = rect.top;
    if (top < 0) {
      top = 0;
    } else if (top + height > window.innerHeight) {
      top = window.innerHeight - height;
    }

    const bottom = window.innerHeight - (top + height);

    container.style.left = `${left}px`;
    container.style.bottom = `${bottom}px`;
    container.style.top = "auto";
    container.style.right = "auto";

    localStorage.setItem("at_widget_x", `${left}px`);
    localStorage.setItem("at_widget_bottom", `${bottom}px`);
  }

  function toggleCollapse() {
    if (wasDragging) {
      wasDragging = false;
      return;
    }
    state.isMinimized = !state.isMinimized;
    localStorage.setItem("at_is_minimized", state.isMinimized);

    if (state.isMinimized) {
      elements.card.style.opacity = "0";
      setTimeout(() => {
        elements.card.style.display = "none";
        elements.badge.style.display = "flex";
        elements.badge.style.opacity = "1";
        constrainToViewport();
      }, 150);
    } else {
      elements.badge.style.opacity = "0";
      setTimeout(() => {
        elements.badge.style.display = "none";
        elements.card.style.display = "flex";
        elements.card.style.opacity = "1";
        updateUI();
        constrainToViewport();
      }, 150);
    }
  }

  function openSettings() {
    elements.card.querySelector("#at-settings-panel").classList.add("active");
  }

  function closeSettings() {
    elements.card
      .querySelector("#at-settings-panel")
      .classList.remove("active");
  }

  function openSync() {
    const token = localStorage.getItem("AccessToken") || "";
    const refreshToken = localStorage.getItem("RefreshToken") || "";

    // Construct sync URL
    const pwaBase = state.pwaUrl.endsWith("/")
      ? state.pwaUrl
      : state.pwaUrl + "/";
    const syncUrl = `${pwaBase}?token=${encodeURIComponent(token)}&refresh=${encodeURIComponent(refreshToken)}`;

    // Update inputs
    const linkInput = elements.card.querySelector("#at-sync-link-input");
    if (linkInput) linkInput.value = syncUrl;

    // Generate QR code using public api.qrserver.com
    const qrImg = elements.card.querySelector("#at-sync-qr-img");
    if (qrImg) {
      qrImg.src = `https://api.qrserver.com/v1/create-qr-code/?size=100x100&data=${encodeURIComponent(syncUrl)}`;
    }

    elements.card.querySelector("#at-sync-panel").classList.add("active");
  }

  function closeSync() {
    elements.card.querySelector("#at-sync-panel").classList.remove("active");
  }

  function copySyncLink() {
    const linkInput = elements.card.querySelector("#at-sync-link-input");
    if (linkInput) {
      linkInput.select();
      linkInput.setSelectionRange(0, 99999);
      navigator.clipboard
        .writeText(linkInput.value)
        .then(() => {
          const copyBtn = elements.card.querySelector("#at-btn-copy-sync-link");
          if (copyBtn) {
            const oldText = copyBtn.innerText;
            copyBtn.innerText = "Copied!";
            setTimeout(() => {
              copyBtn.innerText = oldText;
            }, 1500);
          }
        })
        .catch((err) => {
          console.error("Failed to copy text: ", err);
        });
    }
  }

  function parseWorkTimeInput(inputStr) {
    inputStr = inputStr.trim();
    if (!inputStr) return null;

    // 1. Check for special pattern: H * M * 60
    const specialPattern = /^(\d+)\s*\*\s*(\d+)\s*\*\s*60$/;
    const match = inputStr.match(specialPattern);
    if (match) {
      const hours = parseInt(match[1], 10);
      const minutes = parseInt(match[2], 10);
      return hours * 60 + minutes;
    }

    // 2. Check for colon/dot pattern like "5:10" or "5.10"
    if (/^\d+[:.]\d+$/.test(inputStr)) {
      const parts = inputStr.split(/[:.]/);
      const hours = parseInt(parts[0], 10);
      const minutes = parseInt(parts[1], 10);
      return hours * 60 + minutes;
    }

    // 3. Fallback to general mathematical expression evaluation
    const sanitized = inputStr.replace(/[^0-9+\-*/().\s]/g, "");
    try {
      const evalResult = new Function(`return (${sanitized});`)();
      if (
        typeof evalResult === "number" &&
        !isNaN(evalResult) &&
        isFinite(evalResult)
      ) {
        return evalResult;
      }
    } catch (e) {
      // Ignore
    }

    const plainNum = parseFloat(inputStr);
    if (!isNaN(plainNum)) {
      return plainNum;
    }

    return null;
  }

  function saveSettings() {
    const minutesValStr = elements.card.querySelector(
      "#at-setting-minutes-input",
    ).value;
    const parsedMinutes = parseWorkTimeInput(minutesValStr);
    const notifyVal = elements.card.querySelector("#at-setting-notify").checked;

    state.targetHours =
      parsedMinutes === null || parsedMinutes <= 0 ? 8.5 : parsedMinutes / 60;
    state.notifyEnabled = notifyVal;
    state.theme = elements.card.querySelector("#at-setting-theme").checked
      ? "light"
      : "dark";

    localStorage.setItem("at_target_hours", state.targetHours);
    localStorage.setItem("at_notify_enabled", state.notifyEnabled);
    localStorage.setItem("at_theme", state.theme);
    if (window.AndroidApp) {
      window.AndroidApp.saveSettings(state.targetHours);
    }

    if (state.theme === "light") {
      elements.container.classList.add("at-theme-light");
    } else {
      elements.container.classList.remove("at-theme-light");
    }



    closeSettings();
    fetchAttendanceLogs();
  }

  function updateUI() {
    const metrics = calculateMetrics();
    if (!metrics) {
      // No logs yet today
      const statusTextEl = elements.card.querySelector("#at-status-text");
      if (statusTextEl) statusTextEl.innerText = "No attendance logs today";
      return;
    }

    // Update floating collapsed badge
    const badgeTimeEl = document.getElementById("at-badge-work-time");
    if (badgeTimeEl) badgeTimeEl.innerText = formatMinutes(metrics.workMinutes);

    // Update status badge
    const indicator = elements.card.querySelector("#at-status-indicator");
    const textNode = elements.card.querySelector("#at-status-text");

    if (indicator && textNode) {
      indicator.className = "at-status-indicator";
      if (metrics.completed) {
        indicator.classList.add("at-status-done");
        textNode.innerText = "Completed shift! Time to leave.";
      } else if (metrics.isClockedIn) {
        indicator.classList.add("at-status-in");
        textNode.innerText = `Clocked In (${metrics.lastLocation})`;
      } else {
        indicator.classList.add("at-status-out");
        textNode.innerText = "Clocked Out / Break";
      }
    }

    // Update main grid values
    const firstInEl = elements.card.querySelector("#at-val-first-in");
    const workTimeEl = elements.card.querySelector("#at-val-work-time");
    const breakTimeEl = elements.card.querySelector("#at-val-break-time");
    const exitTimeEl = elements.card.querySelector("#at-val-exit-time");

    if (firstInEl) firstInEl.innerText = metrics.firstClockIn;
    if (workTimeEl) workTimeEl.innerText = formatMinutes(metrics.workMinutes);
    if (breakTimeEl)
      breakTimeEl.innerText = formatMinutes(metrics.breakMinutes);
    if (exitTimeEl) exitTimeEl.innerText = metrics.exitTime;

    // Update progress bar
    const targetMin = state.targetHours * 60;
    const progressPercent = Math.min(
      100,
      Math.floor((metrics.workMinutes / targetMin) * 100),
    );

    const fillEl = elements.card.querySelector("#at-progress-fill");
    const pctEl = elements.card.querySelector("#at-progress-percentage");
    const remEl = elements.card.querySelector("#at-progress-remaining");

    if (fillEl) fillEl.style.width = `${progressPercent}%`;
    if (pctEl) pctEl.innerText = `${progressPercent}% Completed`;
    if (remEl) {
      remEl.innerText = metrics.completed
        ? "0m remaining"
        : `${formatMinutes(metrics.remainingMinutes)} remaining`;
    }

    // Handle Completed Notifications (shown every 5 minutes while shift is completed and user is still clocked in)
    if (metrics.completed && metrics.isClockedIn && state.notifyEnabled) {
      triggerNotificationOnce(metrics.workMinutes);
    }
  }

  function updateLoadingUI() {
    const overlay = document.getElementById("at-loading-overlay");
    if (!overlay) return;
    if (state.loading) {
      overlay.classList.add("active");
    } else {
      overlay.classList.remove("active");
    }
  }

  function triggerNotificationOnce(workMinutes) {
    const now = Date.now();
    // Throttle notifications to run every 5 minutes (or whatever is in state.notificationInterval)
    if (now - state.lastNotificationTime > state.notificationInterval) {
      if (typeof Notification !== "undefined") {
        if (Notification.permission === "default") {
          Notification.requestPermission();
        } else if (Notification.permission === "granted") {
          const bodyText = `You have completed ${formatMinutes(workMinutes)} of biometric office time. Time to wrap up and head home!`;
          const options = { body: bodyText };
          if (state.userAvatar) {
            options.icon = state.userAvatar;
          }
          new Notification("Shift Completed!", options);
          state.lastNotificationTime = now;
        }
      }
    }
  }

  // --- INITIALIZATION ---
  function init() {
    // Request notification permissions early
    if (typeof Notification !== "undefined" && Notification.permission === "default") {
      Notification.requestPermission();
    }

    buildUI();
    constrainToViewport();

    // Listen for window resize to adjust layout
    window.addEventListener("resize", constrainToViewport);

    // Periodic safety check to re-append widget if dynamic SPA routing clears it
    setInterval(() => {
      if (!document.getElementById("at-widget-container")) {
        buildUI();
        constrainToViewport();
      }
    }, 2000);

    // Initial load
    fetchAttendanceLogs();

    // Fast startup poller: retries every 2 seconds until the attendance logs are successfully loaded
    const startupInterval = setInterval(() => {
      if (state.attendanceData) {
        clearInterval(startupInterval);
      } else {
        fetchAttendanceLogs();
      }
    }, 2000);

    // 1-minute poller for fetching new attendance punches
    setInterval(fetchAttendanceLogs, 60000);

    // 1-second ticker for smooth real-time work & break minutes accumulation
    setInterval(() => {
      const settingsPanel = elements.card.querySelector("#at-settings-panel");
      const isSettingsActive =
        settingsPanel && settingsPanel.classList.contains("active");
      if (!state.isMinimized && !isSettingsActive) {
        updateUI();
      }
    }, 1000);
  }

  // Start after DOM is loaded or if it's already loaded
  if (
    document.readyState === "complete" ||
    document.readyState === "interactive"
  ) {
    init();
  } else {
    window.addEventListener("DOMContentLoaded", init);
  }
})();
