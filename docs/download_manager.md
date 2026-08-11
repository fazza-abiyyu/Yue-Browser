# Download Manager

Yue Browser features a high-performance multi-threaded downloader designed to bypass single-connection speed throttles.

---

## 1. Multi-Threaded Parallel Downloads

- **Behavior**: Splits download payloads into parallel byte-range requests.
- **Thread Configuration**: Configurable count (1 to 16 threads, default 4).
- **Range Probe Fallback**: Probes server support for HTTP range requests. Automatically falls back to standard single-connection downloads if range is not supported, size is unknown, or the server lacks `Accept-Ranges`.

**Key Sources:**

- [`DownloadRepositoryImpl.kt`](../app/src/main/java/com/yue/browser/data/repository/DownloadRepositoryImpl.kt) — Manages download state, chunk scheduling, and persistence.
- [`DownloadsScreen.kt`](../app/src/main/java/com/yue/browser/presentation/ui/downloads/DownloadsScreen.kt) — UI for active, paused, and completed downloads.

## 2. File & Directory Management

- **SAF Integration**: Choose custom download folders globally or prompt per download.
- **Pause & Resume**: Pause active downloads and resume them cleanly later.
- **Physical File Deletion**: Clean up downloaded files from local storage when clearing the item list.

**Key Sources:**

- [`DownloadSettingsDialog.kt`](../app/src/main/java/com/yue/browser/presentation/ui/downloads/DownloadSettingsDialog.kt) — Thread count and folder preferences.
- [`DownloadNotifier.kt`](../app/src/main/java/com/yue/browser/data/notification/DownloadNotifier.kt) — Foreground service notifications and progress reporting.
