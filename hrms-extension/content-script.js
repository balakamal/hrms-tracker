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
    /* Widget Container - Obsidian Glass Theme */
    #at-widget-container {
      --at-bg-card: linear-gradient(165deg, rgba(20, 24, 33, 0.95) 0%, rgba(13, 16, 23, 0.98) 100%);
      --at-bg-badge: linear-gradient(145deg, rgba(28, 33, 44, 0.95) 0%, rgba(16, 20, 28, 0.97) 100%);
      --at-bg-panels: #12161F;
      --at-text-primary: #F8FAFC;
      --at-text-secondary: #94A3B8;
      --at-border: rgba(255, 255, 255, 0.08);
      --at-border-input: rgba(255, 255, 255, 0.12);
      --at-bg-input: rgba(255, 255, 255, 0.04);
      --at-bg-item: rgba(255, 255, 255, 0.035);
      --at-shadow: rgba(0, 0, 0, 0.5);
      --at-title-color: #FFFFFF;
      --at-panel-border: rgba(255, 255, 255, 0.08);
      --at-accent-work: #10B981;       /* Clean Emerald */
      --at-accent-exit: #3B82F6;       /* Vibrant Cobalt */
      --at-accent-actual: #818CF8;     /* Soft Indigo */
      --at-accent-gold: #F59E0B;       /* Warm Amber */
      --at-bg-header: transparent;

      position: fixed;
      bottom: 24px;
      right: 24px;
      z-index: 2147483647 !important;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Inter", "Helvetica Neue", sans-serif;
      color: var(--at-text-primary);
      transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
      -webkit-font-smoothing: antialiased;
      -moz-osx-font-smoothing: grayscale;
    }
    #at-widget-container.at-minimized:not(.at-custom-pos) {
      left: 50% !important;
      right: auto !important;
      bottom: 22px !important;
      top: auto !important;
      transform: translateX(-50%);
    }
    #at-widget-container.at-in-app.at-minimized:not(.at-custom-pos) {
      top: 14px !important;
      bottom: auto !important;
      left: 50% !important;
      right: auto !important;
      transform: translateX(-50%) !important;
    }
    #at-widget-container.at-theme-light {
      --at-bg-card: linear-gradient(165deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.96) 100%);
      --at-bg-badge: linear-gradient(145deg, rgba(255, 255, 255, 0.98) 0%, rgba(241, 245, 249, 0.96) 100%);
      --at-bg-panels: #FFFFFF;
      --at-text-primary: #0F172A;
      --at-text-secondary: #64748B;
      --at-border: rgba(0, 0, 0, 0.08);
      --at-border-input: rgba(0, 0, 0, 0.12);
      --at-bg-input: #F1F5F9;
      --at-bg-item: rgba(241, 245, 249, 0.65);
      --at-shadow: rgba(15, 23, 42, 0.12);
      --at-title-color: #0F172A;
      --at-panel-border: rgba(0, 0, 0, 0.06);
      --at-accent-work: #059669;
      --at-accent-exit: #2563EB;
      --at-accent-actual: #4F46E5;
      --at-accent-gold: #D97706;
      --at-bg-header: transparent;
    }
    #at-widget-container.at-theme-light .highlight-actual {
      color: #4F46E5 !important;
      text-shadow: none;
    }
    #at-widget-container.at-theme-light .highlight-exit {
      color: #2563EB !important;
      text-shadow: none;
    }
    #at-widget-container.at-theme-light .highlight-work {
      color: #059669 !important;
      text-shadow: none;
    }
    #at-widget-container.at-theme-light #at-wfh-badge,
    #at-widget-container.at-theme-light #at-badge-wfh {
      background: #FFFBEB !important;
      border-color: rgba(245, 158, 11, 0.3) !important;
      color: #D97706 !important;
    }
    #at-widget-container.at-dragging {
      transition: none !important;
    }

    /* Floating Badge Mode (Frosted Glass Pill) */
    .at-badge {
      display: flex;
      align-items: center;
      gap: 10px;
      background: var(--at-bg-badge);
      backdrop-filter: blur(24px) saturate(180%);
      -webkit-backdrop-filter: blur(24px) saturate(180%);
      border: 1px solid var(--at-border);
      border-radius: 9999px;
      padding: 10px 18px;
      box-shadow: 0 12px 32px -4px var(--at-shadow), inset 0 1px 0 rgba(255, 255, 255, 0.12);
      cursor: pointer;
      user-select: none;
      transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.2s ease, border-color 0.2s ease;
      color: var(--at-text-primary);
      touch-action: none;
    }
    .at-badge:hover {
      transform: translateY(-2px) scale(1.02);
      border-color: rgba(255, 255, 255, 0.18);
      box-shadow: 0 16px 36px -4px var(--at-shadow), inset 0 1px 0 rgba(255, 255, 255, 0.2);
    }
    #at-widget-container.at-theme-light .at-badge:hover {
      border-color: rgba(0, 0, 0, 0.15);
      box-shadow: 0 16px 36px -4px rgba(15, 23, 42, 0.15);
    }
    .at-badge:active {
      transform: scale(0.97);
    }
    .at-badge svg {
      width: 17px;
      height: 17px;
      fill: var(--at-accent-exit);
      filter: drop-shadow(0 0 6px rgba(59, 130, 246, 0.4));
    }
    .at-badge-text {
      font-weight: 700;
      font-size: 14px;
      letter-spacing: -0.01em;
    }

    /* Main Dashboard Card (macOS Sonoma / iOS 18 Frosted Glass) */
    .at-card {
      width: 380px;
      background: var(--at-bg-card);
      backdrop-filter: blur(28px) saturate(190%);
      -webkit-backdrop-filter: blur(28px) saturate(190%);
      border: 1px solid var(--at-border);
      border-radius: 24px;
      padding: 12px 18px 18px 18px;
      box-shadow: 0 24px 50px -12px var(--at-shadow), 0 8px 24px -4px rgba(0, 0, 0, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.16);
      position: relative;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      gap: 13px;
      transition: all 0.35s cubic-bezier(0.16, 1, 0.3, 1);
      color: var(--at-text-primary);
    }
    #at-widget-container.at-theme-light .at-card {
      box-shadow: 0 20px 45px -12px rgba(15, 23, 42, 0.15), 0 4px 12px -2px rgba(15, 23, 42, 0.06), inset 0 1px 0 rgba(255, 255, 255, 1);
    }

    /* Seamless Inset Apple Grab Bar */
    .at-grab-bar {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 0;
      margin: -2px 0 2px 0;
      cursor: grab;
      touch-action: none;
      user-select: none;
    }
    .at-grab-bar:active {
      cursor: grabbing;
    }
    .at-grab-pill {
      width: 36px;
      height: 4px;
      border-radius: 9999px;
      background: rgba(255, 255, 255, 0.2);
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .at-grab-bar:hover .at-grab-pill {
      background: rgba(255, 255, 255, 0.45);
      transform: scaleX(1.15);
    }
    .at-grab-bar:active .at-grab-pill {
      background: rgba(255, 255, 255, 0.7);
      transform: scaleX(1.15);
    }
    #at-widget-container.at-theme-light .at-grab-pill {
      background: rgba(0, 0, 0, 0.16);
    }

    /* Profile & Live Presence Header */
    .at-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      user-select: none;
      padding: 0 2px;
    }
    .at-profile-group {
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: 0;
    }
    .at-avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: linear-gradient(135deg, rgba(59, 130, 246, 0.3) 0%, rgba(16, 185, 129, 0.2) 100%);
      border: 1px solid rgba(255, 255, 255, 0.15);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      box-shadow: 0 4px 10px rgba(0, 0, 0, 0.25);
      overflow: hidden;
    }
    #at-widget-container.at-theme-light .at-avatar {
      background: linear-gradient(135deg, rgba(37, 99, 235, 0.15) 0%, rgba(16, 185, 129, 0.15) 100%);
      border-color: rgba(0, 0, 0, 0.1);
    }
    .at-avatar-initials {
      font-size: 13px;
      font-weight: 700;
      color: #FFFFFF;
      letter-spacing: 0.02em;
    }
    #at-widget-container.at-theme-light .at-avatar-initials {
      color: #1E293B;
    }
    .at-title-group {
      display: flex;
      flex-direction: column;
      gap: 1px;
      min-width: 0;
    }
    .at-title {
      font-weight: 700;
      font-size: 14.5px;
      letter-spacing: -0.01em;
      color: var(--at-title-color);
      line-height: 1.2;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .at-presence-row {
      display: flex;
      align-items: center;
      gap: 6px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .at-status-indicator {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      display: inline-block;
      flex-shrink: 0;
    }
    .at-status-in {
      background-color: #10B981;
      box-shadow: 0 0 8px rgba(16, 185, 129, 0.7);
    }
    .at-status-out {
      background-color: #F59E0B;
      box-shadow: 0 0 8px rgba(245, 158, 11, 0.7);
    }
    .at-status-done {
      background-color: #3B82F6;
      box-shadow: 0 0 8px rgba(59, 130, 246, 0.7);
    }
    .at-status-text {
      font-size: 11.5px;
      font-weight: 500;
      color: var(--at-text-secondary);
      letter-spacing: -0.01em;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .at-header-actions {
      display: flex;
      gap: 6px;
      align-items: center;
      flex-shrink: 0;
    }
    .at-btn {
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.09);
      color: var(--at-text-secondary);
      cursor: pointer;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      padding: 0;
    }
    .at-btn:hover {
      background: rgba(255, 255, 255, 0.14);
      border-color: rgba(255, 255, 255, 0.24);
      color: var(--at-text-primary);
      transform: scale(1.05);
    }
    .at-btn:active {
      transform: scale(0.95);
    }
    #at-widget-container.at-theme-light .at-btn {
      background: rgba(0, 0, 0, 0.04);
      border-color: rgba(0, 0, 0, 0.08);
    }
    #at-widget-container.at-theme-light .at-btn:hover {
      background: rgba(0, 0, 0, 0.08);
      border-color: rgba(0, 0, 0, 0.15);
      color: #0F172A;
    }
    .at-btn svg {
      width: 14px;
      height: 14px;
      fill: currentColor;
    }

    /* Hero Work Time & Progress Section */
    .at-hero-card {
      background: linear-gradient(135deg, rgba(255, 255, 255, 0.04) 0%, rgba(255, 255, 255, 0.015) 100%);
      border: 1px solid rgba(255, 255, 255, 0.07);
      border-radius: 16px;
      padding: 12px 14px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    #at-widget-container.at-theme-light .at-hero-card {
      background: linear-gradient(135deg, rgba(0, 0, 0, 0.03) 0%, rgba(0, 0, 0, 0.015) 100%);
      border-color: rgba(0, 0, 0, 0.06);
    }
    .at-hero-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
    }
    .at-hero-label-group {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .at-hero-label {
      font-size: 10px;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--at-text-secondary);
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .at-hero-label svg {
      width: 11px;
      height: 11px;
      fill: currentColor;
    }
    .at-hero-val {
      font-size: 24px;
      font-weight: 700;
      color: #10B981;
      letter-spacing: -0.02em;
      font-variant-numeric: tabular-nums;
      line-height: 1.1;
      text-shadow: 0 0 20px rgba(16, 185, 129, 0.25);
    }
    #at-widget-container.at-theme-light .at-hero-val {
      color: #059669;
      text-shadow: none;
    }
    .at-hero-progress-group {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 2px;
    }
    .at-progress-badge {
      font-size: 11px;
      font-weight: 700;
      color: #38BDF8;
      background: rgba(56, 189, 248, 0.12);
      border: 1px solid rgba(56, 189, 248, 0.25);
      padding: 2px 7px;
      border-radius: 9999px;
      letter-spacing: -0.01em;
      font-variant-numeric: tabular-nums;
    }
    #at-widget-container.at-theme-light .at-progress-badge {
      color: #0284C7;
      background: rgba(2, 132, 199, 0.08);
      border-color: rgba(2, 132, 199, 0.2);
    }
    .at-progress-rem {
      font-size: 11px;
      font-weight: 500;
      color: var(--at-text-secondary);
      font-variant-numeric: tabular-nums;
    }
    .at-progress-track {
      width: 100%;
      height: 6px;
      background: rgba(255, 255, 255, 0.08);
      border-radius: 9999px;
      overflow: hidden;
      position: relative;
    }
    #at-widget-container.at-theme-light .at-progress-track {
      background: rgba(0, 0, 0, 0.08);
    }
    .at-progress-fill {
      height: 100%;
      width: 0%;
      background: linear-gradient(90deg, #10B981 0%, #38BDF8 100%);
      border-radius: 9999px;
      box-shadow: 0 0 10px rgba(16, 185, 129, 0.5);
      transition: width 0.6s cubic-bezier(0.16, 1, 0.3, 1);
    }

    /* 4-Quadrant Frosted Data Shelf */
    .at-data-shelf {
      display: grid;
      grid-template-columns: 1fr 1fr;
      background: var(--at-bg-item);
      border: 1px solid var(--at-border);
      border-radius: 16px;
      overflow: hidden;
    }
    #at-widget-container.at-theme-light .at-data-shelf {
      background: rgba(241, 245, 249, 0.6);
      border-color: rgba(0, 0, 0, 0.07);
    }
    .at-shelf-cell {
      padding: 10px 14px;
      display: flex;
      flex-direction: column;
      gap: 2px;
      position: relative;
    }
    .at-shelf-cell:nth-child(1) {
      border-right: 1px solid var(--at-border);
      border-bottom: 1px solid var(--at-border);
    }
    .at-shelf-cell:nth-child(2) {
      border-bottom: 1px solid var(--at-border);
    }
    .at-shelf-cell:nth-child(3) {
      border-right: 1px solid var(--at-border);
    }
    .at-shelf-label {
      font-size: 9.5px;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--at-text-secondary);
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .at-shelf-label svg {
      width: 10px;
      height: 10px;
      fill: currentColor;
    }
    .at-shelf-val {
      font-size: 15px;
      font-weight: 700;
      color: var(--at-text-primary);
      letter-spacing: -0.01em;
      font-variant-numeric: tabular-nums;
      margin-top: 1px;
    }
    .highlight-exit {
      color: #3B82F6 !important;
      text-shadow: 0 0 12px rgba(59, 130, 246, 0.25);
    }
    .highlight-actual {
      color: #818CF8 !important;
      text-shadow: 0 0 12px rgba(129, 140, 248, 0.25);
    }
    .at-shelf-sub {
      font-size: 10px;
      color: var(--at-text-secondary);
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    /* Settings Subpanel */
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
      transition: transform 0.35s cubic-bezier(0.16, 1, 0.3, 1);
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
      padding-bottom: 12px;
    }
    .at-settings-title {
      font-weight: 700;
      font-size: 15px;
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
      border-radius: 10px;
      padding: 9px 12px;
      color: var(--at-text-primary);
      font-size: 13px;
      outline: none;
      font-family: inherit;
      transition: border-color 0.2s, box-shadow 0.2s;
    }
    .at-input option {
      background: var(--at-bg-panels);
      color: var(--at-text-primary);
    }
    .at-input:focus {
      border-color: #3B82F6;
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
    }
    .at-toggle {
      position: relative;
      display: inline-block;
      width: 40px;
      height: 22px;
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
      background-color: #334155;
      transition: .3s cubic-bezier(0.4, 0, 0.2, 1);
      border-radius: 20px;
    }
    .at-slider:before {
      position: absolute;
      content: "";
      height: 16px;
      width: 16px;
      left: 3px;
      bottom: 3px;
      background-color: white;
      transition: .3s cubic-bezier(0.4, 0, 0.2, 1);
      border-radius: 50%;
    }
    input:checked + .at-slider {
      background-color: #3B82F6;
    }
    input:checked + .at-slider:before {
      transform: translateX(18px);
    }
    .at-settings-section-title {
      font-size: 10px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: var(--at-text-secondary);
      margin-top: 12px;
      margin-bottom: 2px;
      padding-bottom: 4px;
      border-bottom: 1px solid var(--at-border);
    }
    .at-save-indicator {
      font-size: 11px;
      font-weight: 600;
      color: #10B981;
      opacity: 0;
      transition: opacity 0.3s ease;
      letter-spacing: -0.01em;
    }
    .at-btn-action {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--at-border);
      border-radius: 10px;
      padding: 9px 12px;
      color: var(--at-text-primary);
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      justify-content: space-between;
      align-items: center;
      width: 100%;
      box-sizing: border-box;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      font-family: inherit;
    }
    .at-btn-action:hover {
      background: rgba(255, 255, 255, 0.1);
      border-color: rgba(255, 255, 255, 0.2);
    }
    .at-btn-action:active {
      transform: scale(0.98);
    }
    .at-btn-logout {
      color: #EF4444 !important;
      border-color: rgba(239, 68, 68, 0.25) !important;
      background: rgba(239, 68, 68, 0.06) !important;
      justify-content: center !important;
    }
    .at-btn-logout:hover {
      background: rgba(239, 68, 68, 0.14) !important;
      border-color: rgba(239, 68, 68, 0.4) !important;
    }
    #at-widget-container.at-theme-light .at-btn-action {
      background: rgba(0, 0, 0, 0.03);
      border-color: rgba(0, 0, 0, 0.08);
      color: #0F172A;
    }
    #at-widget-container.at-theme-light .at-btn-action:hover {
      background: rgba(0, 0, 0, 0.06);
    }
    #at-widget-container.at-theme-light .at-btn-logout {
      color: #DC2626 !important;
      background: #FEF2F2 !important;
      border-color: #FECACA !important;
    }

    /* Loading Overlay */
    .at-loading-overlay {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(15, 17, 23, 0.88);
      backdrop-filter: blur(12px);
      z-index: 15;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 12px;
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.3s ease;
      border-radius: 20px;
    }
    #at-widget-container.at-theme-light .at-loading-overlay {
      background: rgba(255, 255, 255, 0.88);
    }
    .at-loading-overlay.active {
      opacity: 1;
      pointer-events: auto;
    }
    .at-spinner {
      width: 32px;
      height: 32px;
      border: 3px solid rgba(59, 130, 246, 0.2);
      border-top-color: #3B82F6;
      border-radius: 50%;
      animation: at-spin 0.8s linear infinite;
    }
    #at-widget-container.at-theme-light .at-spinner {
      border: 3px solid rgba(37, 99, 235, 0.15);
      border-top-color: #2563EB;
    }
    .at-loading-text {
      font-size: 12px;
      font-weight: 600;
      color: var(--at-text-secondary);
      letter-spacing: 0.3px;
    }
    @keyframes at-spin {
      to { transform: rotate(360deg); }
    }
    
    @media (max-width: 600px) {
      #at-widget-container {
        max-width: calc(100vw - 20px);
      }
      #at-widget-container.at-minimized:not(.at-custom-pos) {
        top: 14px !important;
        bottom: auto !important;
        left: 50% !important;
        right: auto !important;
        transform: translateX(-50%) !important;
      }
      #at-widget-container.at-expanded:not(.at-custom-pos) {
        bottom: 16px;
        left: 50%;
        right: auto;
        transform: translateX(-50%);
      }
      #at-widget-container.at-expanded .at-card {
        width: calc(100vw - 24px);
        max-width: 360px;
        border-radius: 24px;
        padding: 12px 14px 16px 14px;
      }
    }

    /* Fullscreen Mode (spacious and comfortable for editing on mobile/desktop) */
    #at-widget-container.at-fullscreen {
      position: fixed !important;
      top: 0 !important;
      left: 0 !important;
      right: 0 !important;
      bottom: 0 !important;
      width: 100vw !important;
      height: 100% !important;
      height: 100dvh !important;
      max-width: none !important;
      max-height: none !important;
      margin: 0 !important;
      transform: none !important;
      border-radius: 0 !important;
      z-index: 2147483647 !important;
      display: flex !important;
      flex-direction: column !important;
      background: var(--at-bg-panels) !important;
    }
    #at-widget-container.at-fullscreen .at-card {
      width: 100% !important;
      max-width: 100% !important;
      height: 100% !important;
      border-radius: 0 !important;
      border: none !important;
      box-shadow: none !important;
      box-sizing: border-box !important;
      padding: 16px 20px 24px 20px !important;
      display: flex !important;
      flex-direction: column !important;
      overflow-y: auto !important;
    }
    #at-widget-container.at-fullscreen .at-settings {
      border-radius: 0 !important;
      height: 100% !important;
      padding: 20px 24px 36px 24px !important;
      overflow-y: auto !important;
    }
    #at-widget-container.at-fullscreen .at-grab-bar {
      display: none !important;
    }
  `;

  // --- SVG ICONS ---
  const ICONS = {
    home: `<svg viewBox="0 0 24 24"><path d="M4 5c-1.1 0-2 .9-2 2v7c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2H4zm0 2h16v7H4V7zm-3 11c0 .55.45 1 1 1h20c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1zm11-9.5c-1.38 0-2.5 1.12-2.5 2.5h1.5c0-.55.45-1 1-1s1 .45 1 1h1.5c0-1.38-1.12-2.5-2.5-2.5zm0 3.5c-.55 0-1 .45-1 1s.45 1 1 1 1-.45 1-1-.45-1-1-1z"/></svg>`,
    clock: `<svg viewBox="0 0 24 24"><path d="M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm1 10.586l-2.793-2.793a1 1 0 0 1 1.414-1.414L13 9.586l4.207-4.207a1 1 0 0 1 1.414 1.414L13 12.586z"/></svg>`,
    login: `<svg viewBox="0 0 24 24"><path d="M19 3H5c-1.1 0-2 .9-2 2v4h2V5h14v14H5v-4H3v4c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-8 11.586l3.293-3.293a1 1 0 0 0 0-1.414L11 6.586 9.586 8l2.293 2.293H3v2h8.879L9.586 14.586 11 16z"/></svg>`,
    work: `<svg viewBox="0 0 24 24"><path d="M20 6h-4V4c0-1.11-.89-2-2-2h-4c-1.11 0-2 .89-2 2v2H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-6 0h-4V4h4v2z"/></svg>`,
    coffee: `<svg viewBox="0 0 24 24"><path d="M2 21h18v-2H2v2zM20 8h-2V5h2v3zm-4-5H4c-1.1 0-2 .9-2 2v8c0 2.2 1.8 4 4 4h8c2.2 0 4-1.8 4-4v-3h2c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"/></svg>`,
    exit: `<svg viewBox="0 0 24 24"><path d="M10.09 15.59L11.5 17l5-5-5-5-1.41 1.41L12.67 11H3v2h9.67l-2.58 2.59zM19 3H5c-1.11 0-2 .9-2 2v4h2V5h14v14H5v-4H3v4c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"/></svg>`,
    gear: `<svg viewBox="0 0 24 24"><path d="M19.43 12.98c.04-.32.07-.64.07-.98s-.03-.66-.07-.98l2.11-1.65c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.3-.61-.22l-2.49 1c-.52-.4-1.08-.73-1.69-.98l-.38-2.65C14.46 2.18 14.25 2 14 2h-4c-.25 0-.46.18-.49.42l-.38 2.65c-.61.25-1.17.59-1.69.98l-2.49-1c-.23-.09-.49 0-.61.22l-2 3.46c-.13.22-.07.49.12.64l2.11 1.65c-.04.32-.07.65-.07.98s.03.66.07.98l-2.11 1.65c-.19.15-.24.42-.12.64l2 3.46c.12.22.39.3.61.22l2.49-1c.52.4 1.08.73 1.69.98l.38 2.65c.03.24.24.42.49.42h4c.25 0 .46-.18.49-.42l.38-2.65c.61-.25 1.17-.59 1.69-.98l2.49 1c.23.09.49 0 .61-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.65zM12 15.5c-1.93 0-3.5-1.57-3.5-3.5s1.57-3.5 3.5-3.5 3.5 1.57 3.5 3.5-1.57 3.5-3.5 3.5z"/></svg>`,
    minimize: `<svg viewBox="0 0 24 24"><path d="M19 13H5v-2h14v2z"/></svg>`,
    fullscreen: `<svg viewBox="0 0 24 24"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>`,
    fullscreenExit: `<svg viewBox="0 0 24 24"><path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/></svg>`,
    phone: `<svg viewBox="0 0 24 24"><path d="M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z"/></svg>`,
    drag: `<svg viewBox="0 0 24 24"><path d="M11 18c0 1.1-.9 2-2 2s-2-.9-2-2 .9-2 2-2 2 .9 2 2zm-2-8c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0-6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm6 4c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/></svg>`,
    chevron: `<svg viewBox="0 0 24 24" style="width:14px;height:14px;fill:currentColor;"><path d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z"/></svg>`,
  };

  // --- STATE MANAGEMENT ---
  let state = {
    userId: localStorage.getItem("at_user_id") || null,
    userName: localStorage.getItem("at_user_name") || "User",
    targetHours: parseFloat(localStorage.getItem("at_target_hours") || "8.5"),
    policyWorkingRate: localStorage.getItem("at_policy_rate") ? parseFloat(localStorage.getItem("at_policy_rate")) : null,
    avgBreakMinutes: localStorage.getItem("at_avg_break_min") ? parseFloat(localStorage.getItem("at_avg_break_min")) : 60,
    notifyEnabled: localStorage.getItem("at_notify_enabled") !== "false",
    isMinimized: localStorage.getItem("at_is_minimized") === "true",
    isFullscreen: false,
    workspaceModeOverride: localStorage.getItem("at_ws_override") || "auto",
    theme: localStorage.getItem("at_theme") || "dark",
    userAvatar: localStorage.getItem("at_user_avatar") || null,
    userAvatarOriginal: localStorage.getItem("at_user_avatar_orig") || null,
    identityFetched: false,
    isWfh: (function () {
      const todayISO = (new Date()).toLocaleDateString("en-CA");
      if (localStorage.getItem("at_wfh_date") !== todayISO) {
        localStorage.setItem("at_wfh_date", todayISO);
        localStorage.setItem("at_wfh_mode", "false");
        return false;
      }
      return localStorage.getItem("at_wfh_mode") === "true";
    })(),
    leaveAlertsEnabled: localStorage.getItem("at_leave_alerts") !== "false",
    leaveInterval: parseInt(localStorage.getItem("at_leave_interval") || "5", 10),
    preExitEnabled: localStorage.getItem("at_pre_exit_notif") !== "false",
    timesheetEnabled: localStorage.getItem("at_timesheet_enabled") !== "false",
    timesheetTime: localStorage.getItem("at_timesheet_time") || "21:00",
    liveCardEnabled: localStorage.getItem("at_live_card_enabled") !== "false",
    imageMap: {},
    attendanceData: null,
    lastNotificationTime: 0,
    notificationInterval: (parseInt(localStorage.getItem("at_leave_interval") || "5", 10)) * 60 * 1000,
    loading: false,
  };

  // Sync initial WFH state and native settings from Android bridge if present
  if (state.workspaceModeOverride === "office") {
    state.isWfh = false;
  } else if (state.workspaceModeOverride === "wfh") {
    state.isWfh = true;
  } else if (window.AndroidApp) {
    if (typeof window.AndroidApp.getWfhMode === "function") {
      try {
        state.isWfh = window.AndroidApp.getWfhMode();
      } catch (_) {}
    }
    if (typeof window.AndroidApp.getNativeSettings === "function") {
      try {
        const raw = window.AndroidApp.getNativeSettings();
        if (raw) {
          const nativeData = JSON.parse(raw);
          if (typeof nativeData.leaveAlertEnabled === "boolean") state.leaveAlertsEnabled = nativeData.leaveAlertEnabled;
          if (typeof nativeData.leaveAlertInterval === "number") {
            state.leaveInterval = nativeData.leaveAlertInterval;
            state.notificationInterval = state.leaveInterval * 60 * 1000;
          }
          if (typeof nativeData.notifPreExit === "boolean") state.preExitEnabled = nativeData.notifPreExit;
          if (typeof nativeData.liveCardEnabled === "boolean") state.liveCardEnabled = nativeData.liveCardEnabled;
          if (typeof nativeData.timesheetEnabled === "boolean") state.timesheetEnabled = nativeData.timesheetEnabled;
          if (typeof nativeData.timesheetHour === "number" && typeof nativeData.timesheetMinute === "number") {
            state.timesheetTime = `${String(nativeData.timesheetHour).padStart(2, "0")}:${String(nativeData.timesheetMinute).padStart(2, "0")}`;
          }
        }
      } catch (_) {}
    }
  }

  // --- UTILS ---
  const formatMinutes = (totalMin) => {
    const h = Math.floor(totalMin / 60);
    const m = Math.floor(totalMin % 60);
    return `${h}h ${m}m`;
  };

  const getUserInitials = (name) => {
    if (!name || name === "User") return "HR";
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  };

  const renderAvatarHtml = () => {
    if (state.userAvatar) {
      return `<img src="${state.userAvatar}" alt="${state.userName}" onerror="this.style.display='none';if(this.nextElementSibling)this.nextElementSibling.style.display='inline-block';" style="width:100%;height:100%;border-radius:50%;object-fit:cover;" /><span class="at-avatar-initials" style="display:none;">${getUserInitials(state.userName)}</span>`;
    }
    return `<span class="at-avatar-initials">${getUserInitials(state.userName)}</span>`;
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

  const syncTokensToAndroid = () => {
    if (window.AndroidApp) {
      const token = localStorage.getItem("AccessToken");
      const refreshToken = localStorage.getItem("RefreshToken");
      if (token && token !== "null") {
        window.AndroidApp.saveTokens(token, refreshToken || "");
      }
    }
  };

  // --- SERVICE METHODS ---
  async function attemptTokenRefresh() {
    const refreshToken = localStorage.getItem("RefreshToken");
    if (!refreshToken) return false;

    try {
      const refreshUrl = "https://sso.pal.tech/token";
      const params = new URLSearchParams();
      params.append("grant_type", "refresh_token");
      params.append("client_id", "sso_prod");
      params.append("refresh_token", refreshToken);

      const res = await fetch(refreshUrl, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "Accept": "application/json",
        },
        body: params.toString(),
      });

      if (!res.ok) return false;
      const resData = await res.json();
      const data = resData.data || resData;
      const newAccess = data.access_token || data.accessToken;
      const newRefresh = data.refresh_token || data.refreshToken;

      if (newAccess) {
        localStorage.setItem("AccessToken", newAccess);
        if (newRefresh) {
          localStorage.setItem("RefreshToken", newRefresh);
        }
        syncTokensToAndroid();
        return true;
      }
    } catch (e) {
      console.error("Token refresh failed in tracker script:", e);
    }
    return false;
  }

  function getAuthHeaders(token) {
    const h = { Accept: "application/json" };
    if (token && token !== "null") {
      h["Authorization"] = `Bearer ${token}`;
    }
    return h;
  }

  async function fetchUserIdentity(force = false) {
    if (state.userId && state.userAvatar && !force) return;
    state.identityFetched = true;
    const url = "https://apps.pal.tech/hrms-backend/api/Employee/GetMyDetails";
    let token = localStorage.getItem("AccessToken");

    state.loading = true;
    updateLoadingUI();
    try {
      let response = await fetch(url, {
        credentials: "include",
        headers: getAuthHeaders(token),
      });

      if (response.status === 401) {
        const refreshed = await attemptTokenRefresh();
        if (refreshed) {
          token = localStorage.getItem("AccessToken");
          response = await fetch(url, {
            credentials: "include",
            headers: getAuthHeaders(token),
          });
        }
      }

      if (!response.ok) return;
      const resData = await response.json();
      if (resData && resData.data) {
        if (resData.data.id && (!state.userId || force)) {
          state.userId = resData.data.id;
          localStorage.setItem("at_user_id", state.userId);
        }
        if (resData.data.name || resData.data.firstName) {
          state.userName = resData.data.name || resData.data.firstName || state.userName;
          localStorage.setItem("at_user_name", state.userName);
        }

        // Cache details
        if (window.AndroidApp && typeof window.AndroidApp.saveUserData === "function") {
          window.AndroidApp.saveUserData(state.userId, state.userName);
        }

        // Extract user avatar photo UUID across all known HRMS photo property variants
        const photoId = resData.data.photo ||
                        resData.data.photoId ||
                        resData.data.imageId ||
                        resData.data.profilePhoto ||
                        resData.data.profilePhotoId ||
                        resData.data.profilePic ||
                        resData.data.avatar ||
                        resData.data.picture ||
                        resData.data.thumbnailId;

        const originalPhotoId = resData.data.originalPhoto ||
                                resData.data.originalPhotoId ||
                                resData.data.originalImageId ||
                                resData.data.originalImage ||
                                resData.data.originalId;

        if (photoId) {
          if (typeof photoId === "string" && (photoId.startsWith("http://") || photoId.startsWith("https://") || photoId.startsWith("data:"))) {
            state.userAvatar = photoId;
          } else {
            state.userAvatar = `https://apps.pal.tech/hrms-backend/images/${photoId}`;
          }
        } else {
          const domAvatar = document.querySelector(
            'img[src*="hrms-backend/images/"]',
          );
          if (domAvatar && domAvatar.src) {
            state.userAvatar = domAvatar.src;
          }
        }
        if (state.userAvatar) {
          localStorage.setItem("at_user_avatar", state.userAvatar);
        }

        if (originalPhotoId) {
          if (typeof originalPhotoId === "string" && (originalPhotoId.startsWith("http://") || originalPhotoId.startsWith("https://"))) {
            state.userAvatarOriginal = originalPhotoId;
          } else {
            state.userAvatarOriginal = `https://apps.pal.tech/hrms-backend/images/${originalPhotoId}`;
          }
          localStorage.setItem("at_user_avatar_orig", state.userAvatarOriginal);
          if (photoId && typeof photoId === "string") {
            state.imageMap[photoId] = originalPhotoId;
            if (window.AndroidApp && typeof window.AndroidApp.saveImageMapping === "function") {
              window.AndroidApp.saveImageMapping(photoId, originalPhotoId);
            }
          }
        }

        const nameEl = document.querySelector("#at-user-name");
        if (nameEl) nameEl.innerText = state.userName;
        const avatarEl = document.querySelector("#at-user-avatar");
        if (avatarEl) {
          avatarEl.innerHTML = renderAvatarHtml();
        }
      }
    } catch (e) {
      console.error("Error fetching user details in tracker script:", e);
    } finally {
      state.loading = false;
      updateLoadingUI();
    }
  }

  function checkDomForWfhStatus() {
    if (state.workspaceModeOverride === "office") {
      state.isWfh = false;
      return;
    }
    if (state.workspaceModeOverride === "wfh") {
      state.isWfh = true;
      return;
    }
    // If today's logs already exist, biometric punches at physical office prove In-Office
    if (state.attendanceData?.attendanceDailyLogs?.length > 0) {
      if (state.isWfh) {
        state.isWfh = false;
        const todayISO = getTodayISO();
        localStorage.setItem("at_wfh_date", todayISO);
        localStorage.setItem("at_wfh_mode", "false");
        if (window.AndroidApp && typeof window.AndroidApp.setWfhMode === "function") {
          window.AndroidApp.setWfhMode(false);
        }
        updateUI();
      }
      return;
    }
    // Only check if an explicit dedicated status element on today's view states WFH
    try {
      const todayCard = document.querySelector('.today-attendance, [data-today-status], .current-day-status');
      if (todayCard && !todayCard.closest("#at-widget-container")) {
        const text = (todayCard.textContent || "").trim();
        if (/^(wfh|work from home|remote|remote work)$/i.test(text)) {
          state.isWfh = true;
          const todayISO = getTodayISO();
          localStorage.setItem("at_wfh_date", todayISO);
          localStorage.setItem("at_wfh_mode", "true");
          if (window.AndroidApp && typeof window.AndroidApp.setWfhMode === "function") {
            window.AndroidApp.setWfhMode(true);
          }
          updateUI();
        }
      }
    } catch (_) {}
  }

  function interceptNetworkForImages() {
    if (typeof window.fetch === "function") {
      const origFetch = window.fetch;
      window.fetch = async function (...args) {
        const response = await origFetch.apply(this, args);
        try {
          const clone = response.clone();
          const ct = clone.headers.get("content-type") || "";
          if (ct.includes("application/json")) {
            clone.json().then(data => scanForImageIds(data)).catch(() => {});
          }
        } catch (_) {}
        return response;
      };
    }

    if (typeof XMLHttpRequest !== "undefined") {
      const origSend = XMLHttpRequest.prototype.send;
      XMLHttpRequest.prototype.send = function (...args) {
        this.addEventListener("load", function () {
          try {
            if (this.responseType === "" || this.responseType === "text" || this.responseType === "json") {
              const raw = typeof this.response === "string" ? JSON.parse(this.response) : this.response;
              scanForImageIds(raw);
            }
          } catch (_) {}
        });
        return origSend.apply(this, args);
      };
    }
  }

  function scanForImageIds(obj) {
    if (!obj || typeof obj !== "object") return;
    if (Array.isArray(obj)) {
      for (let i = 0; i < obj.length; i++) scanForImageIds(obj[i]);
      return;
    }
    const thumbId = obj.photo || obj.photoId || obj.imageId || obj.thumbnailId;
    const origId = obj.originalPhoto || obj.originalPhotoId || obj.originalImageId || obj.originalImage || obj.originalId;
    if (thumbId && origId && typeof thumbId === "string" && typeof origId === "string" && thumbId !== origId) {
      state.imageMap[thumbId] = origId;
      if (window.AndroidApp && typeof window.AndroidApp.saveImageMapping === "function") {
        window.AndroidApp.saveImageMapping(thumbId, origId);
      }
    }
    const keys = Object.keys(obj);
    for (let i = 0; i < keys.length; i++) {
      if (typeof obj[keys[i]] === "object" && obj[keys[i]] !== null) {
        scanForImageIds(obj[keys[i]]);
      }
    }
  }

  async function fetchEmployeeStatus() {
    if (state.workspaceModeOverride === "office") {
      state.isWfh = false;
      return;
    }
    if (state.workspaceModeOverride === "wfh") {
      state.isWfh = true;
      return;
    }

    if (!state.userId) await fetchUserIdentity();
    if (!state.userId) return;

    const url = `https://apps.pal.tech/hrms-backend/api/Employee/GetEmployeeStatus?employeeIds=${state.userId}`;
    let token = localStorage.getItem("AccessToken");

    try {
      let response = await fetch(url, {
        credentials: "include",
        headers: getAuthHeaders(token),
      });

      if (response.status === 401) {
        const refreshed = await attemptTokenRefresh();
        if (refreshed) {
          token = localStorage.getItem("AccessToken");
          response = await fetch(url, {
            credentials: "include",
            headers: getAuthHeaders(token),
          });
        }
      }

      if (!response.ok) return;
      const resData = await response.json();
      if (resData && resData.data) {
        let userStatus = null;
        if (Array.isArray(resData.data)) {
          userStatus = resData.data.find(item => String(item.employeeId || item.userId || item.id) === String(state.userId)) || resData.data[0];
        } else if (typeof resData.data === "object") {
          userStatus = resData.data[String(state.userId)] || resData.data[state.userId] || Object.values(resData.data)[0];
        }

        if (userStatus) {
          const wfhRaw = userStatus.WFH ?? userStatus.wfh ?? userStatus.isWfh ?? userStatus.isWFH;
          if (typeof wfhRaw !== "undefined" && wfhRaw !== null) {
            let isWfhActive = Number(wfhRaw) === 1 || wfhRaw === true || String(wfhRaw).toLowerCase() === "true";
            // In-office physical punches always take priority over remote mode
            if (state.attendanceData?.attendanceDailyLogs?.length > 0) {
              isWfhActive = false;
            }
            state.isWfh = isWfhActive;
            const todayISO = getTodayISO();
            localStorage.setItem("at_wfh_date", todayISO);
            localStorage.setItem("at_wfh_mode", isWfhActive ? "true" : "false");
            if (window.AndroidApp && typeof window.AndroidApp.setWfhMode === "function") {
              window.AndroidApp.setWfhMode(isWfhActive);
            }
            updateUI();
          }
        }
      }
    } catch (e) {
      console.error("Error fetching employee status in tracker script:", e);
    }
  }

  async function fetchOrganizationPolicy() {
    if (!state.userId) await fetchUserIdentity();
    if (!state.userId) return;

    const url = `https://apps.pal.tech/hrms-backend/api/OrganizationPolicy/GetMyPolicies?schemeId=3&policyId=0&employeeId=${state.userId}`;
    let token = localStorage.getItem("AccessToken");

    try {
      let response = await fetch(url, {
        credentials: "include",
        headers: getAuthHeaders(token),
      });

      if (response.status === 401) {
        const refreshed = await attemptTokenRefresh();
        if (refreshed) {
          token = localStorage.getItem("AccessToken");
          response = await fetch(url, {
            credentials: "include",
            headers: getAuthHeaders(token),
          });
        }
      }

      if (!response.ok) return;
      const resData = await response.json();
      if (resData && resData.data && resData.data.effectiveHours) {
        const rate = parseFloat(resData.data.effectiveHours.requiredWorkingRate);
        if (!isNaN(rate) && rate > 0) {
          state.policyWorkingRate = rate;
          // 100% = 8 hours 30 mins = 8.5 hours
          state.targetHours = (rate / 100.0) * 8.5;
          localStorage.setItem("at_target_hours", state.targetHours.toString());
          localStorage.setItem("at_policy_rate", rate.toString());
          if (window.AndroidApp && typeof window.AndroidApp.saveSettings === "function") {
            window.AndroidApp.saveSettings(state.targetHours);
          }
          updateUI();
        }
      }
    } catch (e) {
      console.error("Error fetching organization policy in tracker script:", e);
    }
  }

  async function fetchBreakAverageFromSummary() {
    if (!state.userId) await fetchUserIdentity();
    if (!state.userId) return;

    const today = new Date();
    const oneMonthAgo = new Date();
    oneMonthAgo.setDate(today.getDate() - 30);

    const pad = (n) => String(n).padStart(2, "0");
    const fmtDate = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

    const startDate = `${fmtDate(oneMonthAgo)}T00:00:00.000Z`;
    const endDate = `${fmtDate(today)}T00:00:00.000Z`;
    const todayLocalStr = fmtDate(today);

    const url = `https://apps.pal.tech/hrms-backend/api/Attendance/GetDailySummaryLog?startDate=${startDate}&endDate=${endDate}&userId=${state.userId}`;
    let token = localStorage.getItem("AccessToken");

    try {
      let response = await fetch(url, {
        credentials: "include",
        headers: getAuthHeaders(token),
      });

      if (response.status === 401) {
        const refreshed = await attemptTokenRefresh();
        if (refreshed) {
          token = localStorage.getItem("AccessToken");
          response = await fetch(url, {
            credentials: "include",
            headers: getAuthHeaders(token),
          });
        }
      }

      if (!response.ok) return;
      const resData = await response.json();
      const logs = Array.isArray(resData.data) ? resData.data : [];

      let totalBreakSeconds = 0;
      let workingDayCount = 0;

      for (let i = 0; i < logs.length; i++) {
        const log = logs[i];
        if (!log) continue;
        const logDateStr = (log.date || "").split("T")[0];
        // Exclude today's incomplete log
        if (logDateStr === todayLocalStr) continue;

        const gross = Number(log.grossHours) || 0;
        const eff = Number(log.effectiveHours) || 0;

        // Valid completed working day punch
        if (gross > 0 && eff > 0 && gross >= eff) {
          const diff = gross - eff; // in seconds
          totalBreakSeconds += diff;
          workingDayCount++;
        }
      }

      if (workingDayCount > 0) {
        const avgSec = totalBreakSeconds / workingDayCount;
        state.avgBreakMinutes = Math.max(15, Math.round(avgSec / 60)); // minimum 15m sanity check
      } else {
        state.avgBreakMinutes = 60; // 1-hour fallback
      }
      localStorage.setItem("at_avg_break_min", state.avgBreakMinutes.toString());
      updateUI();
    } catch (e) {
      console.error("Error fetching daily summary break average in tracker script:", e);
    }
  }

  async function fetchAttendanceLogs() {
    checkDomForWfhStatus();
    if ((!state.userId || !state.userAvatar) && !state.identityFetched) {
      await fetchUserIdentity();
    }
    if (!state.userId) return;

    // Fetch official employee status from HRMS backend (WFH: 1 check)
    fetchEmployeeStatus();

    // Fetch organization policy for dynamic target working rate
    fetchOrganizationPolicy();

    // Fetch 30-day daily summary to compute personalized average break duration
    fetchBreakAverageFromSummary();

    // Sync tokens with Android side in case they have changed or are freshly available
    syncTokensToAndroid();

    const todayDate = getTodayISO();
    const url = `https://apps.pal.tech/hrms-backend/api/Attendance/GetDailyLog?date=${todayDate}&userId=${state.userId}`;
    let token = localStorage.getItem("AccessToken");

    state.loading = true;
    updateLoadingUI();
    try {
      let response = await fetch(url, {
        credentials: "include",
        headers: getAuthHeaders(token),
      });

      if (response.status === 401) {
        const refreshed = await attemptTokenRefresh();
        if (refreshed) {
          token = localStorage.getItem("AccessToken");
          response = await fetch(url, {
            credentials: "include",
            headers: getAuthHeaders(token),
          });
        } else {
          state.userId = null;
          return;
        }
      }

      if (!response.ok) return;
      const resData = await response.json();
      if (resData && resData.data) {
        state.attendanceData = resData.data;
        if (state.attendanceData.attendanceDailyLogs && state.attendanceData.attendanceDailyLogs.length > 0) {
          if (state.workspaceModeOverride !== "wfh" && state.isWfh) {
            state.isWfh = false;
            const todayISO = getTodayISO();
            localStorage.setItem("at_wfh_date", todayISO);
            localStorage.setItem("at_wfh_mode", "false");
            if (window.AndroidApp && typeof window.AndroidApp.setWfhMode === "function") {
              window.AndroidApp.setWfhMode(false);
            }
          }
        }
        updateUI();
      }
    } catch (e) {
      console.error("Error fetching attendance logs in tracker script:", e);
    } finally {
      state.loading = false;
      updateLoadingUI();
    }
  }

  // Expose global hook for native token refresh updates
  window.atReloadAttendance = fetchAttendanceLogs;

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

    // Actual Exit Time (pure work completion without extra breaks)
    let actualExitTime = "Completed";
    if (!completed) {
      const actualExitDate = new Date(now.getTime() + remainingMinutes * 60000);
      actualExitTime = actualExitDate.toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
      });
    }

    // Estimated Exit Time (factoring in remaining estimated break)
    const avgBreakMin = typeof state.avgBreakMinutes === "number" && !isNaN(state.avgBreakMinutes) ? state.avgBreakMinutes : 60;
    const remainingBreakMinutes = Math.max(0, avgBreakMin - totalBreakMinutes);

    let exitTime = "Completed";
    if (!completed) {
      const exitDate = new Date(now.getTime() + (remainingMinutes + remainingBreakMinutes) * 60000);
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
      actualExitTime: actualExitTime,
      avgBreakMinutes: avgBreakMin,
      remainingBreakMinutes: remainingBreakMinutes,
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
    if (window.AndroidApp) {
      container.classList.add("at-in-app");
    }
    if (state.theme === "light") {
      container.classList.add("at-theme-light");
    }
    if (state.isMinimized) {
      container.classList.add("at-minimized");
    } else {
      container.classList.add("at-expanded");
    }
    document.body.appendChild(container);
    elements.container = container;

    // Suppress Android swipe-to-refresh whenever touching/interacting with in-app widget
    container.addEventListener("touchstart", () => {
      if (window.AndroidApp && typeof window.AndroidApp.setSwipeRefreshEnabled === "function") {
        window.AndroidApp.setSwipeRefreshEnabled(false);
      }
    }, { passive: true });

    container.addEventListener("touchend", () => {
      if (state.isMinimized && !state.isFullscreen) {
        const settingsPanel = elements.card?.querySelector("#at-settings-panel");
        const isSettingsActive = settingsPanel && settingsPanel.classList.contains("active");
        if (!isSettingsActive && window.AndroidApp && typeof window.AndroidApp.setSwipeRefreshEnabled === "function") {
          window.AndroidApp.setSwipeRefreshEnabled(true);
        }
      }
    }, { passive: true });

    // 1. Badge View (Apple Dynamic Island Live Activity Pill)
    const badge = document.createElement("div");
    badge.className = "at-badge";
    badge.innerHTML = `
      <span class="at-badge-dot" id="at-badge-dot"></span>
      <span id="at-badge-wfh" style="display:${state.isWfh ? "inline-flex" : "none"}; align-items:center; gap:4px; background: rgba(245, 158, 11, 0.16); border: 1px solid rgba(245, 158, 11, 0.35); padding: 1px 7px; border-radius: 12px; font-size: 9.5px; font-weight: 700; color: #F59E0B;"><span style="display:inline-block; width:10px; height:10px; fill:#F59E0B;">${ICONS.home}</span> WFH</span>
      <span class="at-badge-text" id="at-badge-work-time">--h --m</span>
      <span class="at-badge-divider"></span>
      <span class="at-badge-sub" id="at-badge-exit">--:--</span>
    `;
    // Click is handled manually in dragEnd to prevent WebView touch scrolling bugs
    container.appendChild(badge);
    elements.badge = badge;

    // 2. Card View (Expanded macOS/iOS Frosted Glass)
    const card = document.createElement("div");
    card.className = "at-card";
    card.style.display = "none";
    card.innerHTML = `
      <!-- Loading Overlay -->
      <div class="at-loading-overlay" id="at-loading-overlay">
        <div class="at-spinner"></div>
        <span class="at-loading-text">Fetching Live Logs...</span>
      </div>

      <!-- Inset Apple Grab Bar -->
      <div class="at-grab-bar" id="at-drag-handle" title="Drag to move widget">
        <div class="at-grab-pill"></div>
      </div>

      <!-- Profile & Live Presence Header -->
      <div class="at-header">
        <div class="at-profile-group">
          <div class="at-avatar" id="at-user-avatar">
            ${renderAvatarHtml()}
          </div>
          <div class="at-title-group">
            <span class="at-title" id="at-user-name">${state.userName}</span>
            <div class="at-presence-row">
              <span class="at-status-indicator at-status-out" id="at-status-indicator"></span>
              <span class="at-status-text" id="at-status-text">Determining status...</span>
              <span id="at-wfh-badge" style="display:${state.isWfh ? "inline-flex" : "none"}; align-items:center; gap:4px; font-size:9.5px; font-weight:700; color:#F59E0B; background: rgba(245, 158, 11, 0.16); border: 1px solid rgba(245, 158, 11, 0.35); padding: 1px 7px; border-radius: 12px; margin-left: 4px;"><span style="display:inline-block; width:10px; height:10px; fill:#F59E0B;">${ICONS.home}</span> WFH</span>
            </div>
          </div>
        </div>
        <div class="at-header-actions">
          <button class="at-btn" id="at-btn-settings" title="Settings">${ICONS.gear}</button>
          <button class="at-btn" id="at-btn-minimize" title="Minimize">${ICONS.minimize}</button>
        </div>
      </div>

      <!-- Hero Work Time & Shift Progress Section -->
      <div class="at-hero-card">
        <div class="at-hero-header">
          <div class="at-hero-label-group">
            <span class="at-hero-label">${ICONS.work} WORK TIME</span>
            <span class="at-hero-val" id="at-val-work-time">--h --m</span>
          </div>
          <div class="at-hero-progress-group">
            <span class="at-progress-badge" id="at-progress-percentage">0% Completed</span>
            <span class="at-progress-rem" id="at-progress-remaining">--h --m left</span>
          </div>
        </div>
        <div class="at-progress-track">
          <div class="at-progress-fill" id="at-progress-fill"></div>
        </div>
      </div>

      <!-- 4-Quadrant Frosted Data Shelf with Hairline Dividers -->
      <div class="at-data-shelf">
        <div class="at-shelf-cell">
          <span class="at-shelf-label">${ICONS.login} FIRST IN</span>
          <span class="at-shelf-val" id="at-val-first-in">--:--</span>
          <span class="at-shelf-sub">First entry</span>
        </div>
        <div class="at-shelf-cell">
          <span class="at-shelf-label">${ICONS.coffee} TOTAL BREAK</span>
          <span class="at-shelf-val" id="at-val-break-time">--h --m</span>
          <span class="at-shelf-sub" id="at-sub-break-avg">30d Avg: ~60m</span>
        </div>
        <div class="at-shelf-cell">
          <span class="at-shelf-label">${ICONS.exit} EST. EXIT</span>
          <span class="at-shelf-val highlight-exit" id="at-val-exit-time">--:--</span>
          <span class="at-shelf-sub" id="at-sub-exit-desc">with est. break</span>
        </div>
        <div class="at-shelf-cell">
          <span class="at-shelf-label">${ICONS.clock} ACTUAL EXIT</span>
          <span class="at-shelf-val highlight-actual" id="at-val-actual-exit-time">--:--</span>
          <span class="at-shelf-sub">no extra break</span>
        </div>
      </div>

      <!-- Settings Subpanel -->
      <div class="at-settings" id="at-settings-panel">
        <div class="at-settings-header">
          <div style="display:flex; align-items:center; gap:8px;">
            <span class="at-settings-title">Tracker Settings</span>
            <span class="at-save-indicator" id="at-save-indicator">Auto-saved ✓</span>
          </div>
          <div style="display:flex; align-items:center; gap:6px;">
            <button class="at-btn" id="at-btn-fullscreen-settings" title="Fullscreen Settings">${state.isFullscreen ? ICONS.fullscreenExit : ICONS.fullscreen}</button>
            <button class="at-btn" id="at-btn-close-settings" title="Close Settings">${ICONS.minimize}</button>
          </div>
        </div>

        <!-- Section: Shift Target -->
        <div class="at-settings-section-title">Shift Target</div>
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
          <label>Policy Target Rate</label>
          <span id="at-setting-policy-rate" style="font-size:11px; font-weight:700; color: var(--at-accent-exit);">
            ${state.policyWorkingRate ? `${state.policyWorkingRate}% (${formatMinutes(state.targetHours * 60)})` : "Standard (8h 30m)"}
          </span>
        </div>
        <div class="at-form-group row">
          <label>30-Day Break Avg</label>
          <span id="at-setting-break-avg" style="font-size:11px; font-weight:700; color: var(--at-accent-gold);">
            ~${state.avgBreakMinutes || 60}m per day
          </span>
        </div>

        <!-- Section: Smart Alerts & Reminders -->
        <div class="at-settings-section-title">Smart Alerts & Reminders</div>
        <div class="at-form-group row">
          <div>
            <div style="font-size:12px; font-weight:600; color:var(--at-text-primary);">Leave Office Alerts</div>
            <div style="font-size:10px; color:var(--at-text-secondary);">Repeats when shift is done</div>
          </div>
          <label class="at-toggle">
            <input type="checkbox" id="at-setting-leave-alerts" ${state.leaveAlertsEnabled ? "checked" : ""}>
            <span class="at-slider"></span>
          </label>
        </div>
        <div class="at-form-group row" id="at-row-leave-interval">
          <label>Alert Repeat Frequency</label>
          <select class="at-input" id="at-setting-leave-interval" style="padding: 4px 8px; font-size: 12px; border-radius: 8px;">
            <option value="2" ${state.leaveInterval === 2 ? "selected" : ""}>Every 2 min</option>
            <option value="5" ${state.leaveInterval === 5 || !state.leaveInterval ? "selected" : ""}>Every 5 min (Default)</option>
            <option value="10" ${state.leaveInterval === 10 ? "selected" : ""}>Every 10 min</option>
            <option value="15" ${state.leaveInterval === 15 ? "selected" : ""}>Every 15 min</option>
            <option value="30" ${state.leaveInterval === 30 ? "selected" : ""}>Every 30 min</option>
          </select>
        </div>
        <div class="at-form-group row">
          <div>
            <div style="font-size:12px; font-weight:600; color:var(--at-text-primary);">15-Min Pre-Exit Alert</div>
            <div style="font-size:10px; color:var(--at-text-secondary);">Advance reminder before exit</div>
          </div>
          <label class="at-toggle">
            <input type="checkbox" id="at-setting-pre-exit" ${state.preExitEnabled ? "checked" : ""}>
            <span class="at-slider"></span>
          </label>
        </div>
        <div class="at-form-group row">
          <div>
            <div style="font-size:12px; font-weight:600; color:var(--at-text-primary);">Daily Timesheet Reminder</div>
            <div style="font-size:10px; color:var(--at-text-secondary);">Evening submission prompt</div>
          </div>
          <label class="at-toggle">
            <input type="checkbox" id="at-setting-timesheet" ${state.timesheetEnabled ? "checked" : ""}>
            <span class="at-slider"></span>
          </label>
        </div>
        <div class="at-form-group row" id="at-row-timesheet-time">
          <label>Reminder Time</label>
          <input type="time" class="at-input" id="at-setting-timesheet-time" value="${state.timesheetTime || "21:00"}" style="padding: 4px 8px; font-size: 12px; border-radius: 8px;">
        </div>

        <!-- Section: Display & Widgets -->
        <div class="at-settings-section-title">Display & Widgets</div>
        <div class="at-form-group row">
          <label>Light Theme</label>
          <label class="at-toggle">
            <input type="checkbox" id="at-setting-theme" ${state.theme === "light" ? "checked" : ""}>
            <span class="at-slider"></span>
          </label>
        </div>
        <div class="at-form-group row" id="at-row-live-card" style="display:${window.AndroidApp ? "flex" : "none"};">
          <div>
            <div style="font-size:12px; font-weight:600; color:var(--at-text-primary);">Lock Screen Live Activity</div>
            <div style="font-size:10px; color:var(--at-text-secondary);">Real-time clock on lock screen & shade</div>
          </div>
          <label class="at-toggle">
            <input type="checkbox" id="at-setting-live-card" ${state.liveCardEnabled ? "checked" : ""}>
            <span class="at-slider"></span>
          </label>
        </div>
        <div class="at-form-group row" id="at-row-live-ongoing" style="display:${(window.AndroidApp && state.liveCardEnabled) ? "flex" : "none"}; padding-left: 10px; border-left: 2px solid var(--at-accent-blue); margin-top: -4px;">
          <div>
            <div style="font-size:11px; font-weight:600; color:var(--at-text-primary);">Keep Notification Pinned</div>
            <div style="font-size:9.5px; color:var(--at-text-secondary);">Uncheck to allow swipe to dismiss</div>
          </div>
          <label class="at-toggle" style="transform: scale(0.85); transform-origin: right center;">
            <input type="checkbox" id="at-setting-live-ongoing" ${state.liveCardOngoing !== false ? "checked" : ""}>
            <span class="at-slider"></span>
          </label>
        </div>
        <div class="at-form-group" id="at-row-notif-settings" style="display:${window.AndroidApp ? "block" : "none"}; margin-top: 4px;">
          <button type="button" class="at-btn-action" id="at-btn-notif-settings">
            <span>Notification Categories (Android Settings)</span>
            ${ICONS.chevron}
          </button>
        </div>
        <div class="at-form-group" id="at-row-widget-config" style="display:${window.AndroidApp ? "block" : "none"}; margin-top: 4px;">
          <button type="button" class="at-btn-action" id="at-btn-widget-config">
            <span>Customize Home Screen Widgets</span>
            ${ICONS.chevron}
          </button>
        </div>

        <!-- Section: Workspace & Account -->
        <div class="at-settings-section-title">Workspace & Account</div>
        <div class="at-form-group">
          <label>Workspace Mode</label>
          <select class="at-input" id="at-setting-workspace-select" style="width:100%; padding: 6px; background: var(--at-bg-input); color: var(--at-text-primary); border: 1px solid var(--at-border-input); border-radius: 8px; font-size: 13px;">
            <option value="auto" ${state.workspaceModeOverride === "auto" ? "selected" : ""}>Auto (Synced from HRMS)</option>
            <option value="office" ${state.workspaceModeOverride === "office" ? "selected" : ""}>In-Office (Biometrics Active)</option>
            <option value="wfh" ${state.workspaceModeOverride === "wfh" ? "selected" : ""}>Remote / WFH (Biometrics Paused)</option>
          </select>
          <div style="margin-top: 4px; font-size: 10px; color: var(--at-text-secondary); display: flex; justify-content: space-between;">
            <span>Current Status:</span>
            <span id="at-setting-workspace-mode" style="font-weight:700; color:${state.isWfh ? "var(--at-accent-gold)" : "var(--at-accent-work)"};">
              ${state.isWfh ? "Remote Workspace (WFH)" : "In-Office Mode"}
            </span>
          </div>
        </div>
        <div class="at-form-group" style="margin-top: 6px; margin-bottom: 8px;">
          <button type="button" class="at-btn-action at-btn-logout" id="at-btn-logout">
            Sign Out of Account
          </button>
        </div>
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
    const isInApp = Boolean(window.AndroidApp) || window.innerWidth <= 600;
    const savedX = localStorage.getItem("at_widget_x");
    const savedBottom = localStorage.getItem("at_widget_bottom");
    if (savedX !== null && savedBottom !== null && !(isInApp && state.isMinimized)) {
      container.classList.add("at-custom-pos");
      container.style.left = savedX;
      container.style.bottom = savedBottom;
      container.style.top = "auto";
      container.style.right = "auto";
    }

    setupDragging();

    // Bind event listeners
    card
      .querySelector("#at-btn-fullscreen-settings")
      ?.addEventListener("click", toggleFullscreen);
    card
      .querySelector("#at-btn-minimize")
      .addEventListener("click", toggleCollapse);
    const dragPill = card.querySelector("#at-drag-handle");
    if (dragPill) {
      dragPill.addEventListener("click", () => {
        if (!wasDragging) toggleCollapse();
      });
    }
    card
      .querySelector("#at-btn-settings")
      .addEventListener("click", openSettings);
    card
      .querySelector("#at-btn-close-settings")
      .addEventListener("click", closeSettings);

    const workspaceSelect = card.querySelector("#at-setting-workspace-select");
    workspaceSelect?.addEventListener("change", () => {
      const val = workspaceSelect.value;
      state.workspaceModeOverride = val;
      localStorage.setItem("at_ws_override", val);
      if (val === "office") {
        state.isWfh = false;
      } else if (val === "wfh") {
        state.isWfh = true;
      } else {
        if (state.attendanceData?.attendanceDailyLogs?.length > 0) {
          state.isWfh = false;
        } else {
          fetchEmployeeStatus();
        }
      }
      const todayISO = getTodayISO();
      localStorage.setItem("at_wfh_date", todayISO);
      localStorage.setItem("at_wfh_mode", state.isWfh ? "true" : "false");
      if (window.AndroidApp && typeof window.AndroidApp.setWfhMode === "function") {
        window.AndroidApp.setWfhMode(state.isWfh);
      }
      updateUI();
    });

    // Auto-save event bindings
    const hoursSelect = card.querySelector("#at-setting-hours-select");
    const minutesSelect = card.querySelector("#at-setting-minutes-select");
    const minutesInput = card.querySelector("#at-setting-minutes-input");
    const leaveAlertsToggle = card.querySelector("#at-setting-leave-alerts");
    const leaveIntervalSelect = card.querySelector("#at-setting-leave-interval");
    const preExitToggle = card.querySelector("#at-setting-pre-exit");
    const timesheetToggle = card.querySelector("#at-setting-timesheet");
    const timesheetTimeInput = card.querySelector("#at-setting-timesheet-time");
    const themeToggle = card.querySelector("#at-setting-theme");
    const liveCardToggle = card.querySelector("#at-setting-live-card");
    const liveOngoingToggle = card.querySelector("#at-setting-live-ongoing");
    const notifSettingsBtn = card.querySelector("#at-btn-notif-settings");
    const widgetConfigBtn = card.querySelector("#at-btn-widget-config");
    const logoutBtn = card.querySelector("#at-btn-logout");

    function syncDropdownsToInput() {
      const h = parseInt(hoursSelect.value, 10);
      const m = parseInt(minutesSelect.value, 10);
      minutesInput.value = h * 60 + m;
      autoSaveSettings();
    }

    function syncInputToDropdowns() {
      const parsed = parseWorkTimeInput(minutesInput.value);
      if (parsed !== null && parsed >= 0) {
        const h = Math.min(24, Math.floor(parsed / 60));
        const m = Math.min(59, Math.round(parsed % 60));
        hoursSelect.value = h;
        minutesSelect.value = m;
      }
      debounceAutoSave();
    }

    hoursSelect?.addEventListener("change", syncDropdownsToInput);
    minutesSelect?.addEventListener("change", syncDropdownsToInput);
    minutesInput?.addEventListener("input", syncInputToDropdowns);
    minutesInput?.addEventListener("change", autoSaveSettings);

    leaveAlertsToggle?.addEventListener("change", autoSaveSettings);
    leaveIntervalSelect?.addEventListener("change", autoSaveSettings);
    preExitToggle?.addEventListener("change", autoSaveSettings);
    timesheetToggle?.addEventListener("change", autoSaveSettings);
    timesheetTimeInput?.addEventListener("change", autoSaveSettings);
    themeToggle?.addEventListener("change", autoSaveSettings);
    liveCardToggle?.addEventListener("change", autoSaveSettings);
    liveOngoingToggle?.addEventListener("change", autoSaveSettings);

    notifSettingsBtn?.addEventListener("click", () => {
      if (window.AndroidApp && typeof window.AndroidApp.openNotificationSettings === "function") {
        window.AndroidApp.openNotificationSettings();
      }
    });

    widgetConfigBtn?.addEventListener("click", () => {
      if (window.AndroidApp && typeof window.AndroidApp.openWidgetConfig === "function") {
        window.AndroidApp.openWidgetConfig();
      }
    });

    logoutBtn?.addEventListener("click", () => {
      if (confirm("Are you sure you want to sign out?")) {
        if (window.AndroidApp && typeof window.AndroidApp.logout === "function") {
          window.AndroidApp.logout();
        } else {
          localStorage.clear();
          sessionStorage.clear();
          window.location.reload();
        }
      }
    });
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
    let initialTarget = null;

    function dragStart(e) {
      // Don't drag if in fullscreen or interacting with buttons, inputs, or dropdowns
      if (state.isFullscreen) return;
      if (e.target.closest("button") || e.target.closest("a") || e.target.closest("input") || e.target.closest("select")) {
        return;
      }
      if (window.AndroidApp && typeof window.AndroidApp.setSwipeRefreshEnabled === "function") {
        window.AndroidApp.setSwipeRefreshEnabled(false);
      }

      if (e.cancelable) {
        e.preventDefault();
      }

      const rect = container.getBoundingClientRect();
      initialX = e.clientX;
      initialY = e.clientY;
      initialTarget = e.target;

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
      } else {
        // Tap/click fallback since we preventDefault in dragStart
        if (initialTarget && (initialTarget.closest(".at-badge") || initialTarget.closest("#at-drag-handle"))) {
          toggleCollapse();
        }
      }
      if (state.isMinimized && !state.isFullscreen) {
        if (window.AndroidApp && typeof window.AndroidApp.setSwipeRefreshEnabled === "function") {
          window.AndroidApp.setSwipeRefreshEnabled(true);
        }
      }
      initialTarget = null;
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
        el.addEventListener("touchstart", (e) => {
          if (e.cancelable) e.preventDefault();
        }, { passive: false });
      }
    });

    window.addEventListener("pointermove", drag);
    window.addEventListener("pointerup", dragEnd);
    window.addEventListener("pointercancel", dragEnd);

    // Prevent default scroll on touch screens while active dragging is occurring
    window.addEventListener("touchmove", (e) => {
      if (active) {
        if (e.cancelable) e.preventDefault();
      }
    }, { passive: false });
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

    const container = elements.container;

    if (state.isMinimized) {
      if (state.isFullscreen) {
        toggleFullscreen();
      }
      if (window.AndroidApp && typeof window.AndroidApp.setSwipeRefreshEnabled === "function") {
        window.AndroidApp.setSwipeRefreshEnabled(true);
      }
      if (container) {
        container.classList.add("at-minimized");
        container.classList.remove("at-expanded");
        const isInApp = Boolean(window.AndroidApp) || window.innerWidth <= 600;
        if (isInApp || !localStorage.getItem("at_widget_x")) {
          container.classList.remove("at-custom-pos");
          container.style.left = "";
          container.style.bottom = "";
          container.style.top = "";
          container.style.right = "";
        }
      }
      elements.card.style.opacity = "0";
      setTimeout(() => {
        elements.card.style.display = "none";
        elements.badge.style.display = "flex";
        elements.badge.style.opacity = "1";
        if (container.classList.contains("at-custom-pos")) {
          constrainToViewport();
        }
      }, 150);
    } else {
      if (window.AndroidApp && typeof window.AndroidApp.setSwipeRefreshEnabled === "function") {
        window.AndroidApp.setSwipeRefreshEnabled(false);
      }
      if (container) {
        container.classList.add("at-expanded");
        container.classList.remove("at-minimized");
        const isInApp = Boolean(window.AndroidApp) || window.innerWidth <= 600;
        if (isInApp && !localStorage.getItem("at_widget_x")) {
          container.classList.remove("at-custom-pos");
          container.style.left = "";
          container.style.bottom = "";
          container.style.top = "";
          container.style.right = "";
        }
      }
      elements.badge.style.opacity = "0";
      setTimeout(() => {
        elements.badge.style.display = "none";
        elements.card.style.display = "flex";
        elements.card.style.opacity = "1";
        updateUI();
        if (container.classList.contains("at-custom-pos")) {
          constrainToViewport();
        }
      }, 150);
    }
  }

  function toggleFullscreen() {
    state.isFullscreen = !state.isFullscreen;
    const container = elements.container;
    if (!container) return;

    if (state.isFullscreen) {
      container.classList.add("at-fullscreen");
      const btnFsSettings = elements.card?.querySelector("#at-btn-fullscreen-settings");
      if (btnFsSettings) btnFsSettings.innerHTML = ICONS.fullscreenExit;
      if (window.AndroidApp && typeof window.AndroidApp.setWidgetFullscreen === "function") {
        window.AndroidApp.setWidgetFullscreen(true);
      } else if (window.AndroidApp && typeof window.AndroidApp.setSwipeRefreshEnabled === "function") {
        window.AndroidApp.setSwipeRefreshEnabled(false);
      }
    } else {
      container.classList.remove("at-fullscreen");
      const btnFsSettings = elements.card?.querySelector("#at-btn-fullscreen-settings");
      if (btnFsSettings) btnFsSettings.innerHTML = ICONS.fullscreen;
      if (window.AndroidApp && typeof window.AndroidApp.setWidgetFullscreen === "function") {
        window.AndroidApp.setWidgetFullscreen(false);
      } else if (window.AndroidApp && typeof window.AndroidApp.setSwipeRefreshEnabled === "function") {
        window.AndroidApp.setSwipeRefreshEnabled(state.isMinimized);
      }
      constrainToViewport();
    }
  }

  // Register window helper for native hardware back button
  window.atExitFullscreen = function () {
    if (state.isFullscreen) {
      toggleFullscreen();
      return true;
    }
    const settingsPanel = elements.card?.querySelector("#at-settings-panel");
    if (settingsPanel && settingsPanel.classList.contains("active")) {
      closeSettings();
      return true;
    }
    return false;
  };

  let autoSaveTimeout = null;
  let saveIndicatorTimeout = null;

  function flashSaveIndicator() {
    const indicator = elements.card?.querySelector("#at-save-indicator");
    if (!indicator) return;
    indicator.style.opacity = "1";
    if (saveIndicatorTimeout) clearTimeout(saveIndicatorTimeout);
    saveIndicatorTimeout = setTimeout(() => {
      indicator.style.opacity = "0";
    }, 1800);
  }

  function autoSaveSettings() {
    if (!elements.card) return;

    const hoursSelect = elements.card.querySelector("#at-setting-hours-select");
    const minutesSelect = elements.card.querySelector("#at-setting-minutes-select");
    const minutesInput = elements.card.querySelector("#at-setting-minutes-input");
    const leaveAlertsToggle = elements.card.querySelector("#at-setting-leave-alerts");
    const leaveIntervalSelect = elements.card.querySelector("#at-setting-leave-interval");
    const preExitToggle = elements.card.querySelector("#at-setting-pre-exit");
    const timesheetToggle = elements.card.querySelector("#at-setting-timesheet");
    const timesheetTimeInput = elements.card.querySelector("#at-setting-timesheet-time");
    const themeToggle = elements.card.querySelector("#at-setting-theme");
    const liveCardToggle = elements.card.querySelector("#at-setting-live-card");

    if (minutesInput) {
      const parsedMinutes = parseWorkTimeInput(minutesInput.value);
      state.targetHours = parsedMinutes === null || parsedMinutes <= 0 ? 8.5 : parsedMinutes / 60;
      localStorage.setItem("at_target_hours", state.targetHours.toString());
      if (window.AndroidApp && typeof window.AndroidApp.saveSettings === "function") {
        window.AndroidApp.saveSettings(state.targetHours);
      }
    }

    if (leaveAlertsToggle && leaveIntervalSelect) {
      state.leaveAlertsEnabled = leaveAlertsToggle.checked;
      state.leaveInterval = parseInt(leaveIntervalSelect.value, 10) || 5;
      state.notificationInterval = state.leaveInterval * 60 * 1000;
      state.notifyEnabled = state.leaveAlertsEnabled;
      localStorage.setItem("at_leave_alerts", state.leaveAlertsEnabled ? "true" : "false");
      localStorage.setItem("at_leave_interval", state.leaveInterval.toString());
      localStorage.setItem("at_notify_enabled", state.notifyEnabled ? "true" : "false");

      if (window.AndroidApp && typeof window.AndroidApp.setLeaveAlerts === "function") {
        window.AndroidApp.setLeaveAlerts(state.leaveAlertsEnabled, state.leaveInterval);
      }
    }

    if (preExitToggle) {
      state.preExitEnabled = preExitToggle.checked;
      localStorage.setItem("at_pre_exit_notif", state.preExitEnabled ? "true" : "false");
      if (window.AndroidApp && typeof window.AndroidApp.setPreExitAlerts === "function") {
        window.AndroidApp.setPreExitAlerts(state.preExitEnabled);
      }
    }

    if (timesheetToggle && timesheetTimeInput) {
      state.timesheetEnabled = timesheetToggle.checked;
      state.timesheetTime = timesheetTimeInput.value || "21:00";
      localStorage.setItem("at_timesheet_enabled", state.timesheetEnabled ? "true" : "false");
      localStorage.setItem("at_timesheet_time", state.timesheetTime);

      const parts = state.timesheetTime.split(":");
      const hour = parseInt(parts[0], 10) || 21;
      const min = parseInt(parts[1], 10) || 0;
      if (window.AndroidApp && typeof window.AndroidApp.setTimesheetReminder === "function") {
        window.AndroidApp.setTimesheetReminder(state.timesheetEnabled, hour, min);
      }
    }

    if (themeToggle) {
      state.theme = themeToggle.checked ? "light" : "dark";
      localStorage.setItem("at_theme", state.theme);
      if (state.theme === "light") {
        elements.container.classList.add("at-theme-light");
      } else {
        elements.container.classList.remove("at-theme-light");
      }
    }

    if (liveCardToggle && window.AndroidApp && typeof window.AndroidApp.setLiveCardEnabled === "function") {
      state.liveCardEnabled = liveCardToggle.checked;
      localStorage.setItem("at_live_card_enabled", state.liveCardEnabled ? "true" : "false");
      window.AndroidApp.setLiveCardEnabled(state.liveCardEnabled);
      const rowOngoing = elements.card?.querySelector("#at-row-live-ongoing");
      if (rowOngoing) {
        rowOngoing.style.display = (window.AndroidApp && state.liveCardEnabled) ? "flex" : "none";
      }
    }

    if (liveOngoingToggle && window.AndroidApp && typeof window.AndroidApp.setLiveCardOngoing === "function") {
      state.liveCardOngoing = liveOngoingToggle.checked;
      localStorage.setItem("at_live_card_ongoing", state.liveCardOngoing ? "true" : "false");
      window.AndroidApp.setLiveCardOngoing(state.liveCardOngoing);
    }

    flashSaveIndicator();
    updateUI();
  }

  function debounceAutoSave() {
    if (autoSaveTimeout) clearTimeout(autoSaveTimeout);
    autoSaveTimeout = setTimeout(() => {
      autoSaveSettings();
    }, 400);
  }

  function saveSettings() {
    autoSaveSettings();
  }

  function openSettings() {
    const card = elements.card;
    if (!card) return;

    // Sync from Android bridge if running in app
    if (window.AndroidApp && typeof window.AndroidApp.getNativeSettings === "function") {
      try {
        const raw = window.AndroidApp.getNativeSettings();
        if (raw) {
          const nativeData = JSON.parse(raw);
          if (typeof nativeData.leaveAlertEnabled === "boolean") {
            state.leaveAlertsEnabled = nativeData.leaveAlertEnabled;
          }
          if (typeof nativeData.leaveAlertInterval === "number") {
            state.leaveInterval = nativeData.leaveAlertInterval;
            state.notificationInterval = state.leaveInterval * 60 * 1000;
          }
          if (typeof nativeData.notifPreExit === "boolean") {
            state.preExitEnabled = nativeData.notifPreExit;
          }
          if (typeof nativeData.liveCardEnabled === "boolean") {
            state.liveCardEnabled = nativeData.liveCardEnabled;
          }
          if (typeof nativeData.liveCardOngoing === "boolean") {
            state.liveCardOngoing = nativeData.liveCardOngoing;
          }
          if (typeof nativeData.channelLiveEnabled === "boolean") {
            state.channelLiveEnabled = nativeData.channelLiveEnabled;
          }
          if (typeof nativeData.timesheetEnabled === "boolean") {
            state.timesheetEnabled = nativeData.timesheetEnabled;
          }
          if (typeof nativeData.timesheetHour === "number" && typeof nativeData.timesheetMinute === "number") {
            const hStr = String(nativeData.timesheetHour).padStart(2, "0");
            const mStr = String(nativeData.timesheetMinute).padStart(2, "0");
            state.timesheetTime = `${hStr}:${mStr}`;
          }
        }
      } catch (e) {
        console.error("Error reading native settings in openSettings:", e);
      }
    }

    const hoursSelect = card.querySelector("#at-setting-hours-select");
    const minutesSelect = card.querySelector("#at-setting-minutes-select");
    const minutesInput = card.querySelector("#at-setting-minutes-input");
    const policyRateEl = card.querySelector("#at-setting-policy-rate");
    const breakAvgEl = card.querySelector("#at-setting-break-avg");
    const wsModeEl = card.querySelector("#at-setting-workspace-mode");
    const leaveAlertsToggle = card.querySelector("#at-setting-leave-alerts");
    const leaveIntervalSelect = card.querySelector("#at-setting-leave-interval");
    const preExitToggle = card.querySelector("#at-setting-pre-exit");
    const timesheetToggle = card.querySelector("#at-setting-timesheet");
    const timesheetTimeInput = card.querySelector("#at-setting-timesheet-time");
    const themeToggle = card.querySelector("#at-setting-theme");
    const liveCardToggle = card.querySelector("#at-setting-live-card");
    const liveOngoingToggle = card.querySelector("#at-setting-live-ongoing");
    const rowLiveCard = card.querySelector("#at-row-live-card");
    const rowLiveOngoing = card.querySelector("#at-row-live-ongoing");
    const rowNotifSettings = card.querySelector("#at-row-notif-settings");
    const rowWidgetConfig = card.querySelector("#at-row-widget-config");

    if (hoursSelect && minutesSelect && minutesInput) {
      const totalMinutes = Math.round(state.targetHours * 60);
      const h = Math.min(24, Math.floor(totalMinutes / 60));
      const m = Math.min(59, Math.round(totalMinutes % 60));
      hoursSelect.value = h;
      minutesSelect.value = m;
      minutesInput.value = totalMinutes;
    }
    if (policyRateEl) {
      policyRateEl.innerText = state.policyWorkingRate
        ? `${state.policyWorkingRate}% (${formatMinutes(state.targetHours * 60)})`
        : "Standard (8h 30m)";
    }
    if (breakAvgEl) {
      breakAvgEl.innerText = `~${state.avgBreakMinutes || 60}m per day`;
    }
    if (wsModeEl) {
      const modeDesc = state.workspaceModeOverride === "office"
        ? "In-Office (Manual)"
        : state.workspaceModeOverride === "wfh"
        ? "Remote / WFH (Manual)"
        : state.isWfh ? "Remote Workspace (Synced)" : "In-Office (Synced)";
      wsModeEl.innerText = modeDesc;
      wsModeEl.style.color = state.isWfh ? "var(--at-accent-gold)" : "var(--at-accent-work)";
    }
    const wsSelect = card.querySelector("#at-setting-workspace-select");
    if (wsSelect) {
      wsSelect.value = state.workspaceModeOverride || "auto";
    }
    if (leaveAlertsToggle) {
      leaveAlertsToggle.checked = Boolean(state.leaveAlertsEnabled);
    }
    if (leaveIntervalSelect) {
      leaveIntervalSelect.value = String(state.leaveInterval || 5);
    }
    if (preExitToggle) {
      preExitToggle.checked = Boolean(state.preExitEnabled);
    }
    if (timesheetToggle) {
      timesheetToggle.checked = Boolean(state.timesheetEnabled);
    }
    if (timesheetTimeInput) {
      timesheetTimeInput.value = state.timesheetTime || "21:00";
    }
    if (themeToggle) {
      themeToggle.checked = state.theme === "light";
    }
    if (liveCardToggle) {
      liveCardToggle.checked = Boolean(state.liveCardEnabled);
    }
    if (liveOngoingToggle) {
      liveOngoingToggle.checked = state.liveCardOngoing !== false;
    }
    if (rowLiveCard) {
      rowLiveCard.style.display = window.AndroidApp ? "flex" : "none";
    }
    if (rowLiveOngoing) {
      rowLiveOngoing.style.display = (window.AndroidApp && state.liveCardEnabled) ? "flex" : "none";
    }
    if (rowNotifSettings) {
      rowNotifSettings.style.display = window.AndroidApp ? "block" : "none";
    }
    if (rowWidgetConfig) {
      rowWidgetConfig.style.display = window.AndroidApp ? "block" : "none";
    }

    if (window.AndroidApp && typeof window.AndroidApp.setSwipeRefreshEnabled === "function") {
      window.AndroidApp.setSwipeRefreshEnabled(false);
    }
    card.querySelector("#at-settings-panel").classList.add("active");
  }

  function closeSettings() {
    if (state.isFullscreen) {
      toggleFullscreen();
    }
    elements.card
      ?.querySelector("#at-settings-panel")
      ?.classList.remove("active");
    if (state.isMinimized && !state.isFullscreen) {
      if (window.AndroidApp && typeof window.AndroidApp.setSwipeRefreshEnabled === "function") {
        window.AndroidApp.setSwipeRefreshEnabled(true);
      }
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

  function updateUI() {
    // Check and update WFH badges on card and collapsed badge
    const wfhBadge = elements.card ? elements.card.querySelector("#at-wfh-badge") : null;
    if (wfhBadge) {
      wfhBadge.style.display = state.isWfh ? "inline-flex" : "none";
    }
    const badgeWfh = document.getElementById("at-badge-wfh");
    if (badgeWfh) {
      badgeWfh.style.display = state.isWfh ? "inline" : "none";
    }
    const wsModeEl = elements.card ? elements.card.querySelector("#at-setting-workspace-mode") : null;
    if (wsModeEl) {
      const modeDesc = state.workspaceModeOverride === "office"
        ? "In-Office (Manual)"
        : state.workspaceModeOverride === "wfh"
        ? "Remote / WFH (Manual)"
        : state.isWfh ? "Remote Workspace (Synced)" : "In-Office (Synced)";
      wsModeEl.innerText = modeDesc;
      wsModeEl.style.color = state.isWfh ? "var(--at-accent-gold)" : "var(--at-accent-work)";
    }
    const wsSelect = elements.card ? elements.card.querySelector("#at-setting-workspace-select") : null;
    if (wsSelect && wsSelect.value !== state.workspaceModeOverride) {
      wsSelect.value = state.workspaceModeOverride || "auto";
    }

    const metrics = calculateMetrics();
    if (!metrics) {
      // No logs yet today
      const today = new Date();
      const isWeekend = today.getDay() === 0 || today.getDay() === 6; // 0=Sun, 6=Sat
      const statusTextEl = elements.card.querySelector("#at-status-text");
      const indicator = elements.card.querySelector("#at-status-indicator");
      const badgeTimeEl = document.getElementById("at-badge-work-time");
      const exitTimeEl = elements.card.querySelector("#at-val-exit-time");
      const actualExitTimeEl = elements.card.querySelector("#at-val-actual-exit-time");

      if (state.isWfh) {
        if (statusTextEl) statusTextEl.innerText = "Working from Home";
        if (indicator) {
          indicator.className = "at-status-indicator";
          indicator.style.background = "#FFCE20";
          indicator.style.boxShadow = "0 0 8px #FFCE20";
        }
      } else if (isWeekend) {
        if (statusTextEl) statusTextEl.innerText = "Week Off";
        if (indicator) {
          indicator.className = "at-status-indicator";
          indicator.style.background = "#0F47C0";
          indicator.style.boxShadow = "0 0 8px rgba(15, 71, 192, 0.45)";
        }
        if (badgeTimeEl) badgeTimeEl.innerText = "Week Off";
        if (exitTimeEl) exitTimeEl.innerText = "Week Off";
        if (actualExitTimeEl) actualExitTimeEl.innerText = "Week Off";
      } else {
        if (statusTextEl) statusTextEl.innerText = "No attendance logs today";
      }
      return;
    }

    // Update floating collapsed badge (Apple Dynamic Island Live Activity Pill)
    const badgeTimeEl = document.getElementById("at-badge-work-time");
    if (badgeTimeEl) badgeTimeEl.innerText = formatMinutes(metrics.workMinutes);
    const badgeDotEl = document.getElementById("at-badge-dot");
    if (badgeDotEl) {
      badgeDotEl.className = "at-badge-dot" + (metrics.completed ? " at-dot-done" : (metrics.isClockedIn ? "" : " at-dot-out"));
    }
    const badgeExitEl = document.getElementById("at-badge-exit");
    if (badgeExitEl) {
      badgeExitEl.innerText = metrics.completed ? "Goal Met" : metrics.exitTime;
    }

    // Update status badge
    const indicator = elements.card.querySelector("#at-status-indicator");
    const textNode = elements.card.querySelector("#at-status-text");

    if (indicator && textNode) {
      indicator.className = "at-status-indicator";
      if (metrics.completed) {
        indicator.classList.add("at-status-done");
        textNode.innerText = "Shift Completed";
      } else if (metrics.isClockedIn) {
        indicator.classList.add("at-status-in");
        const today = new Date();
        const isWeekend = today.getDay() === 0 || today.getDay() === 6;
        const loc = metrics.lastLocation ? ` (${metrics.lastLocation})` : "";
        if (state.isWfh) {
          textNode.innerText = `In Office${loc} • WFH`;
        } else if (isWeekend) {
          textNode.innerText = `In Office${loc} • Weekend`;
        } else {
          textNode.innerText = `In Office${loc}`;
        }
      } else {
        indicator.classList.add("at-status-out");
        textNode.innerText = state.isWfh ? "WFH (Break)" : "Clocked Out / Break";
      }
    }

    // Update main grid values
    const firstInEl = elements.card.querySelector("#at-val-first-in");
    const workTimeEl = elements.card.querySelector("#at-val-work-time");
    const breakTimeEl = elements.card.querySelector("#at-val-break-time");
    const exitTimeEl = elements.card.querySelector("#at-val-exit-time");
    const actualExitTimeEl = elements.card.querySelector("#at-val-actual-exit-time");
    const subBreakAvgEl = elements.card.querySelector("#at-sub-break-avg");
    const subExitDescEl = elements.card.querySelector("#at-sub-exit-desc");

    if (firstInEl) firstInEl.innerText = metrics.firstClockIn;
    if (workTimeEl) workTimeEl.innerText = formatMinutes(metrics.workMinutes);
    if (breakTimeEl)
      breakTimeEl.innerText = formatMinutes(metrics.breakMinutes);
    if (exitTimeEl) exitTimeEl.innerText = metrics.exitTime;
    if (actualExitTimeEl) actualExitTimeEl.innerText = metrics.actualExitTime;
    if (subBreakAvgEl) {
      const avgMin = Math.round(metrics.avgBreakMinutes || 0);
      subBreakAvgEl.innerText = avgMin >= 60 ? `30d Avg: ~${formatMinutes(avgMin)}` : `30d Avg: ~${avgMin}m`;
    }
    if (subExitDescEl) {
      if (metrics.completed) {
        subExitDescEl.innerText = "Goal Met";
      } else {
        const remBreak = Math.round(metrics.remainingBreakMinutes || 0);
        subExitDescEl.innerText = remBreak > 0 ? `+${remBreak}m avg break` : "on track";
      }
    }

    // Update avatar and name
    const avatarEl = elements.card.querySelector("#at-user-avatar");
    if (avatarEl) {
      const currentImg = avatarEl.querySelector("img");
      if (state.userAvatar) {
        if (!currentImg || currentImg.src !== state.userAvatar) {
          avatarEl.innerHTML = renderAvatarHtml();
        }
      } else {
        if (currentImg || !avatarEl.querySelector(".at-avatar-initials")) {
          avatarEl.innerHTML = renderAvatarHtml();
        }
      }
    }
    const nameEl = elements.card.querySelector("#at-user-name");
    if (nameEl) nameEl.innerText = state.userName;

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

    // Handle Completed Notifications (shown repeating at state.notificationInterval while shift is completed and user is still clocked in)
    if (metrics.completed && metrics.isClockedIn && state.leaveAlertsEnabled) {
      triggerNotificationOnce(metrics.workMinutes);
    }

    // Handle 15-Minute Pre-Exit Advance Notification
    if (!metrics.completed && metrics.isClockedIn && state.preExitEnabled && metrics.remainingMinutes <= 15 && metrics.remainingMinutes > 0) {
      triggerPreExitNotificationOnce();
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
    const interval = state.notificationInterval || (state.leaveInterval * 60 * 1000) || (5 * 60 * 1000);
    if (now - state.lastNotificationTime > interval) {
      if (typeof Notification !== "undefined") {
        if (Notification.permission === "default") {
          Notification.requestPermission();
        } else if (Notification.permission === "granted") {
          const bodyText = `You have completed ${formatMinutes(workMinutes)} of biometric office time. Time to wrap up and head home!`;
          const options = { body: bodyText };
          if (state.userAvatar) {
            options.icon = state.userAvatar;
          }
          new Notification("Shift Completed! 🚀", options);
          state.lastNotificationTime = now;
        }
      }
    }
  }

  let preExitNotifiedDate = null;
  function triggerPreExitNotificationOnce() {
    const todayStr = getTodayLocalDateStr();
    if (preExitNotifiedDate === todayStr) return;
    if (typeof Notification !== "undefined" && Notification.permission === "granted") {
      new Notification("15 Minutes Remaining ⏳", {
        body: "Almost time to head out! You have approximately 15 minutes left in today's biometric shift.",
        icon: state.userAvatar || undefined,
      });
      preExitNotifiedDate = todayStr;
    }
  }

  // --- FULLSCREEN HIGH-RESOLUTION IMAGE VIEWER ---
  function initImageFullscreenObserver() {
    const styleId = "at-image-fullscreen-style";
    if (!document.getElementById(styleId)) {
      const style = document.createElement("style");
      style.id = styleId;
      style.textContent = `
        img:not(#at-widget-container img):not(.at-lightbox-img) {
          cursor: zoom-in !important;
          transition: filter 0.15s ease, transform 0.15s ease !important;
        }
        img:not(#at-widget-container img):not(.at-lightbox-img):hover {
          filter: brightness(1.06);
        }
        #at-lightbox-overlay {
          position: fixed;
          top: 0; left: 0; width: 100vw; height: 100vh;
          background: rgba(11, 12, 16, 0.95);
          backdrop-filter: blur(10px);
          z-index: 9999999;
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          opacity: 0;
          pointer-events: none;
          transition: opacity 0.22s ease-out;
          font-family: 'Outfit', 'Inter', system-ui, -apple-system, sans-serif;
          user-select: none;
        }
        #at-lightbox-overlay.at-active {
          opacity: 1;
          pointer-events: auto;
        }
        #at-lightbox-toolbar {
          position: absolute;
          top: 0; left: 0; right: 0; height: 60px;
          background: linear-gradient(180deg, rgba(22, 24, 34, 0.95) 0%, rgba(22, 24, 34, 0) 100%);
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 0 24px;
          z-index: 10;
        }
        #at-lightbox-title {
          font-size: 15px;
          font-weight: 600;
          color: #ffffff;
          max-width: 50%;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .at-lightbox-btn-group {
          display: flex;
          align-items: center;
          gap: 8px;
        }
        .at-lb-btn {
          background: rgba(255, 255, 255, 0.08);
          border: 1px solid rgba(255, 255, 255, 0.12);
          color: #f1f5f9;
          padding: 6px 12px;
          border-radius: 6px;
          font-size: 12px;
          font-weight: 600;
          cursor: pointer;
          display: inline-flex;
          align-items: center;
          gap: 4px;
          transition: all 0.15s ease;
        }
        .at-lb-btn:hover {
          background: rgba(255, 255, 255, 0.18);
          border-color: rgba(255, 255, 255, 0.25);
          color: #fff;
          transform: translateY(-1px);
        }
        .at-lb-btn-primary {
          background: #7DE8B3;
          color: #0B0C10;
          border: none;
        }
        .at-lb-btn-primary:hover {
          background: #A3F2C5;
          color: #0B0C10;
        }
        #at-lightbox-viewport {
          width: 100%;
          height: 100%;
          display: flex;
          align-items: center;
          justify-content: center;
          overflow: hidden;
          cursor: grab;
        }
        #at-lightbox-viewport:active {
          cursor: grabbing;
        }
        #at-lightbox-img {
          max-width: 88vw;
          max-height: 82vh;
          object-fit: contain;
          border-radius: 4px;
          box-shadow: 0 25px 60px rgba(0, 0, 0, 0.6);
          transform-origin: center center;
          transition: transform 0.05s linear;
          -webkit-user-drag: none;
        }
        #at-lightbox-hint {
          position: absolute;
          bottom: 20px;
          background: rgba(22, 24, 34, 0.8);
          border: 1px solid rgba(255, 255, 255, 0.1);
          color: #94a3b8;
          font-size: 12px;
          padding: 6px 16px;
          border-radius: 20px;
          pointer-events: none;
        }
      `;
      document.head.appendChild(style);
    }

    function resolveImageSources(target) {
      if (!target) return null;

      // Ignore clicks inside our own tracker UI widgets, settings modal, or lightbox
      if (target.closest("#at-widget-container, .at-lightbox-overlay, .at-settings-overlay")) {
        return null;
      }

      // Ignore clicks on standard form and interactive controls
      if (target.closest("button, input, select, textarea, [role='button'], label, summary, [contenteditable='true']")) {
        return null;
      }

      let originalUrl = null;
      let previewUrl = null;
      let title = "";

      // 1. Target MUST be an <img>, or a <picture>/<figure> wrapper
      let imgEl = null;
      if (target.tagName === "IMG") {
        imgEl = target;
      } else if (target.tagName === "PICTURE" || target.tagName === "FIGURE") {
        imgEl = target.querySelector("img");
      }

      // 2. Check if parent anchor links directly to an image file or backend image endpoint
      const parentLink = target.closest("a");
      if (parentLink && parentLink.href) {
        const href = parentLink.getAttribute("href") || "";
        const isImgHref = /\.(jpe?g|png|webp|gif|bmp)(\?.*)?$/i.test(href);
        const isBackendImgHref = /\/images\/[a-zA-Z0-9_-]+/i.test(href);
        if (isImgHref || isBackendImgHref) {
          originalUrl = parentLink.href;
          title = parentLink.getAttribute("title") || parentLink.innerText?.trim() || "";
        }
      }

      // If neither an actual image element nor an explicit image link was clicked, DO NOT open image viewer!
      if (!imgEl && !originalUrl) {
        return null;
      }

      if (imgEl) {
        // Filter out UI icons, tiny badges, decorative icons, status dots (<= 32px)
        const rect = imgEl.getBoundingClientRect();
        if ((rect.width > 0 && rect.width <= 32) || (rect.height > 0 && rect.height <= 32)) {
          return null;
        }

        previewUrl = imgEl.currentSrc || imgEl.src;
        if (!previewUrl || previewUrl.startsWith("data:image/svg") || previewUrl.includes("blank.gif") || previewUrl.length < 12) {
          return null;
        }

        if (!title) {
          title = imgEl.alt || imgEl.title || imgEl.getAttribute("aria-label") || "";
        }

        // Check if element has explicit original photo/image ID
        const explicitOrigId = imgEl.getAttribute("data-original-id") ||
                               imgEl.getAttribute("data-original-photo-id") ||
                               imgEl.getAttribute("data-original-image-id") ||
                               imgEl.getAttribute("data-original-image");
        if (explicitOrigId && !originalUrl) {
          originalUrl = `https://apps.pal.tech/hrms-backend/images/${explicitOrigId}`;
        }

        // Check high-res data attributes on the img element
        const highResAttr = imgEl.getAttribute("data-original") ||
                            imgEl.getAttribute("data-highres") ||
                            imgEl.getAttribute("data-full") ||
                            imgEl.getAttribute("data-zoom-src") ||
                            imgEl.getAttribute("data-large") ||
                            imgEl.getAttribute("data-src");
        if (highResAttr && !originalUrl) {
          try {
            originalUrl = new URL(highResAttr, window.location.href).href;
          } catch (_) {
            originalUrl = highResAttr;
          }
        }

        // Check if previewUrl points to /images/UUID and maps to an original ID
        if (!originalUrl && previewUrl && previewUrl.includes("/images/")) {
          const match = previewUrl.match(/\/images\/([a-zA-Z0-9_-]+)/);
          if (match && match[1]) {
            const thumbUuid = match[1];
            if (state.imageMap && state.imageMap[thumbUuid]) {
              originalUrl = `https://apps.pal.tech/hrms-backend/images/${state.imageMap[thumbUuid]}`;
            } else if (state.userAvatarOriginal && (previewUrl === state.userAvatar || previewUrl.includes(thumbUuid))) {
              originalUrl = state.userAvatarOriginal;
            }
          }
        }

        // Check srcset for highest resolution candidate
        const srcset = imgEl.getAttribute("srcset");
        if (srcset && !originalUrl) {
          try {
            const candidates = srcset.split(",").map(s => s.trim().split(/\s+/)).filter(parts => parts[0]);
            if (candidates.length > 0) {
              const best = candidates[candidates.length - 1][0];
              originalUrl = new URL(best, window.location.href).href;
            }
          } catch (_) {}
        }

        // De-thumbnail preview URL if still no separate original URL
        if (!originalUrl && previewUrl) {
          try {
            const parsed = new URL(previewUrl, window.location.href);
            let modified = false;
            ['w', 'width', 'h', 'height', 'size', 'thumb', 'thumbnail', 'maxwidth', 'maxheight'].forEach(p => {
              if (parsed.searchParams.has(p)) {
                parsed.searchParams.delete(p);
                modified = true;
              }
            });
            if (modified) {
              originalUrl = parsed.href;
            }
          } catch (_) {}
        }

        if (!originalUrl && previewUrl) {
          originalUrl = previewUrl;
        }
      } else if (originalUrl) {
        previewUrl = originalUrl;
      }

      if (!originalUrl && !previewUrl) return null;
      if (!originalUrl) originalUrl = previewUrl;
      if (!previewUrl) previewUrl = originalUrl;
      if (!title) title = "HRMS Image";

      return { originalUrl, previewUrl, title };
    }

    document.addEventListener("click", function (e) {
      // 1. Ignore clicks inside our own tracker UI widgets, settings modal, or lightbox
      if (e.target.closest("#at-widget-container, .at-lightbox-overlay, .at-settings-overlay")) {
        return;
      }

      // 2. Check if user clicked an actual image
      const resolved = resolveImageSources(e.target);
      if (resolved) {
        const { originalUrl, previewUrl, title } = resolved;
        if (window.AndroidApp && typeof window.AndroidApp.openImageFullscreen === "function") {
          e.preventDefault();
          e.stopPropagation();
          window.AndroidApp.openImageFullscreen(originalUrl, previewUrl, title);
          return;
        }
        e.preventDefault();
        e.stopPropagation();
        openDesktopLightbox(originalUrl, title);
        return;
      }

      // 3. Interactive web controls or page content clicks
      // Bars stay stable in modern One UI design without flicker
    }, true);
  }

  function openDesktopLightbox(imgUrl, imgTitle) {
    let overlay = document.getElementById("at-lightbox-overlay");
    if (!overlay) {
      overlay = document.createElement("div");
      overlay.id = "at-lightbox-overlay";
      overlay.innerHTML = `
        <div id="at-lightbox-toolbar">
          <div id="at-lightbox-title">Image Preview</div>
          <div class="at-lightbox-btn-group">
            <span id="at-lightbox-scale" style="font-size:12px;color:#94a3b8;margin-right:4px;">100%</span>
            <button class="at-lb-btn" id="at-lb-zoom-out" title="Zoom Out">-</button>
            <button class="at-lb-btn" id="at-lb-reset" title="Reset Zoom">1:1</button>
            <button class="at-lb-btn" id="at-lb-zoom-in" title="Zoom In">+</button>
            <a class="at-lb-btn at-lb-btn-primary" id="at-lb-download" download title="Download full resolution">⬇ Download</a>
            <button class="at-lb-btn" id="at-lb-open-tab" title="Open in new tab">↗ New Tab</button>
            <button class="at-lb-btn" id="at-lb-close" style="font-size:16px;padding:4px 10px;" title="Close (ESC)">✕</button>
          </div>
        </div>
        <div id="at-lightbox-viewport">
          <img id="at-lightbox-img" class="at-lightbox-img" src="" alt="" />
        </div>
        <div id="at-lightbox-hint">Scroll or +/- to Zoom • Drag to Pan • Double-click to Toggle • ESC to Close</div>
      `;
      document.body.appendChild(overlay);

      // Lightbox state
      let scale = 1;
      let panX = 0;
      let panY = 0;
      let isDragging = false;
      let startX = 0;
      let startY = 0;

      const img = overlay.querySelector("#at-lightbox-img");
      const scaleLabel = overlay.querySelector("#at-lightbox-scale");
      const viewport = overlay.querySelector("#at-lightbox-viewport");

      function updateTransform() {
        img.style.transform = `translate(${panX}px, ${panY}px) scale(${scale})`;
        scaleLabel.textContent = `${Math.round(scale * 100)}%`;
      }

      function resetTransform() {
        scale = 1;
        panX = 0;
        panY = 0;
        updateTransform();
      }

      // Zoom controls
      overlay.querySelector("#at-lb-zoom-in").addEventListener("click", (e) => {
        e.stopPropagation();
        scale = Math.min(5, scale + 0.3);
        updateTransform();
      });
      overlay.querySelector("#at-lb-zoom-out").addEventListener("click", (e) => {
        e.stopPropagation();
        scale = Math.max(0.4, scale - 0.3);
        updateTransform();
      });
      overlay.querySelector("#at-lb-reset").addEventListener("click", (e) => {
        e.stopPropagation();
        resetTransform();
      });

      // Mouse wheel zoom
      viewport.addEventListener("wheel", (e) => {
        e.preventDefault();
        const delta = e.deltaY > 0 ? -0.15 : 0.15;
        scale = Math.max(0.4, Math.min(5, scale + delta));
        updateTransform();
      }, { passive: false });

      // Double-click toggle zoom
      img.addEventListener("dblclick", (e) => {
        e.stopPropagation();
        if (scale > 1.2) {
          resetTransform();
        } else {
          scale = 2.2;
          panX = 0;
          panY = 0;
          updateTransform();
        }
      });

      // Drag / Pan
      viewport.addEventListener("mousedown", (e) => {
        if (e.target.closest("#at-lightbox-toolbar")) return;
        isDragging = true;
        startX = e.clientX - panX;
        startY = e.clientY - panY;
      });
      window.addEventListener("mousemove", (e) => {
        if (!isDragging) return;
        panX = e.clientX - startX;
        panY = e.clientY - startY;
        updateTransform();
      });
      window.addEventListener("mouseup", () => {
        isDragging = false;
      });

      // Close handlers
      function closeLightbox() {
        overlay.classList.remove("at-active");
        setTimeout(() => {
          img.src = "";
          resetTransform();
        }, 250);
      }
      overlay.querySelector("#at-lb-close").addEventListener("click", closeLightbox);
      viewport.addEventListener("click", (e) => {
        if (e.target === viewport) closeLightbox();
      });
      window.addEventListener("keydown", (e) => {
        if (e.key === "Escape" && overlay.classList.contains("at-active")) {
          closeLightbox();
        }
      });

      // Open new tab
      overlay.querySelector("#at-lb-open-tab").addEventListener("click", (e) => {
        e.stopPropagation();
        const currentUrl = img.src;
        if (currentUrl) window.open(currentUrl, "_blank");
      });
    }

    const titleEl = overlay.querySelector("#at-lightbox-title");
    const imgEl = overlay.querySelector("#at-lightbox-img");
    const downloadBtn = overlay.querySelector("#at-lb-download");

    titleEl.textContent = imgTitle || "Image Preview";
    imgEl.src = imgUrl;
    downloadBtn.href = imgUrl;

    const filename = imgUrl.split("/").pop()?.split("?")[0] || "hrms-image.png";
    downloadBtn.setAttribute("download", filename);

    // Reset zoom and show
    imgEl.style.transform = "translate(0px, 0px) scale(1)";
    overlay.querySelector("#at-lightbox-scale").textContent = "100%";
    overlay.classList.add("at-active");
  }

  // --- INITIALIZATION ---
  function init() {
    // Intercept network requests to capture thumbnail-to-original photo mappings
    interceptNetworkForImages();

    // Initialize Fullscreen Image Viewer on all page images
    initImageFullscreenObserver();

    // Request notification permissions early
    if (typeof Notification !== "undefined" && Notification.permission === "default") {
      Notification.requestPermission();
    }

    // Startup sync with Android SharedPreferences
    if (window.AndroidApp) {
      if (state.userId) {
        window.AndroidApp.saveUserData(state.userId, state.userName);
      }
      window.AndroidApp.saveSettings(state.targetHours);
      if (typeof window.AndroidApp.getNativeSettings === "function") {
        try {
          const raw = window.AndroidApp.getNativeSettings();
          if (raw) {
            const nativeData = JSON.parse(raw);
            if (typeof nativeData.leaveAlertEnabled === "boolean") state.leaveAlertsEnabled = nativeData.leaveAlertEnabled;
            if (typeof nativeData.leaveAlertInterval === "number") {
              state.leaveInterval = nativeData.leaveAlertInterval;
              state.notificationInterval = state.leaveInterval * 60 * 1000;
            }
            if (typeof nativeData.notifPreExit === "boolean") state.preExitEnabled = nativeData.notifPreExit;
            if (typeof nativeData.liveCardEnabled === "boolean") state.liveCardEnabled = nativeData.liveCardEnabled;
            if (typeof nativeData.timesheetEnabled === "boolean") state.timesheetEnabled = nativeData.timesheetEnabled;
            if (typeof nativeData.timesheetHour === "number" && typeof nativeData.timesheetMinute === "number") {
              state.timesheetTime = `${String(nativeData.timesheetHour).padStart(2, "0")}:${String(nativeData.timesheetMinute).padStart(2, "0")}`;
            }
          }
        } catch (_) {}
      }
      syncTokensToAndroid();
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
