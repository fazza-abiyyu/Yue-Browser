# Yue Browser: Atmospheric Clarity

Yue Browser is a premium, feature-rich, high-performance Android web browser built on Jetpack Compose and the Android System WebView engine. It prioritizes user privacy, control, and visual elegance, delivering a desktop-class browsing experience to mobile.

---

## ✨ Key Features

### 🛡️ Privacy & Security (First-Class)
- **Biometric InPrivate (Incognito) Lock:** View your private tabs securely. The private session is guarded by Android Biometrics (Fingerprint/Face) or Device PIN/Pattern, automatically auto-locking immediately when switching to normal tabs or viewing the public switcher.
- **Website Lock (WebLock):** Lock specific website domains behind a custom application PIN/Biometric lock. Features customizable auto-lock timeouts for idle periods.
- **Secure Password Manager:** Save, edit, search, and view credentials directly on-device. Supports auto-fill popups on credential fields and password list exports/imports via CSV files.

### 🗂️ Advanced Tab Management & Grouping
- **Visual Grid Switcher:** Fast, fluid, and intuitive double-column grid layout for active tabs.
- **Tab Grouping:** Organise tabs into custom folders with personalized names and active color themes.
- **Dynamic Session Preservation:** Full restoration of tab states, navigation histories, and groups across application restarts.

### 🚫 Built-In Ad Blocking & Cosmetic Filtering
- **Domain Blocker:** Add custom blocklists for domains.
- **Interactive Element Picker:** Tap and dynamically select elements on any webpage to hide or filter them (Cosmetic filtering), keeping site layouts clean and clutter-free.

### 📥 Power Tools & Playback
- **Advanced Downloader:** Multi-threaded parallel downloading, SAF folder destination settings, progress checking, and custom delete methods.
- **Offline Pages:** Capture and save complete web page documents locally for offline viewing.
- **Built-in Page Translation:** Instantly translate websites between multiple languages with smart auto-detect capability.
- **Find in Page:** Search, navigate, and highlight matches on the active page.
- **Background Media Playback:** Support for background audio/video playback on both normal and private tabs.

---

## 🛠️ Technology Stack

- **Framework:** Kotlin + Jetpack Compose
- **Design System:** Material Design 3 (M3) with custom dark mode and typography
- **Rendering Engine:** Android System WebView (`android.webkit.WebView`)
- **Data Persistence:** Cloud Firestore, SQLite, or local Shared Preferences depending on build variants

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
