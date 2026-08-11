# Privacy & Security Features

Yue Browser provides a secure and anonymous environment by implementing a suite of privacy controls and diagnostics directly on the device.

---

## 1. Biometric InPrivate Lock

Restricts access to private incognito tabs. The app displays an authentication shield when launching or returning to a private session.

**Key Sources:**

- [`MainActivity.kt`](../app/src/main/java/com/yue/browser/MainActivity.kt) — Triggers validation checks when resuming tab states.
- [`WebLockOverlay.kt`](../app/src/main/java/com/yue/browser/presentation/ui/WebLockOverlay.kt) — Compose views for biometric prompt sheets and fallback pattern codes.

## 2. Website Lock (WebLock)

Locks specific web domains behind a user-defined app PIN. When loading a locked site, navigation is suspended until the correct PIN is provided.

**Key Sources:**

- [`SiteSettingsDialog.kt`](../app/src/main/java/com/yue/browser/presentation/ui/components/SiteSettingsDialog.kt) — Holds the lock switch tile and launches the PIN setup dialog.
- [`BrowserViewModel.kt`](../app/src/main/java/com/yue/browser/presentation/BrowserViewModel.kt) — Saves locked domains, registers SHA-256 PIN hashes, and tracks failed unlock attempts.

## 3. Anti-Tracking Shield

Mitigates web fingerprinting, blocks third-party tracking cookies, and manages referrer fields.

**Key Sources:**

- [`SystemWebViewSessionExtensions.kt`](../app/src/main/java/com/yue/browser/data/engine/SystemWebViewSessionExtensions.kt) — Configures third-party cookie blocking on WebView profiles and injects fingerprinting/referrer scripts at page load start:
  ```kotlin
  cookieManager.setAcceptThirdPartyCookies(webViewInstance, !currentSettings.isBlockThirdPartyCookiesEnabled)
  ```
- [`WebViewScripts.kt`](../app/src/main/java/com/yue/browser/data/engine/WebViewScripts.kt) — Defines the JS fingerprinting override script:
  - Spoofs `navigator.hardwareConcurrency` (locked at 4).
  - Spoofs `navigator.deviceMemory` (locked at 8GB).
  - Mocks battery charging states via `navigator.getBattery`.
  - Injects a custom `navigator.userAgentData` object.
  - Fills empty hardware listings for enumerating device queries.

## 4. Advanced Security Diagnostics

Safe Browsing warn pages, detailed SSL certificate listings, and connection type checking.

**Key Sources:**

- [`IpDnsCheckerScreen.kt`](../app/src/main/java/com/yue/browser/presentation/ui/IpDnsCheckerScreen.kt) — Detects active VPN/Proxy transports via `ConnectivityManager`.
- [`SiteSettingsDialog.kt`](../app/src/main/java/com/yue/browser/presentation/ui/components/SiteSettingsDialog.kt) — Decodes `webView?.certificate` from the session view:
  - Subject (Issued To CName).
  - Issuer (Issued By CName).
  - Validity (NotBefore / NotAfter timestamps).
  - Manages Location, Camera, and Microphone overrides with a one-click reset button.
- [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml) — Declares Google Safe Browsing metadata for automated browser-side protection.
