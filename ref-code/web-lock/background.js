// Track unlocked sites per tab in-memory during this browser session
// Format: tabId -> Map of hostname -> unlockTimestamp
const unlockedTabs = new Map();

// Clean up when tab is closed
chrome.tabs.onRemoved.addListener((tabId) => {
  unlockedTabs.delete(tabId);
});

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  const tabId = sender.tab ? sender.tab.id : null;

  if (request.action === "checkLockStatus") {
    const { hostname } = request;
    
    chrome.storage.local.get(["lockedSites", "pin", "autoLockTimeout"], (data) => {
      const lockedSites = data.lockedSites || [];
      const hasPin = !!data.pin;
      
      // If no PIN is configured, don't lock anything
      if (!hasPin) {
        sendResponse({ isLocked: false, isDomainLocked: false });
        return;
      }

      // Check if hostname or a parent domain is locked
      const isDomainLocked = lockedSites.some(site => 
        hostname === site || hostname.endsWith("." + site)
      );

      if (!isDomainLocked) {
        sendResponse({ isLocked: false, isDomainLocked: false });
        return;
      }

      // If autoLockTimeout is "0" (Seketika), we never restore unlock state on fresh load
      const timeoutMinutes = data.autoLockTimeout !== undefined ? parseInt(data.autoLockTimeout, 10) : 5;
      if (timeoutMinutes === 0) {
        sendResponse({ isLocked: true, isDomainLocked: true });
        return;
      }

      // Check if already unlocked in this tab session and not expired
      const tabMap = unlockedTabs.get(tabId);
      const unlockTime = tabMap ? tabMap.get(hostname) : null;
      
      if (unlockTime) {
        const timeElapsed = Date.now() - unlockTime;
        const timeoutMs = timeoutMinutes * 60 * 1000;
        if (timeElapsed < timeoutMs) {
          sendResponse({ isLocked: false, isDomainLocked: true });
          return;
        } else {
          // Expired, remove from map
          tabMap.delete(hostname);
        }
      }

      sendResponse({ isLocked: true, isDomainLocked: true });
    });
    return true; // Keep channel open for async sendResponse
  }

  if (request.action === "unlockSiteForTab") {
    const { hostname } = request;
    if (tabId) {
      if (!unlockedTabs.has(tabId)) {
        unlockedTabs.set(tabId, new Map());
      }
      unlockedTabs.get(tabId).set(hostname, Date.now());
    }
    sendResponse({ success: true });
  }

  if (request.action === "lockSiteForTab") {
    const { hostname } = request;
    if (tabId && unlockedTabs.has(tabId)) {
      unlockedTabs.get(tabId).delete(hostname);
    }
    sendResponse({ success: true });
  }
});

