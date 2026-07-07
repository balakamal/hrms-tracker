// --- PWA APPLICATION SCRIPT: DIRECT API LOGS SYNC ---

// --- STATE MANAGEMENT ---
let state = {
  token: localStorage.getItem('at_pwa_token') || null,
  userId: localStorage.getItem('at_pwa_userid') || null,
  userName: localStorage.getItem('at_pwa_username') || 'User',
  targetHours: parseFloat(localStorage.getItem('at_pwa_target_hours') || '8.5'),
  notifyEnabled: localStorage.getItem('at_pwa_notify_enabled') !== 'false',
  proxyUrl: (localStorage.getItem('at_pwa_proxy') !== null && localStorage.getItem('at_pwa_proxy') !== 'https://api.allorigins.win/raw?url=')
            ? localStorage.getItem('at_pwa_proxy') 
            : 'https://corsproxy.io/?url=',
  attendanceData: null,
  lastNotificationTime: 0,
  notificationInterval: 5 * 60 * 1000, // 5 minutes
  refreshIntervalId: null,
  tickerIntervalId: null
};

// --- SERVICE WORKER REGISTRATION ---
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('service_worker.js')
      .then(reg => console.log('SW: Registered successfully', reg.scope))
      .catch(err => console.error('SW: Registration failed', err));
  });
}

// --- UTILS ---
const formatMinutes = (totalMin) => {
  const h = Math.floor(totalMin / 60);
  const m = Math.floor(totalMin % 60);
  return `${h}h ${String(m).padStart(2, '0')}m`;
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

// Helper to make API requests (supports CORS proxy and checks for 401 Unauthorized)
async function makeApiRequest(apiUrl) {
  if (!state.token) {
    handleUnauthorized();
    throw new Error("No token configured.");
  }

  // Construct final URL with proxy if configured
  const requestUrl = state.proxyUrl ? `${state.proxyUrl}${encodeURIComponent(apiUrl)}` : apiUrl;
  
  const headers = {
    "Authorization": `Bearer ${state.token}`,
    "Accept": "application/json"
  };

  try {
    const response = await fetch(requestUrl, { headers });
    
    if (response.status === 401) {
      handleUnauthorized();
      throw new Error("Session expired or token is invalid (401 Unauthorized).");
    }

    if (!response.ok) {
      throw new Error(`HTTP error! Status: ${response.status}`);
    }

    const data = await response.json();
    return data;
  } catch (error) {
    console.error(`API Fetch Error: ${error.message}`);
    throw error;
  }
}

function handleUnauthorized() {
  state.token = null;
  state.userId = null;
  localStorage.removeItem('at_pwa_token');
  localStorage.removeItem('at_pwa_userid');
  
  // Stop pollers
  if (state.refreshIntervalId) clearInterval(state.refreshIntervalId);
  if (state.tickerIntervalId) clearInterval(state.tickerIntervalId);
  state.refreshIntervalId = null;
  state.tickerIntervalId = null;

  // Notify Android App if running in WebView wrapper
  if (window.AndroidApp && typeof window.AndroidApp.onLogout === 'function') {
    window.AndroidApp.onLogout();
  }

  showPanelStates();
  alert("Session expired or Access Token is invalid. Please log in and provide a new token.");
}

// --- API METHODS ---
async function fetchUserIdentity() {
  const apiUrl = "https://apps.pal.tech/hrms-backend/api/Employee/GetMyDetails";
  
  const syncBadge = document.getElementById('sync-status-badge');
  if (syncBadge) {
    syncBadge.className = 'badge-sync syncing';
    syncBadge.innerText = 'Syncing...';
  }

  try {
    const resData = await makeApiRequest(apiUrl);
    if (resData && resData.data) {
      state.userId = resData.data.id;
      state.userName = resData.data.name || resData.data.firstName || 'User';
      
      localStorage.setItem('at_pwa_userid', state.userId);
      localStorage.setItem('at_pwa_username', state.userName);
      
      // Notify Android App of user details if running in WebView wrapper
      if (window.AndroidApp && typeof window.AndroidApp.saveUserData === 'function') {
        window.AndroidApp.saveUserData(state.userId.toString(), state.userName);
      }
      
      document.getElementById('user-display-name').innerText = state.userName;
      return true;
    }
  } catch (e) {
    console.error("Error fetching user details in PWA app:", e);
    if (syncBadge) {
      syncBadge.className = 'badge-sync error';
      syncBadge.innerText = 'Error';
    }
  }
  return false;
}

async function fetchAttendanceLogs() {
  if (!state.userId) {
    const success = await fetchUserIdentity();
    if (!success) return;
  }

  const todayDate = getTodayISO();
  const apiUrl = `https://apps.pal.tech/hrms-backend/api/Attendance/GetDailyLog?date=${todayDate}&userId=${state.userId}`;
  
  const syncBadge = document.getElementById('sync-status-badge');
  if (syncBadge) {
    syncBadge.className = 'badge-sync syncing';
    syncBadge.innerText = 'Syncing...';
  }

  try {
    const resData = await makeApiRequest(apiUrl);
    if (resData && resData.data) {
      state.attendanceData = resData.data;
      updateUI();
      
      if (syncBadge) {
        syncBadge.className = 'badge-sync';
        syncBadge.innerText = 'Synced';
      }
    }
  } catch (e) {
    console.error("Error fetching attendance logs in PWA app:", e);
    if (syncBadge) {
      syncBadge.className = 'badge-sync error';
      syncBadge.innerText = 'Error';
    }
  }
}

// --- CORE CALCULATIONS ---
function calculateMetrics() {
  if (!state.attendanceData) return null;

  const logs = state.attendanceData.attendanceDailyLogs || [];
  if (logs.length === 0) return null;

  const todayStr = getTodayLocalDateStr();
  const parseTime = (timeStr) => new Date(`${todayStr}T${timeStr}`);

  // Map and sort daily logs by time
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
    lastLocation: lastLocation,
    timelineLogs: parsedLogs
  };
}

// --- UI UPDATE CONTROLLER ---
function updateUI() {
  const metrics = calculateMetrics();
  
  const statusIndicator = document.getElementById('status-indicator');
  const statusText = document.getElementById('status-text');
  
  if (!metrics) {
    // Reset to empty state
    statusIndicator.className = 'status-indicator status-out';
    statusText.innerText = 'No attendance logs today';
    
    document.getElementById('val-first-in').innerText = '--:--';
    document.getElementById('val-work-time').innerText = '0h 00m';
    document.getElementById('val-break-time').innerText = '0h 00m';
    document.getElementById('val-exit-time').innerText = '--:--';
    
    const circleBar = document.getElementById('progress-circle-bar');
    if (circleBar) circleBar.style.strokeDashoffset = '351.85';
    document.getElementById('progress-percentage').innerText = '0%';
    document.getElementById('progress-remaining').innerText = `${state.targetHours}h remaining`;
    
    document.getElementById('swipe-timeline-list').innerHTML = `<div class="empty-state">No swiping events logged today.</div>`;
    return;
  }

  // Update Status Banner
  statusIndicator.className = 'status-indicator';
  if (metrics.completed) {
    statusIndicator.classList.add('status-done');
    statusText.innerText = 'Completed shift! Time to leave.';
  } else if (metrics.isClockedIn) {
    statusIndicator.classList.add('status-in');
    statusText.innerText = `Clocked In (${metrics.lastLocation})`;
  } else {
    statusIndicator.classList.add('status-out');
    statusText.innerText = 'Clocked Out / Break';
  }

  // Update Core Metrics
  document.getElementById('val-first-in').innerText = metrics.firstClockIn;
  document.getElementById('val-work-time').innerText = formatMinutes(metrics.workMinutes);
  document.getElementById('val-break-time').innerText = formatMinutes(metrics.breakMinutes);
  document.getElementById('val-exit-time').innerText = metrics.exitTime;

  // Update Progress Segment
  const targetMin = state.targetHours * 60;
  const progressPercent = Math.min(100, Math.floor((metrics.workMinutes / targetMin) * 100));
  
  const circleBar = document.getElementById('progress-circle-bar');
  if (circleBar) {
    const circumference = 351.85;
    const offset = circumference - (progressPercent / 100) * circumference;
    circleBar.style.strokeDashoffset = offset;
  }
  document.getElementById('progress-percentage').innerText = `${progressPercent}%`;
  document.getElementById('progress-remaining').innerText = metrics.completed
    ? '0m remaining'
    : `${formatMinutes(metrics.remainingMinutes)} remaining`;

  // Render Timeline
  renderTimeline(metrics.timelineLogs);

  // Desktop/Mobile Notification Trigger
  if (metrics.completed && metrics.isClockedIn && state.notifyEnabled) {
    triggerNotification(metrics.workMinutes);
  }
}

// Render Timeline Logs
function renderTimeline(timelineLogs) {
  const listEl = document.getElementById('swipe-timeline-list');
  if (!timelineLogs || timelineLogs.length === 0) {
    listEl.innerHTML = `<div class="empty-state">No swiping events logged today.</div>`;
    return;
  }

  listEl.innerHTML = '';
  timelineLogs.forEach((log) => {
    const item = document.createElement('div');
    item.className = 'swipe-list-item';
    
    const displayTime = log.time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const badgeClass = log.isIn === 0 ? 'badge-in' : 'badge-out';
    const badgeText = log.isIn === 0 ? 'In' : 'Out';
    const locationStr = log.location ? ` @ ${log.location}` : '';

    item.innerHTML = `
      <div class="swipe-item-info">
        <span class="${badgeClass}">${badgeText}</span>
        <span class="swipe-item-time">${displayTime} <span class="help-text">${locationStr}</span></span>
      </div>
    `;
    listEl.appendChild(item);
  });
}

function triggerNotification(workMinutes) {
  const now = Date.now();
  if (now - state.lastNotificationTime > state.notificationInterval) {
    if (Notification.permission === 'default') {
      Notification.requestPermission();
    } else if (Notification.permission === 'granted') {
      const bodyText = `You have completed ${formatMinutes(workMinutes)} of biometric office time. Time to head home!`;
      new Notification("Shift Completed!", {
        body: bodyText
      });
      state.lastNotificationTime = now;
    }
  }
}

// --- VIEW MANAGEMENT ---
function showPanelStates() {
  const authPanel = document.getElementById('auth-panel');
  const mainDashboard = document.getElementById('main-dashboard');
  const timelineCard = document.getElementById('timeline-card');

  if (state.token) {
    authPanel.style.display = 'none';
    mainDashboard.style.display = 'block';
    timelineCard.style.display = 'block';
    document.getElementById('user-display-name').innerText = state.userName;
  } else {
    authPanel.style.display = 'block';
    mainDashboard.style.display = 'none';
    timelineCard.style.display = 'none';
    document.getElementById('user-display-name').innerText = 'Access Required';
  }
}

// --- INITIALIZATION & POLLING ---
function startTrackingPollers() {
  if (state.refreshIntervalId) clearInterval(state.refreshIntervalId);
  if (state.tickerIntervalId) clearInterval(state.tickerIntervalId);

  // Initial logs pull
  fetchAttendanceLogs();

  // Poll API logs every 60 seconds
  state.refreshIntervalId = setInterval(fetchAttendanceLogs, 60000);

  // Real-time calculation updater every 1 second
  state.tickerIntervalId = setInterval(() => {
    const isSettingsOpen = document.getElementById('settings-modal').style.display === 'flex';
    if (!isSettingsOpen && state.attendanceData) {
      updateUI();
    }
  }, 1000);
}

// --- EVENT LISTENERS ---

// Save Token Action
document.getElementById('btn-save-token').addEventListener('click', async () => {
  const tokenInput = document.getElementById('input-auth-token').value.trim();
  if (!tokenInput) return alert("Please paste a valid Access Token.");

  state.token = tokenInput;
  localStorage.setItem('at_pwa_token', tokenInput);

  // Attempt to fetch profile
  const success = await fetchUserIdentity();
  if (success) {
    showPanelStates();
    startTrackingPollers();
    document.getElementById('input-auth-token').value = '';
  } else {
    state.token = null;
    localStorage.removeItem('at_pwa_token');
    alert("Could not validate the token. Please make sure it is correct and not expired.");
  }
});

// Refresh API Logs manually
document.getElementById('btn-refresh-api').addEventListener('click', () => {
  if (state.token) {
    fetchAttendanceLogs();
  }
});

// Settings Modal controls
const settingsModal = document.getElementById('settings-modal');
document.getElementById('btn-settings').addEventListener('click', () => {
  document.getElementById('setting-hours').value = state.targetHours;
  document.getElementById('setting-notify').checked = state.notifyEnabled;
  document.getElementById('setting-proxy').value = state.proxyUrl;
  document.getElementById('setting-token').value = state.token || '';
  settingsModal.style.display = 'flex';
});

document.getElementById('btn-close-settings').addEventListener('click', () => {
  settingsModal.style.display = 'none';
});

document.getElementById('btn-save-settings').addEventListener('click', async () => {
  const hoursVal = parseFloat(document.getElementById('setting-hours').value);
  const notifyVal = document.getElementById('setting-notify').checked;
  const proxyVal = document.getElementById('setting-proxy').value.trim();
  const tokenVal = document.getElementById('setting-token').value.trim();

  state.targetHours = isNaN(hoursVal) ? 8.5 : hoursVal;
  state.notifyEnabled = notifyVal;
  state.proxyUrl = proxyVal;

  localStorage.setItem('at_pwa_target_hours', state.targetHours);
  localStorage.setItem('at_pwa_notify_enabled', state.notifyEnabled);
  localStorage.setItem('at_pwa_proxy', state.proxyUrl);

  // Notify Android App of target hours change if running in WebView wrapper
  if (window.AndroidApp && typeof window.AndroidApp.saveSettings === 'function') {
    window.AndroidApp.saveSettings(state.targetHours);
  }

  if (tokenVal && tokenVal !== state.token) {
    state.token = tokenVal;
    localStorage.setItem('at_pwa_token', tokenVal);
    
    // Validate new token
    await fetchUserIdentity();
  }

  settingsModal.style.display = 'none';
  showPanelStates();
  
  if (state.token) {
    startTrackingPollers();
  }
});

// Reset application data
document.getElementById('btn-reset').addEventListener('click', () => {
  if (confirm("Are you sure you want to log out and clear all local configurations?")) {
    state.token = null;
    state.userId = null;
    state.userName = 'User';
    state.attendanceData = null;
    
    localStorage.removeItem('at_pwa_token');
    localStorage.removeItem('at_pwa_userid');
    localStorage.removeItem('at_pwa_username');
    
    if (state.refreshIntervalId) clearInterval(state.refreshIntervalId);
    if (state.tickerIntervalId) clearInterval(state.tickerIntervalId);
    state.refreshIntervalId = null;
    state.tickerIntervalId = null;

    showPanelStates();
  }
});

// Request notification permission early
if (Notification.permission === 'default') {
  Notification.requestPermission();
}

function checkUrlParams() {
  const urlParams = new URLSearchParams(window.location.search);
  const token = urlParams.get('token');
  const refresh = urlParams.get('refresh');

  if (token) {
    state.token = token;
    localStorage.setItem('at_pwa_token', token);
    
    if (refresh) {
      localStorage.setItem('at_pwa_refresh_token', refresh);
    }
    
    // Reset cached userId so it fetches fresh details
    state.userId = null;
    localStorage.removeItem('at_pwa_userid');

    // Clean address bar
    const cleanUrl = window.location.protocol + "//" + window.location.host + window.location.pathname;
    window.history.replaceState({ path: cleanUrl }, '', cleanUrl);
  }
}

// Initial View Load
checkUrlParams();
showPanelStates();
if (state.token) {
  startTrackingPollers();
}
