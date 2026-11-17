/**
 * HRMS Attendance Tracker — Chrome Extension Bootstrap Loader
 *
 * Instead of bundling the full tracker script here, this loader fetches the
 * latest version from GitHub at runtime. This means script fixes are deployed
 * instantly — users never need to reinstall the extension.
 *
 * Strategy:
 *  1. If a fresh cached copy exists (< 6h old) → inject it immediately,
 *     then refresh the cache silently in the background.
 *  2. If cache is stale or missing → fetch from GitHub, cache, inject.
 *  3. If network fails → fall back to stale cache (any age).
 *  4. If nothing is available → warn and exit gracefully.
 */
(function () {
  "use strict";

  const REMOTE_URL =
    "https://raw.githubusercontent.com/balakamal/hrms-tracker/main/hrms-attendance-tracker.js";
  const CACHE_SCRIPT_KEY = "at_ext_script_cache";
  const CACHE_TS_KEY     = "at_ext_script_ts";
  const CACHE_TTL_MS     = 6 * 60 * 60 * 1000; // 6 hours

  /**
   * Inject script text as a <script> element into the host page DOM.
   * Content scripts have full DOM access to the host page, so this is allowed
   * even under MV3 (we're not using eval or chrome.scripting on a remote URL).
   */
  function injectScript(code) {
    try {
      const el = document.createElement("script");
      el.textContent = code;
      (document.head || document.documentElement).appendChild(el);
      el.remove();
    } catch (err) {
      console.error("[HRMS Tracker] Script injection failed:", err);
    }
  }

  /** Fetch fresh script text from GitHub (bypassing browser cache). */
  async function fetchRemote() {
    const response = await fetch(REMOTE_URL, { cache: "no-store" });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.text();
  }

  /** Silently refresh cache without blocking injection. */
  function refreshCacheInBackground() {
    fetchRemote()
      .then(function (text) {
        try {
          localStorage.setItem(CACHE_SCRIPT_KEY, text);
          localStorage.setItem(CACHE_TS_KEY, Date.now().toString());
        } catch (_) {}
      })
      .catch(function () {}); // silent — user already has injected copy
  }

  async function loadAndInject() {
    let cached = null;
    let cachedTs = 0;
    try {
      cached  = localStorage.getItem(CACHE_SCRIPT_KEY);
      cachedTs = parseInt(localStorage.getItem(CACHE_TS_KEY) || "0", 10);
    } catch (_) {}

    const isFresh = cached && (Date.now() - cachedTs < CACHE_TTL_MS);

    if (isFresh) {
      // Fast path: inject from cache immediately, refresh in background
      injectScript(cached);
      refreshCacheInBackground();
      return;
    }

    // Slow path: fetch from GitHub, then inject
    try {
      const text = await fetchRemote();
      try {
        localStorage.setItem(CACHE_SCRIPT_KEY, text);
        localStorage.setItem(CACHE_TS_KEY, Date.now().toString());
      } catch (_) {}
      injectScript(text);
      return;
    } catch (fetchErr) {
      console.warn("[HRMS Tracker] Remote fetch failed:", fetchErr.message);
    }

    // Fallback: stale cache (any age)
    if (cached) {
      console.warn("[HRMS Tracker] Using stale cached script (offline or GitHub unreachable).");
      injectScript(cached);
      return;
    }

    // Nothing available — extension won't show widget this session
    console.error("[HRMS Tracker] No script available. Widget will not load.");
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", loadAndInject);
  } else {
    loadAndInject();
  }
})();
