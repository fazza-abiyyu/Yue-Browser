# Changelog

All notable changes to this project will be documented in this file.

## [1.1.9] - 2026-08-29

### 🐛 Bug Fixes

- **Consent Popup False Positives**: Expanded semantic dialog detection (`role="dialog"`, `aria-modal`) and text-based consent matching (cookie, privacy, preferences, TCF, GPC, Google Funding Choices). Consent popups are now protected from adblock CSS and restored via `data-yue-popup` attribute.
- **Layout Breakage on CDN Sites**: Added Brave-style static asset bypass for `.js`, `.css`, fonts, and images on multi-purpose CDNs like `cdnsolutions.media`. Layout assets are no longer blocked at host level, only explicit ad paths.
- **In-Page Script Redirects (Judol)**: Silent `window.location` redirects to third-party ad/judol hosts are now blocked on the main frame without triggering blank pages.
- **Video Speed Stuck After Release**: Rewrote `cancelHold()` with deferred native setter restoration, prototype-chain fallback, and multi-video sweep to prevent playbackRate from staying stuck at 2x.
- **Ad Loader CORS Fallback**: Blocked responses now return `403 Forbidden` without `Access-Control-Allow-Origin` headers, causing ad config endpoints to fail preflight instead of falling back.

### 🔧 Adblock Engine Improvements

- **Minimal CSS Hiding**: Replaced aggressive `height:0 / overflow:hidden / z-index:-1` with `display:none / visibility:hidden / opacity:0` to prevent permanent layout corruption.
- **Expanded Consent Patterns**: Cookie banner and cosmetic filter engines now reject selectors containing generic consent words (`cookie`, `consent`, `gdpr`, `preference`, `privacy`, `policy`, `fundingchoices`, etc.) across class, id, and aria-label attributes.

## [1.1.8] - 2026-08-29

### 🐛 Bug Fixes

- **Reload Not Working on Blank Pages**: Pull-to-refresh failed to reload pages stuck in a blank loading state. `shouldOverrideUrlLoading` was intercepting the reload and re-posting `loadUrl` via `mainHandler.post`, introducing a delay/race condition. Now `reload()` sets `lastOverrideTime`/`lastOverrideUrl` so the navigation is not intercepted.
- **CSS Missing Due to cdnsolutions.media Blocked**: Domain `cdnsolutions.media` removed from `ad_domains.txt` and `knownAdHosts` to prevent CSS/JS requests from being blocked at the network level. Retained in JS-level detection to hide ad elements from that domain.
- **Ads Leaking (hevexiximyzywy.com)**: Domain `hevexiximyzywy.com` added to `ad_domains.txt`, `knownAdHosts`, and `adDomains` JS detection.

### 🔧 Yue-AdBlock Updates

- **Remote Adblock Rule Expansion**: Added `video_ads.txt` (152 lines), `popup_ads.txt` (223 lines), `anti_adblock_selectors.txt` (128 selectors), and `streaming.json` policy profile. Ad domain list expanded by +2485 domains.
- **Streaming Profile**: New policy profile optimized for video streaming sites with balanced ad blocking.

## [1.1.7] - 2026-08-28

### ✨ New Features

- **QR/Barcode Scanner**: Built-in scanner reader powered by CameraX + ML Kit Barcode Scanning. Accessible via the scanner icon on the New Tab home page search bar and the search overlay bar. Scanned URLs open directly; non-URL results (text, numbers) are sent through the default search engine. Features a dimmed overlay with a transparent scan window and corner brackets for precise framing.
- **Themed Scanner Icon**: Scanner icon follows the browser's accent color — pink for normal mode, red (`#FF002C`) for incognito mode.

### 🐛 Bug Fixes

- **Ghost Ads / Popunders**: Blocked cross-domain popup windows via `window.open()`, popunder redirects via `location.assign()`/`location.replace()`, clicks on ad-network `<a>` links, and auto-removed ad iframes and video ad players from the DOM.
- **Back Navigation Opening Ads**: Added `markRedirectUrlForSkip()` to track ad redirect URLs. The `goBack()` method now checks the history stack and skips ad-domain entries, navigating directly to the previous legitimate page.
- **Video Ads**: Expanded VAST/VPAID blocking patterns (25+ new signatures including `ad_break`, `adplayer`, `video_ads`, etc.), added detection for ad iframes and video ad players, and blocked 100+ additional video ad networks (Brightcove, Brid.tv, Teads, Connatix, Playwire, etc.).
- **Aggressive Cosmetic Filtering**: Switched from `el.remove()` to CSS-only hiding (`display:none`, `visibility:hidden`) to prevent JavaScript errors on ad-heavy sites. Added periodic DOM rescan (5s interval) to catch dynamically injected ads.

### 🔧 Adblock Engine Improvements

- **Expanded Ad Domain Blocklist**: Added 200+ ad domains across popup networks (shrinkearn, linkvertise, adsco.re), video networks (brightcove, teads, brid.tv, vidoomy), and ad networks (popads, propellerads, adsterra, exoclick, etc.).
- **Enhanced JS-Level Ad Blocking**: `blockAdIframes()` removes `src` attributes from iframes loading ad domains. `blockVideoAdPlayers()` hides elements with ad player classes (preroll, midroll, vast-player, instream).
- **Improved Popup Detection**: Better detection of cross-domain window openings and `<a>` link clicks targeting ad networks.

## [1.1.6] - 2026-08-27

### ✨ New Features

- **Remote Adblock Engine**: Modular adblock with remote policy sync via Yue-AdBlock Strategy Center (https://github.com/fazza-abiyyu/Yue-AdBlock). Four policy profiles (Balanced, Aggressive, Minimal, Anti-Judol), dynamic filter updates without app rebuild, hash-based change detection to minimize bandwidth, and 6-hour cooldown sync on app open.
- **Three-Layer Adblock Architecture**: Strategy (BE policy/rules) → Rules (filter data) → Engine (native Android implementation with HostBlocker, CosmeticService, RedirectService).
- **Host Blocking**: Parses `easylist.txt` and `abpindo.txt` domain patterns (`||domain.com^`, `|https://domain.com/`) for network-level ad blocking with whitelist support and fallback domains.
- **Redirect Bypass**: Detects ad-network redirect chains (e.g., shrinkearn.com → tpi.li) and extracts final target URLs directly, bypassing intermediate ad pages for download links.

### 🏗️ Refactored

- **AdBlockManager → AdBlockEngine**: Full modularization into services — `BlockingService`, `RedirectService`, `CosmeticService`, with clear separation of concerns for maintainability.
- **BE Pattern**: Yue-AdBlock backend follows cp-center/elysia pattern — Elysia + OData v4 + Zod validation, deployed on Vercel with Bun runtime.

### 🐛 Bug Fixes

- **Blank/White Screens**: Switched from aggressive DOM removal (`el.remove()`) to CSS-only hiding (`display:none`, `visibility:hidden`, `height:0`) to prevent site JavaScript errors.
- **Permission Popups Not Showing**: Fixed `onPermissionRequest()` and `onGeolocationPermissionsShowPrompt()` to not prematurely deny before system permission dialogs complete.
- **Clicks Not Working**: Removed `pointer-events: none` CSS injection and stopped `breakAdTimers()` from blocking legitimate click handlers.
- **Back Navigation Broken**: Removed HTTPS upgrade for IP addresses and added `suppressAutoSave` flag to prevent corrupted WebView state from being saved during error page loads.
- **State JSON Corruption**: Added `stateWriteLock` (write lock) to prevent concurrent JSON file write race conditions during tab state restoration.
- **Adsco.re Ads Not Blocked**: Added `parseHostPatternsFromList()` to parse easylist.txt domain patterns into HostBlocker.
- **Cleartext Traffic Blocked**: Set `cleartextTrafficPermitted=true` in network security config and AndroidManifest for all domains.

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
