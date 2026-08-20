# Changelog

All notable changes to this project will be documented in this file.

## [1.1.5] - 2026-08-20

### ✨ New Features

- **Set Default Browser Setting**: Added an option under General settings to set Yue Browser as the default system browser with dynamic status check using `RoleManager` (Android 10+) and system settings fallback. Fully localized in English and Indonesian.
- **Block External App Redirects Setting**: Added a Privacy & Security toggle to prevent redirects or link clicks from launching external apps (e.g. Facebook, Play Store) and force them to load inside the browser. Fully localized in English and Indonesian.

### 🐛 Bug Fixes

- **Background WebView Crash Recovery**: Handle WebView rendering process crashes programmatically in `onRenderProcessGone` by scheduling an automatic page reload, and manage WebView resources during Activity pause/resume.
- **Media Player False Notifications**: Added validation to prevent empty media player notifications from appearing before media playback has actually started.
- **Incognito Back History Restoration**: Fixed an issue where incognito tabs lost their navigation history (back/forward state) after being restored from the background.

## [1.1.4] - 2026-08-13

### 🐛 Bug Fixes

- **Password Import**: Add dynamic CSV column header mapping in `ExportImportHelper` to resolve failures when importing Firefox-style exported passwords

## [1.1.3] - 2026-08-13

### 🐛 Bug Fixes

- **APK Installer**: Add `REQUEST_INSTALL_PACKAGES` permission in Manifest to fix direct APK installation failures on Android 8.0+
- **Incognito Private Keyboard**: Enforce private keyboard mode (`IME_FLAG_NO_PERSONALIZED_LEARNING`) inside web input fields when using private/incognito tabs
- **Theme Selector**: Fix Drawer Menu theme toggle out-of-sync behavior when using system theme settings in dark mode

## [1.1.2] - 2026-08-11

### ✨ New Features

- **Site Permissions Screen**: Add per-site permission management screen (`SitePermissionsScreen.kt`) with Location, Camera, and Microphone overrides and one-click reset
- **IP & DNS Checker Screen**: Add network diagnostics screen (`IpDnsCheckerScreen.kt`) that detects active VPN/Proxy transports via `ConnectivityManager`
- **Security & Privacy Settings**: Add HTTPS-Only Mode, Do-Not-Track, third-party cookie blocking, fingerprint protection, referrer control, and Safe Browsing toggles, plus per-domain permission persistence

### 📝 Documentation

- Rewrite all `docs/` guides with relative source-file paths (GitHub-safe) and reusable code references
- Clean up `README.md`: add `public/preview` screenshots, tidy feature list, and link to feature docs

---

## [1.1.1] - 2026-08-04

### 🐛 Bug Fixes

- **Fix tab state corruption**: Prevent unwanted empty popup tab creation during state restore and tab switching by adding `suppressPopupCreation` flag and serializing `writeTabsState` with mutex
- **Fix hold speed bug**: Save original video rate for all videos (including paused ones) to prevent race condition where video stays stuck in 2x speed after releasing hold gesture
- **Fix scroll false-trigger refresh**: Add dynamic scroll position detection to prevent pull-to-refresh from accidentally triggering on manga/comic sites when scrolling down
- **Fix group drag-drop**: Fix group detail drag-drop zone not detecting drops due to stale state capture in `pointerInput`
- **Fix PiP video pausing**: Prevent video from pausing and force single-pane view when entering Picture-in-Picture mode
- **Fix PiP UI overlays**: Hide all UI overlays (tab switcher, menu sheet) when entering PiP mode
- **Fix PiP lifecycle**: Override 1-parameter callback and sync PiP state correctly in `onResume`
- **Fix adblock video visibility**: Restore video visibility after ad is skipped by moving `hideAdUI` outside conditional block
- **Fix tabs_state.json writes**: Ensure atomic writes using temporary file rename strategy

### ✨ New Features

- **Picture-in-Picture (PiP)**: Implement automatic PiP lifecycle in MainActivity with DOM isolation and settings toggle
- **URL Probing & Smart Filename**: Implement URL probing and intelligent filename extraction for download initialization
- **Adblock Sponsored/Promoted Selectors**: Add sponsored and promoted ad selectors for better ad detection
- **Top Bar Component**: Add top bar component and refine bottom bar split tab slot management

### ⚡ Performance Improvements

- **Adblock Optimization**: Optimize YouTube ad blocker with CSS `:has()` selectors and event-driven skipping
- **Adblock Filter Loading**: Refactor adblock filter loading logic for better performance
- **Undo-Close-Tab Timer**: Optimize undo-close-tab timer with ViewModel jobs for better lifecycle management

### 🔧 Refactoring

- **Adblock Architecture**: Rewrite adblock filter loading logic and remove auto sync on app launch
- **Error Handling**: Improve WebView error handling and search overlay behavior
- **Tablet Detection**: Improve tablet detection and refine UI layout responsiveness
- **Element Picker**: Optimize element picker hit-testing and pointer-events handling

### 📝 Landing Page Updates

- Updated "Encrypted Backup" → "Encrypted Password Vault" with clear description that only password data is encrypted (AES-256)
- All download buttons now point to GitHub releases page
- Removed "judol" / gambling references from marquee, adblock description, and statistics
- Version display updated to 1.1.1

### 🗑️ Removed

- Commented out Auto PIP settings option in SettingsScreen
- Removed auto adblock filter synchronization on app launch

---

## [1.1.0] - Previous Release

_Refer to git history for changes in previous releases._
