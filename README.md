# Yue Browser: Atmospheric Clarity

Yue Browser is a premium, feature-rich, high-performance Android web browser built on Jetpack Compose and the Android System WebView engine. It prioritizes user privacy, control, and visual elegance, delivering a desktop-class browsing experience to mobile.

---

## 🚀 Standout & Premium Features

Yue Browser includes several advanced, standout features designed to give you ultimate control over your browsing:

*   **🕶️ Universal Dark Mode:** Force-renders a dark theme on any webpage dynamically, even if the website doesn't natively support it, reducing eye strain in low-light environments.
    *   *Implementation:* Managed via Android's WebView `settings.forceDark` configured dynamically in [SystemWebViewSession.kt](app/src/main/java/com/yue/browser/data/engine/SystemWebViewSession.kt#L311) and [SystemWebViewSessionExtensions.kt](app/src/main/java/com/yue/browser/data/engine/SystemWebViewSessionExtensions.kt#L206).
*   **🎵 Playback Engine:** Play audio/video in the background seamlessly. Media playback continues playing even when you switch tabs, go to the home screen, or lock the device, for both normal and private tabs.
    *   *Implementation:* Injected JavaScript hooks mock `navigator.mediaSession` in [WebViewScripts.kt](app/src/main/java/com/yue/browser/data/engine/WebViewScripts.kt#L323) which binds to Android's `MediaSessionManager` via [SystemWebViewMediaSessionInterface.kt](app/src/main/java/com/yue/browser/data/engine/SystemWebViewMediaSessionInterface.kt#L6) for background notification media controls.
*   **🚫 Anti Open-in-App:** Prevents websites from automatically redirecting and hijacking your navigation to open external native apps (like YouTube, Shopee, or app stores). Keeps you completely in control inside the browser.
    *   *Implementation:* Intercepts and blocks automatic, non-user-initiated external app launches and third-party domain redirects inside `shouldOverrideUrlLoading` in [SystemWebViewClient.kt](app/src/main/java/com/yue/browser/data/engine/SystemWebViewClient.kt#L373).
*   **⚡ Hold to Speedup:** Press and hold on any playing video to accelerate the playback speed to **2x** instantly. Releasing the long press returns the video to normal speed—ideal for scanning content quickly.
    *   *Implementation:* JavaScript touch event listeners injected in [WebViewScripts.kt](app/src/main/java/com/yue/browser/data/engine/WebViewScripts.kt#L682) intercept long presses on videos to accelerate `playbackRate` to 2.0 with a visual HUD speedup overlay.
*   **🔄 Lock Orientation Fullscreen:** Automatically forces the device screen orientation to landscape when you enter video fullscreen mode, ensuring a seamless viewing experience without needing to toggle system-wide screen rotation.
    *   *Implementation:* Controlled dynamically in [SystemWebChromeClient.kt](app/src/main/java/com/yue/browser/data/engine/SystemWebChromeClient.kt#L241) to automatically force landscape orientation during fullscreen playback and revert to portrait on exit.
*   **🎯 Interactive Element Picker:** Tap and dynamically select any layout element on a webpage to hide or filter it (adblocking/cosmetic filtering), keeping site pages clean and clutter-free.
    *   *Implementation:* Orchestrated via helper extensions in [SystemWebViewSessionPicker.kt](app/src/main/java/com/yue/browser/data/engine/SystemWebViewSessionPicker.kt), custom client-side overlay styling scripts in [WebViewScriptsVideo.kt](app/src/main/java/com/yue/browser/data/engine/WebViewScriptsVideo.kt#L4), and `prompt()` event interceptors in [SystemWebChromeClient.kt](app/src/main/java/com/yue/browser/data/engine/SystemWebChromeClient.kt#L117).
*   **🛡️ Built-in Adblocker & Anti-Judol Blocker:** Blocks ads, trackers, and gambling/betting (Judol) sites using dynamically synchronized EasyList and ABPIndo rules. Includes request-level blocking, cosmetic script injections, and custom YouTube ad-blocking capabilities.
    *   *Implementation:* Powered by [AdBlockManager.kt](app/src/main/java/com/yue/browser/data/engine/AdBlockManager.kt) to handle rule synchronization, domain checks, and script generation, and integrated into network routing inside [SystemWebViewClient.kt](app/src/main/java/com/yue/browser/data/engine/SystemWebViewClient.kt#L405).
*   **🧵 Multi-Thread Downloader:** Splits a file into parallel byte-range chunks and pulls them over multiple simultaneous HTTP connections for dramatically faster transfers on range-supporting servers. The default thread count is fully configurable (1–16, default 4) and the system gracefully falls back to a single-connection download when the server does not advertise `Accept-Ranges` support or the file size is unknown.
    *   *Implementation:* Toggle and per-default thread count slider are exposed in [DownloadSettingsDialog.kt](app/src/main/java/com/yue/browser/presentation/ui/downloads/DownloadSettingsDialog.kt#L69). The chunking algorithm, range-probe fallback, and parallel fetcher are handled in [DownloadRepositoryImpl.kt](app/src/main/java/com/yue/browser/data/repository/DownloadRepositoryImpl.kt#L305) — `createChunks` at line 649 splits the file and the parallel `performMultiPartDownload` worker at line 826 reconciles the chunks back into a single file.
*   **🔐 Encrypted Settings Backup & Restore:** Export your full browser configuration — settings, adblock filters, WebLock PIN hash, speed dials, bookmarks, and password vault — into a portable JSON file and restore it on any device in one tap. When a master password is supplied at export time, the password payload is sealed with **AES-256-GCM** using a key derived via **PBKDF2-HMAC-SHA256** over 100,000 iterations, so the backup remains safe even if the file is leaked. The import flow verifies the master password *before* mutating any state and refuses the entire restore on a wrong password (returning `WRONG_PASSWORD`); settings and bookmarks are merged additively while passwords are decrypted and re-inserted.
    *   *Implementation:* Crypto pipeline lives in [ExportImportHelper.kt](app/src/main/java/com/yue/browser/presentation/ExportImportHelper.kt#L29) — `exportToJson` at line 29, `importFromJson` at line 138 (with the master-password pre-check at line 160), `encryptData`/`decryptData` at lines 324/335, and PBKDF2 `deriveKey` at line 346. The master-password dialogs, SAF file picker, and "wrong password" handling are wired up in [SettingsScreen.kt](app/src/main/java/com/yue/browser/presentation/ui/SettingsScreen.kt#L126) and the ViewModel entry points in [BrowserViewModel.kt](app/src/main/java/com/yue/browser/presentation/BrowserViewModel.kt#L529).

---

## ✨ Key Features

### 🛡️ Privacy & Security (First-Class)
- **Biometric InPrivate (Incognito) Lock:** View your private tabs securely. The private session is guarded by Android Biometrics (Fingerprint/Face) or Device PIN/Pattern, automatically auto-locking immediately when switching to normal tabs or viewing the public switcher.
- **Website Lock (WebLock):** Lock specific website domains behind a custom application PIN/Biometric lock. Features customizable auto-lock timeouts for idle periods.
- **Secure Password Manager:** Save, edit, search, and view credentials directly on-device. Supports auto-fill popups on credential fields, password list exports/imports via CSV files, and inclusion in encrypted AES-GCM backup bundles (see *Encrypted Settings Backup & Restore*).

### 🗂️ Advanced Tab Management & Grouping
- **Visual Grid Switcher:** Fast, fluid, and intuitive double-column grid layout for active tabs.
- **Tab Grouping:** Organise tabs into custom folders with personalized names and active color themes.
- **Dynamic Session Preservation:** Full restoration of tab states, navigation histories, and groups across application restarts.

### 🚫 Built-In Ad Blocking & Cosmetic Filtering
- **Domain Blocker:** Add custom blocklists for domains.

### 📥 Power Tools & Playback
- **Advanced Downloader:** Multi-threaded parallel downloading with configurable connection count (1–16), SAF folder destination settings, live progress tracking, pause/resume, and configurable physical file deletion.
- **Offline Pages:** Capture and save complete web page documents locally for offline viewing.
- **Built-in Page Translation:** Instantly translate websites between multiple languages with smart auto-detect capability.
- **Find in Page:** Search, navigate, and highlight matches on the active page.

---

## 🛠️ Technology Stack

- **Framework:** Kotlin + Jetpack Compose
- **Design System:** Material Design 3 (M3) with custom dark mode and typography
- **Rendering Engine:** Android System WebView (`android.webkit.WebView`)
- **Data Persistence:** Local Shared Preferences (JSON-serialized)

---

## 📁 Project Architecture & Package Structure

The codebase is highly modularized, keeping Kotlin source files under a strict 500-600 line limit to maintain code clarity and testability.

```
com/yue/browser/
│
├── data/
│   └── engine/
│       ├── SystemWebViewSession.kt       - Web engine state, cosmetics, and settings
│       ├── SystemWebViewRenderer.kt      - Touch overlays and rendering composition
│       ├── SystemWebViewClient.kt        - Network routing and cosmetic style injections
│       └── SystemWebViewSessionPicker.kt - DOM Element picker logic
│
├── domain/
│   └── model/
│       └── HistoryItem.kt                - Domain models
│
└── presentation/
    ├── BrowserViewModel.kt              - Core state management & repository binding
    │
    └── ui/
        ├── tabswitcher/
        │   ├── TabSwitcherScreen.kt      - Dual-mode tab switcher panel
        │   ├── TabSwitcherComponents.kt  - Incognito icons, slots, and visual tab cards
        │   ├── TabGroupDialogs.kt        - Color pickers and group creation overlays
        │   └── GroupDetailOverlay.kt     - Overlay for tab folder details
        │
        ├── downloads/
        │   ├── DownloadsScreen.kt        - Download center
        │   ├── DownloadItemComponents.kt - Status labels, extensions, and file rows
        │   └── DownloadSettingsDialog.kt - Thread counts & directory settings
        │
        ├── components/
        │   ├── BrowserBottomBar.kt       - Fluid bottom toolbar and address container
        │   ├── MenuDrawerSheet.kt        - Settings/InPrivate quick drawer
        │   ├── NewTabHomeScreen.kt       - Brand homepage layout
        │   └── IncognitoLockScreen.kt    - Authentication screen for private views
        │
        ├── HistoryScreen.kt             - Locale-aware historical records
        ├── BookmarksScreen.kt           - Bookmark navigation entries
        └── PasswordManagerScreen.kt     - Credentials list
```

---

## 🚀 How to Get Started

### Prerequisites
- **Android Studio Koala+** (or newer)
- **Android SDK Platform 34+**
- An Android device or emulator running **API Level 26 (Android 8.0) or higher**

### Setup
1. Clone this repository:
   ```bash
   git clone <repo-url>
   ```
2. Open the project folder in Android Studio.
3. Allow Gradle to sync and download dependencies.

### Running & Deploying
To assemble a debug APK and run it:
```bash
./gradlew installDebug
```

To build a release bundle:
```bash
./gradlew assembleRelease
```
The compiled APK will be output at `app/build/outputs/apk/release/app-release.apk`.
