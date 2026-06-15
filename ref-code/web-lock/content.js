(function () {
  // Only run in the top-level frame (don't block inside iframes like ads or widgets)
  if (window.self !== window.top) return;

  // Prevent double injection
  if (window.hasWebLockerInjected) return;
  window.hasWebLockerInjected = true;

  const hostname = window.location.hostname;

  function isContextValid() {
    return typeof chrome !== "undefined" && chrome.runtime && !!chrome.runtime.id;
  }

  // 1. Instantly hide the html page to prevent any leak of the original page content before verification
  const blockerStyle = document.createElement("style");
  blockerStyle.setAttribute("id", "web-locker-blocker");
  blockerStyle.innerHTML = `
    html {
      display: none !important;
      overflow: hidden !important;
    }
  `;
  document.documentElement.appendChild(blockerStyle);

  // 2. Check lock status with background worker
  if (!isContextValid()) {
    removeBlocker();
  } else {
    chrome.runtime.sendMessage({ action: "checkLockStatus", hostname }, (response) => {
      if (chrome.runtime.lastError || !response) {
        removeBlocker();
        return;
      }

      if (!response.isLocked) {
        // Not locked, reveal page immediately
        removeBlocker();
        if (response.isDomainLocked) {
          startIdleTimer();
        }
        return;
      }

      // Site is locked: adjust blocker style to reveal html but hide all children except our locker root
      blockerStyle.innerHTML = `
        html, body {
          overflow: hidden !important;
          height: 100vh !important;
          width: 100vw !important;
          margin: 0 !important;
          padding: 0 !important;
        }
        body > :not(#web-locker-root) {
          display: none !important;
        }
      `;

      // Initialize lock UI once DOM is loaded enough (or right away since we are at document_start)
      if (document.body) {
        initLockScreen();
      } else {
        document.addEventListener("DOMContentLoaded", initLockScreen);
      }
    });
  }

  function removeBlocker() {
    if (blockerStyle && blockerStyle.parentNode) {
      blockerStyle.parentNode.removeChild(blockerStyle);
    }
  }

  // --- Idle & Timeout Handling ---
  let idleCheckInterval = null;
  let activityListeners = [];
  let lastActivity = Date.now();

  function startIdleTimer() {
    stopIdleTimer();

    if (!isContextValid()) return;

    chrome.storage.local.get("autoLockTimeout", (data) => {
      if (!isContextValid()) return;
      const timeoutMinutes = data.autoLockTimeout !== undefined ? parseInt(data.autoLockTimeout, 10) : 5;
      
      // If 0, it locks strictly on page load/navigation
      if (timeoutMinutes <= 0) return;

      const timeoutMs = timeoutMinutes * 60 * 1000;
      lastActivity = Date.now();

      const updateActivity = () => {
        lastActivity = Date.now();
      };

      const events = ["mousemove", "keydown", "click", "scroll"];
      events.forEach(event => {
        window.addEventListener(event, updateActivity, { passive: true });
        activityListeners.push({ event, listener: updateActivity });
      });

      idleCheckInterval = setInterval(() => {
        if (!isContextValid()) {
          stopIdleTimer();
          return;
        }
        if (Date.now() - lastActivity > timeoutMs) {
          chrome.runtime.sendMessage({ action: "lockSiteForTab", hostname }, () => {
            stopIdleTimer();
            triggerLock();
          });
        }
      }, 5000);
    });
  }

  function stopIdleTimer() {
    if (idleCheckInterval) {
      clearInterval(idleCheckInterval);
      idleCheckInterval = null;
    }
    activityListeners.forEach(({ event, listener }) => {
      window.removeEventListener(event, listener);
    });
    activityListeners = [];
  }

  function triggerLock() {
    // 1. Re-add blocker style if not already in document
    if (!document.getElementById("web-locker-blocker")) {
      document.documentElement.appendChild(blockerStyle);
    }
    blockerStyle.innerHTML = `
      html, body {
        overflow: hidden !important;
        height: 100vh !important;
        width: 100vw !important;
        margin: 0 !important;
        padding: 0 !important;
      }
      body > :not(#web-locker-root) {
        display: none !important;
      }
    `;

    // 2. Load lock screen
    initLockScreen();
  }

  function initLockScreen() {
    // Check if lock screen already exists
    if (document.getElementById("web-locker-root")) return;

    const root = document.createElement("div");
    root.id = "web-locker-root";
    // Set style of root to ensure it behaves correctly outside Shadow DOM
    root.style.position = "fixed";
    root.style.top = "0";
    root.style.left = "0";
    root.style.width = "100vw";
    root.style.height = "100vh";
    root.style.zIndex = "2147483647";

    const shadow = root.attachShadow({ mode: "open" });

    // Embed content.css styles directly to bypass CSP and prevent FOUC
    const style = document.createElement("style");
    style.textContent = `
      @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;800&display=swap');

      :host {
        all: initial;
        font-family: 'Outfit', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      }

      .locker-overlay {
        position: fixed;
        top: 0;
        left: 0;
        width: 100vw;
        height: 100vh;
        background: #000000;
        z-index: 2147483647;
        display: flex;
        justify-content: center;
        align-items: center;
        color: #e5e5e7;
        overflow: hidden;
        transition: opacity 0.3s ease;
        box-sizing: border-box;
      }

      .locker-container {
        display: flex;
        flex-direction: column;
        align-items: center;
        background: #000000;
        border: 1px solid rgba(212, 178, 111, 0.15);
        border-radius: 24px;
        padding: 44px 40px;
        max-width: 360px;
        width: 90%;
        box-sizing: border-box;
        text-align: center;
        animation: slideUp 0.4s cubic-bezier(0.16, 1, 0.3, 1);
      }

      @keyframes slideUp {
        from {
          opacity: 0;
          transform: translateY(15px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }

      .locker-logo {
        width: 52px;
        height: 52px;
        background: transparent;
        border-radius: 12px;
        display: flex;
        justify-content: center;
        align-items: center;
        margin-bottom: 20px;
        overflow: hidden;
      }

      .locker-logo img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .locker-title {
        font-size: 18px;
        font-weight: 800;
        margin: 0 0 6px 0;
        text-transform: uppercase;
        letter-spacing: 1px;
        color: #d4b26f;
      }

      .locker-subtitle {
        font-size: 12px;
        color: rgba(255, 255, 255, 0.4);
        margin: 0 0 28px 0;
        font-weight: 400;
        letter-spacing: 0.2px;
      }

      .pin-display {
        display: flex;
        gap: 16px;
        margin-bottom: 32px;
        justify-content: center;
      }

      .pin-dot {
        width: 12px;
        height: 12px;
        border-radius: 50%;
        border: 2px solid rgba(255, 255, 255, 0.15);
        transition: all 0.15s cubic-bezier(0.16, 1, 0.3, 1);
      }

      .pin-dot.filled {
        background: #d4b26f;
        border-color: #d4b26f;
        box-shadow: 0 0 10px rgba(212, 178, 111, 0.3);
        transform: scale(1.2);
      }

      .pin-keypad {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 18px 24px;
        width: 240px;
        margin: 0 auto;
        margin-bottom: 8px;
      }

      .key-btn {
        background: #09090b;
        border: 1px solid rgba(212, 178, 111, 0.05);
        border-radius: 50%;
        width: 60px;
        height: 60px;
        font-size: 20px;
        font-weight: 500;
        color: #e5e5e7;
        cursor: pointer;
        display: flex;
        justify-content: center;
        align-items: center;
        transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
        user-select: none;
        -webkit-tap-highlight-color: transparent;
        box-sizing: border-box;
      }

      .key-btn:hover {
        background: #d4b26f;
        color: #000000;
        border-color: #d4b26f;
      }

      .key-btn:active {
        transform: scale(0.9);
      }

      .key-btn.action-btn {
        font-size: 10px;
        font-weight: 700;
        color: rgba(255, 255, 255, 0.4);
        text-transform: uppercase;
        border: none;
        background: transparent;
      }

      .key-btn.action-btn:hover {
        color: #d4b26f;
        background: transparent;
        border: none;
      }

      .key-btn.action-btn svg {
        width: 18px;
        height: 18px;
        fill: currentColor;
      }

      @keyframes shake {
        0%, 100% { transform: translateX(0); }
        20%, 60% { transform: translateX(-6px); }
        40%, 80% { transform: translateX(6px); }
      }

      .locker-container.shake {
        animation: shake 0.3s ease;
        border-color: #ff3b30;
      }

      .locker-container.shake .pin-dot {
        border-color: #ff3b30;
      }
      .locker-container.shake .pin-dot.filled {
        background: #ff3b30;
        border-color: #ff3b30;
      }
    `;
    shadow.appendChild(style);

    // Build Lock UI Structure
    const overlay = document.createElement("div");
    overlay.className = "locker-overlay";

    overlay.innerHTML = `
      <div class="locker-container">
        <div class="locker-logo">
          <img src="${isContextValid() ? chrome.runtime.getURL('image.png') : ''}" alt="Logo">
        </div>
        <h2 class="locker-title">Halaman Terkunci</h2>
        <p class="locker-subtitle">Masukkan PIN Anda untuk mengakses ${hostname}</p>
        
        <div class="pin-display">
          <div class="pin-dot"></div>
          <div class="pin-dot"></div>
          <div class="pin-dot"></div>
          <div class="pin-dot"></div>
          <div class="pin-dot"></div>
          <div class="pin-dot"></div>
        </div>

        <div class="pin-keypad">
          <button class="key-btn" data-key="1">1</button>
          <button class="key-btn" data-key="2">2</button>
          <button class="key-btn" data-key="3">3</button>
          <button class="key-btn" data-key="4">4</button>
          <button class="key-btn" data-key="5">5</button>
          <button class="key-btn" data-key="6">6</button>
          <button class="key-btn" data-key="7">7</button>
          <button class="key-btn" data-key="8">8</button>
          <button class="key-btn" data-key="9">9</button>
          <button class="key-btn action-btn" data-key="clear">CLEAR</button>
          <button class="key-btn" data-key="0">0</button>
          <button class="key-btn action-btn" data-key="backspace">
            <svg viewBox="0 0 24 24">
              <path d="M22 3H7c-.69 0-1.23.35-1.59.88L0 12l5.41 8.11c.36.53.9.89 1.59.89h15c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-3 12.59L17.59 17 14 13.41 10.41 17 9 15.59 12.59 12 9 8.41 10.41 7 14 10.59 17.59 7 19 8.41 15.41 12 19 15.59z"/>
            </svg>
          </button>
        </div>
      </div>
    `;

    shadow.appendChild(overlay);
    document.documentElement.appendChild(root);

    // Input States
    let pinInput = "";
    const container = overlay.querySelector(".locker-container");
    const dots = overlay.querySelectorAll(".pin-dot");

    function updateDots() {
      dots.forEach((dot, index) => {
        if (index < pinInput.length) {
          dot.classList.add("filled");
        } else {
          dot.classList.remove("filled");
        }
      });
    }

    function handleKeyPress(key) {
      if (key === "clear") {
        pinInput = "";
        updateDots();
      } else if (key === "backspace") {
        pinInput = pinInput.slice(0, -1);
        updateDots();
      } else if (/^\d$/.test(key)) {
        if (pinInput.length < 6) {
          pinInput += key;
          updateDots();
          
          if (pinInput.length === 6) {
            // Auto check PIN
            verifyPin(pinInput);
          }
        }
      }
    }

    // Keypad Click Event Listeners
    overlay.querySelectorAll(".key-btn").forEach(btn => {
      btn.addEventListener("click", () => {
        const key = btn.getAttribute("data-key");
        handleKeyPress(key);
      });
    });

    // Keyboard Support
    const onKeyDown = (e) => {
      if (/^\d$/.test(e.key)) {
        handleKeyPress(e.key);
      } else if (e.key === "Backspace") {
        handleKeyPress("backspace");
      } else if (e.key === "Escape") {
        handleKeyPress("clear");
      }
    };
    window.addEventListener("keydown", onKeyDown);

    function verifyPin(enteredPin) {
      if (!isContextValid()) return;
      chrome.storage.local.get("pin", (data) => {
        if (!isContextValid()) return;
        const actualPin = data.pin;
        
        if (enteredPin === actualPin) {
          // Success: Unlock site for this tab
          chrome.runtime.sendMessage({ action: "unlockSiteForTab", hostname }, () => {
            // Fade out animations
            overlay.style.opacity = "0";
            window.removeEventListener("keydown", onKeyDown);
            setTimeout(() => {
              removeBlocker();
              if (root.parentNode) {
                root.parentNode.removeChild(root);
              }
              startIdleTimer();
            }, 400);
          });
        } else {
          // Fail: Trigger shake animation and clear
          container.classList.add("shake");
          setTimeout(() => {
            container.classList.remove("shake");
            pinInput = "";
            updateDots();
          }, 500);
        }
      });
    }
  }
})();
