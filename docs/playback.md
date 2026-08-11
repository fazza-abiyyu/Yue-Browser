# Media & Playback Controls

Yue Browser features custom media hooks that provide desktop-grade background playback and interactive video controls.

---

## 1. Background Playback

- **Behavior**: Audio and video continue playing in the background — even when locking the phone, turning off the screen, or using other apps.
- **Implementation**: Intercepts `navigator.mediaSession` calls and binds them to Android `MediaSession` and notification bar controllers. Supports play, pause, seek, and track-skipping actions globally.

**Key Sources:**

- [`MediaSessionManager.kt`](../app/src/main/java/com/yue/browser/data/engine/MediaSessionManager.kt) — Bridges WebView media hooks to Android `MediaSession`.
- [`SystemWebViewMediaSessionInterface.kt`](../app/src/main/java/com/yue/browser/data/engine/SystemWebViewMediaSessionInterface.kt) — JS interface used by injected scripts.
- [`PlaybackSettingsScreen.kt`](../app/src/main/java/com/yue/browser/presentation/ui/PlaybackSettingsScreen.kt) — Playback-related user preferences.

## 2. Video Controls & Speedup Gesture

- **Hold to Speedup**: Long-pressing inside a playing video instantly speeds up playback to **2x**. Releasing restores the normal playback rate immediately.
- **Fullscreen Auto-Rotation**: Forces the device layout to landscape when videos enter fullscreen, and reverts to portrait on exit.

**Key Sources:**

- [`WebViewScriptsVideo.kt`](../app/src/main/java/com/yue/browser/data/engine/WebViewScriptsVideo.kt) — Video control and speed-up JavaScript injections.
