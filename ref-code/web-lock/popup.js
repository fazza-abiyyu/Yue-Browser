document.addEventListener("DOMContentLoaded", () => {
  const setupView = document.getElementById("setup-view");
  const mainView = document.getElementById("main-view");
  
  // Tab Elements
  const tabBtns = document.querySelectorAll(".tab-btn");
  const tabContents = document.querySelectorAll(".tab-content");

  // Current Site Elements
  const currentHostnameEl = document.getElementById("current-hostname");
  const statusIcon = document.getElementById("status-icon");
  const lockIconSvg = document.getElementById("lock-icon-svg");
  const statusText = document.getElementById("status-text");
  const toggleLockBtn = document.getElementById("toggle-lock-btn");

  // Setup PIN Elements
  const setupPinInput = document.getElementById("setup-pin");
  const setupPinConfirmInput = document.getElementById("setup-pin-confirm");
  const saveInitialPinBtn = document.getElementById("save-initial-pin-btn");
  const setupError = document.getElementById("setup-error");

  // Manage Sites Elements
  const newSiteInput = document.getElementById("new-site-input");
  const addSiteBtn = document.getElementById("add-site-btn");
  const lockedSitesList = document.getElementById("locked-sites-list");

  // Settings PIN Elements
  const oldPinInput = document.getElementById("old-pin");
  const newPinInput = document.getElementById("new-pin");
  const confirmNewPinInput = document.getElementById("confirm-new-pin");
  const changePinBtn = document.getElementById("change-pin-btn");
  const settingsMessage = document.getElementById("settings-message");

  let currentHostname = "";
  let isCurrentSiteLocked = false;

  // Lock Icon SVGs
  const SVG_LOCKED = `<path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/>`;
  const SVG_UNLOCKED = `<path d="M12 17c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm6-9h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6h1.9c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm0 12H6V10h12v10z"/>`;

  // Check if PIN is already set
  chrome.storage.local.get(["pin", "lockedSites"], (data) => {
    if (!data.pin) {
      setupView.classList.remove("hidden");
      mainView.classList.add("hidden");
    } else {
      setupView.classList.add("hidden");
      mainView.classList.remove("hidden");
      initDashboard(data.lockedSites || []);
    }
  });

  // --- Initial PIN Setup ---
  saveInitialPinBtn.addEventListener("click", () => {
    const pin = setupPinInput.value;
    const confirmPin = setupPinConfirmInput.value;

    if (pin.length !== 6 || confirmPin.length !== 6) {
      showError(setupError, "PIN harus berisi 6 angka.");
      return;
    }
    if (!/^\d{6}$/.test(pin) || !/^\d{6}$/.test(confirmPin)) {
      showError(setupError, "PIN hanya boleh berupa angka.");
      return;
    }
    if (pin !== confirmPin) {
      showError(setupError, "Konfirmasi PIN tidak cocok.");
      return;
    }

    chrome.storage.local.set({ pin, lockedSites: [] }, () => {
      setupView.classList.add("hidden");
      mainView.classList.remove("hidden");
      initDashboard([]);
    });
  });

  // --- Dashboard Initialization ---
  function initDashboard(lockedSites) {
    // 1. Tab Switching
    tabBtns.forEach(btn => {
      btn.addEventListener("click", () => {
        tabBtns.forEach(b => b.classList.remove("active"));
        tabContents.forEach(c => c.classList.remove("active"));

        btn.classList.add("active");
        const tabId = btn.getAttribute("data-tab");
        document.getElementById(tabId).classList.add("active");
      });
    });

    // 2. Fetch Active Tab URL Information
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      if (tabs[0] && tabs[0].url) {
        try {
          const url = new URL(tabs[0].url);
          currentHostname = url.hostname;

          if (["http:", "https:"].includes(url.protocol)) {
            currentHostnameEl.textContent = currentHostname;
            updateLockStatus(lockedSites);
          } else {
            // Internal pages like chrome://, etc.
            currentHostnameEl.textContent = "Halaman Sistem Chrome";
            toggleLockBtn.disabled = true;
            toggleLockBtn.style.opacity = "0.5";
            toggleLockBtn.textContent = "Tidak Bisa Dikunci";
          }
        } catch (e) {
          currentHostnameEl.textContent = "URL tidak didukung";
          toggleLockBtn.disabled = true;
        }
      }
    });

    // 3. Render list of locked sites
    renderLockedSites(lockedSites);

    // 4. Toggle Current Site Lock Click Handlers
    toggleLockBtn.addEventListener("click", () => {
      if (!currentHostname) return;

      chrome.storage.local.get("lockedSites", (data) => {
        let sites = data.lockedSites || [];
        if (isCurrentSiteLocked) {
          // Unlock it -> REQUIRES PIN
          requestPinConfirmation(() => {
            sites = sites.filter(s => s !== currentHostname);
            saveToggleLock(sites);
          });
        } else {
          // Lock it -> NO PIN REQUIRED
          if (!sites.includes(currentHostname)) {
            sites.push(currentHostname);
          }
          saveToggleLock(sites);
        }
      });
    });

    function saveToggleLock(sites) {
      chrome.storage.local.set({ lockedSites: sites }, () => {
        updateLockStatus(sites);
        renderLockedSites(sites);
        // Reload active tab to immediately apply locker overlay if locked,
        // or unlock if removed.
        chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
          if (tabs[0]) {
            chrome.tabs.reload(tabs[0].id);
          }
        });
      });
    }

    // 5. Add Custom Site Event
    addSiteBtn.addEventListener("click", () => {
      const inputVal = newSiteInput.value.trim().toLowerCase();
      if (!inputVal) return;

      // Basic host verification
      let cleanHost = inputVal;
      try {
        if (inputVal.includes("://")) {
          const urlObj = new URL(inputVal);
          cleanHost = urlObj.hostname;
        } else {
          // Make it a full URL temporarily to parse correctly
          const urlObj = new URL("http://" + inputVal);
          cleanHost = urlObj.hostname;
        }
      } catch (e) {
        // Fallback to whatever input they wrote if parsing fails
      }

      if (!cleanHost || cleanHost.split(".").length < 2) {
        alert("Masukkan nama domain yang valid.");
        return;
      }

      chrome.storage.local.get("lockedSites", (data) => {
        const sites = data.lockedSites || [];
        if (!sites.includes(cleanHost)) {
          sites.push(cleanHost);
          chrome.storage.local.set({ lockedSites: sites }, () => {
            renderLockedSites(sites);
            newSiteInput.value = "";
            updateLockStatus(sites);
          });
        }
      });
    });

    // Enter Key to Add Site
    newSiteInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        addSiteBtn.click();
      }
    });

    // 6. Update PIN Settings Event
    changePinBtn.addEventListener("click", () => {
      const oldPin = oldPinInput.value;
      const newPin = newPinInput.value;
      const confirmNewPin = confirmNewPinInput.value;

      chrome.storage.local.get("pin", (data) => {
        if (oldPin !== data.pin) {
          showSettingsMsg("PIN Lama tidak benar.", true);
          return;
        }
        if (newPin.length !== 6 || !/^\d{6}$/.test(newPin)) {
          showSettingsMsg("PIN Baru harus berisi 6 angka.", true);
          return;
        }
        if (newPin !== confirmNewPin) {
          showSettingsMsg("Konfirmasi PIN tidak cocok.", true);
          return;
        }

        chrome.storage.local.set({ pin: newPin }, () => {
          showSettingsMsg("PIN berhasil diperbarui!", false);
          oldPinInput.value = "";
          newPinInput.value = "";
          confirmNewPinInput.value = "";
        });
      });
    });

    // 7. Auto-lock Timeout Event
    const timeoutSelect = document.getElementById("auto-lock-timeout");
    chrome.storage.local.get("autoLockTimeout", (data) => {
      // Default to 5 minutes if not set
      const currentTimeout = data.autoLockTimeout !== undefined ? data.autoLockTimeout : "5";
      timeoutSelect.value = currentTimeout;
    });

    timeoutSelect.addEventListener("change", () => {
      const selectedValue = timeoutSelect.value;
      chrome.storage.local.set({ autoLockTimeout: selectedValue }, () => {
        showSettingsMsg("Waktu auto lock diperbarui!", false);
      });
    });
  }

  // --- Helper Functions ---
  function updateLockStatus(lockedSites) {
    if (!currentHostname) return;

    isCurrentSiteLocked = lockedSites.some(site => 
      currentHostname === site || currentHostname.endsWith("." + site)
    );

    if (isCurrentSiteLocked) {
      statusIcon.className = "status-icon locked";
      lockIconSvg.innerHTML = SVG_LOCKED;
      statusText.textContent = "Terkunci";
      statusText.style.color = "var(--error-color)";
      toggleLockBtn.textContent = "Buka Kunci Website Ini";
    } else {
      statusIcon.className = "status-icon unlocked";
      lockIconSvg.innerHTML = SVG_UNLOCKED;
      statusText.textContent = "Terbuka";
      statusText.style.color = "var(--success-color)";
      toggleLockBtn.textContent = "Kunci Website Ini";
    }
  }

  function renderLockedSites(lockedSites) {
    lockedSitesList.innerHTML = "";

    if (lockedSites.length === 0) {
      lockedSitesList.innerHTML = `<li class="site-item" style="color: var(--text-muted); justify-content: center; font-style: italic;">Tidak ada website terkunci</li>`;
      return;
    }

    lockedSites.forEach(site => {
      const li = document.createElement("li");
      li.className = "site-item";

      const span = document.createElement("span");
      span.textContent = site;

      const deleteBtn = document.createElement("button");
      deleteBtn.className = "delete-btn";
      deleteBtn.innerHTML = `
        <svg viewBox="0 0 24 24">
          <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
        </svg>
      `;
      deleteBtn.addEventListener("click", () => {
        removeSite(site);
      });

      li.appendChild(span);
      li.appendChild(deleteBtn);
      lockedSitesList.appendChild(li);
    });
  }

  function removeSite(siteToRemove) {
    requestPinConfirmation(() => {
      chrome.storage.local.get("lockedSites", (data) => {
        const sites = data.lockedSites || [];
        const updatedSites = sites.filter(s => s !== siteToRemove);

        chrome.storage.local.set({ lockedSites: updatedSites }, () => {
          renderLockedSites(updatedSites);
          updateLockStatus(updatedSites);
          // Refresh active tab if we just unlocked its domain
          chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
            if (tabs[0] && tabs[0].url) {
              try {
                const url = new URL(tabs[0].url);
                if (url.hostname === siteToRemove || url.hostname.endsWith("." + siteToRemove)) {
                  chrome.tabs.reload(tabs[0].id);
                }
              } catch (e) {}
            }
          });
        });
      });
    });
  }

  // --- PIN Verification Modal Logic ---
  const verifyModal = document.getElementById("verify-modal");
  const confirmActionPinInput = document.getElementById("confirm-action-pin");
  const modalCancelBtn = document.getElementById("modal-cancel-btn");
  const modalConfirmBtn = document.getElementById("modal-confirm-btn");
  const modalError = document.getElementById("modal-error");

  let currentOnSuccessCallback = null;

  function requestPinConfirmation(onSuccess) {
    currentOnSuccessCallback = onSuccess;
    confirmActionPinInput.value = "";
    modalError.classList.add("hidden");
    verifyModal.classList.remove("hidden");
    confirmActionPinInput.focus();
  }

  modalCancelBtn.addEventListener("click", () => {
    verifyModal.classList.add("hidden");
    currentOnSuccessCallback = null;
  });

  function processModalConfirmation() {
    const enteredPin = confirmActionPinInput.value;
    chrome.storage.local.get("pin", (data) => {
      if (enteredPin === data.pin) {
        verifyModal.classList.add("hidden");
        if (currentOnSuccessCallback) {
          currentOnSuccessCallback();
          currentOnSuccessCallback = null;
        }
      } else {
        modalError.textContent = "PIN salah.";
        modalError.classList.remove("hidden");
        confirmActionPinInput.value = "";
      }
    });
  }

  modalConfirmBtn.addEventListener("click", processModalConfirmation);
  confirmActionPinInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      processModalConfirmation();
    }
  });

  function showError(el, msg) {
    el.textContent = msg;
    el.classList.remove("hidden");
    setTimeout(() => {
      el.classList.add("hidden");
    }, 4000);
  }

  function showSettingsMsg(msg, isError) {
    settingsMessage.textContent = msg;
    settingsMessage.className = isError ? "message-text error" : "message-text";
    settingsMessage.classList.remove("hidden");
    setTimeout(() => {
      settingsMessage.classList.add("hidden");
    }, 4000);
  }
});
