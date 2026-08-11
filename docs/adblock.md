# Adblock & Element Picker Features

Yue Browser features a high-efficiency network ad blocker alongside an interactive DOM Element Picker that lets users hide unwanted content dynamically.

---

## 1. Network Request Blocker (EasyList Engine)

Analyzes resource URLs loaded on web pages and blocks trackers, banners, pop-ups, and advertisements.

**Key Sources:**

- [`AdBlockManager.kt`](../app/src/main/java/com/yue/browser/data/engine/AdBlockManager.kt) — Downloads, parses, and caches filter rules (EasyList & ABPIndo). Uses prefix trees or substring search engines to evaluate request domains.
- [`SystemWebViewClient.kt`](../app/src/main/java/com/yue/browser/data/engine/SystemWebViewClient.kt) — Intercepts requests inside `shouldInterceptRequest`:
  ```kotlin
  if (adBlockManager.shouldBlock(request.url, pageUrl)) {
      return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
  }
  ```
- [`BrowserViewModelExtensions.kt`](../app/src/main/java/com/yue/browser/presentation/BrowserViewModelExtensions.kt) — Provides domain whitelisting to skip ad-blocking on trusted websites.

## 2. Cosmetic Filter & Element Picker

A developer-style interface that lets users tap any element on a webpage, highlight it, and block it permanently from rendering.

**Key Sources:**

- [`SystemWebViewSessionPicker.kt`](../app/src/main/java/com/yue/browser/data/engine/SystemWebViewSessionPicker.kt) — Controls the active element picker session, injects scripts, enables highlighting overlays, and listens for coordinate events.
- [`WebViewScriptsVideo.kt`](../app/src/main/java/com/yue/browser/data/engine/WebViewScriptsVideo.kt) — Defines JavaScript injection payloads for element hovering, node identification (generating clean CSS selectors), and confirmation alerts.
- [`SystemWebChromeClient.kt`](../app/src/main/java/com/yue/browser/data/engine/SystemWebChromeClient.kt) — Intercepts element selections inside `onJsPrompt` and saves the generated selector to local preferences.
- [`SettingsRepositoryImpl.kt`](../app/src/main/java/com/yue/browser/data/repository/SettingsRepositoryImpl.kt) — Saves lists of custom CSS-blocked elements per domain:
  ```kotlin
  // Serialized as JSON strings inside SharedPreferences
  sharedPreferences.getString("blocked_css_selectors", "{}")
  ```
