package com.yue.browser.data.engine

object WebViewErrorPage {
    fun getCustomErrorHtml(failedUrl: String?, isDarkActive: Boolean, isPrivate: Boolean): String {
        val displayUrl = if (failedUrl.isNullOrBlank()) "situs ini" else failedUrl.take(80)
        
        val bgColor = if (isDarkActive) "#09090B" else "#F9FAFB"
        val textColor = if (isDarkActive) "#FAFAFA" else "#111827"
        val subTextColor = if (isDarkActive) "#A1A1AA" else "#4B5563"
        val cardBg = if (isDarkActive) "rgba(24, 24, 27, 0.6)" else "rgba(255, 255, 255, 0.8)"
        val cardBorder = if (isDarkActive) "rgba(255,255,255,0.05)" else "rgba(0,0,0,0.05)"
        val btnSecondaryBg = if (isDarkActive) "rgba(255,255,255,0.05)" else "rgba(0,0,0,0.04)"
        val btnSecondaryBorder = if (isDarkActive) "rgba(255,255,255,0.08)" else "rgba(0,0,0,0.06)"
        
        val moonColor = if (isDarkActive) "#27272A" else "#E5E7EB"
        val moonHighlight = if (isDarkActive) "#3F3F46" else "#F3F4F6"
        val craterColor = if (isDarkActive) "#18181B" else "#D1D5DB"
        
        val accentColor = if (isPrivate) "#FF002C" else "#EC4899"
        val accentHover = if (isPrivate) "#FF3355" else "#FF6FB5"
        val glowColor = if (isDarkActive) {
            if (isPrivate) "rgba(255,0,44,0.18)" else "rgba(236,72,153,0.18)"
        } else {
            if (isPrivate) "rgba(255,0,44,0.06)" else "rgba(236,72,153,0.06)"
        }

        return """
<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>Yue Browser — Tidak dapat terhubung</title>
<style>
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html, body {
  margin: 0; padding: 0;
  background: $bgColor;
  color: $textColor;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-user-select: none;
  user-select: none;
  -webkit-font-smoothing: antialiased;
}
body {
  background:
    radial-gradient(circle at 50% 25%, $glowColor, transparent 60%),
    $bgColor;
}
.wrap {
  padding: 24px;
  max-width: 380px;
  width: 100%;
  text-align: center;
}
.moon-wrap {
  width: 120px;
  height: 120px;
  margin: 0 auto 24px;
  position: relative;
  animation: floatY 4s ease-in-out infinite;
}
@keyframes floatY {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}
.moon-svg {
  width: 120px;
  height: 120px;
  position: relative;
  z-index: 1;
}
.title {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 8px;
  color: $textColor;
  letter-spacing: -0.3px;
}
.sub {
  font-size: 13.5px;
  line-height: 1.5;
  color: $subTextColor;
  margin: 0 auto 20px;
}
.url-box {
  background: $cardBg;
  border: 1px solid $cardBorder;
  border-radius: 8px;
  padding: 10px 12px;
  margin: 0 0 24px;
  font-size: 11.5px;
  color: $subTextColor;
  text-align: center;
  word-break: break-all;
  font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
}
.btns {
  display: flex;
  gap: 8px;
  justify-content: center;
}
.btn {
  flex: 1;
  display: inline-block;
  padding: 11px 16px;
  border-radius: 8px;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  -webkit-appearance: none;
  appearance: none;
  text-decoration: none;
  transition: transform 0.1s ease, opacity 0.1s ease;
  border: none;
  text-align: center;
}
.btn:active { transform: scale(0.97); }
.btn-primary {
  background: $accentColor;
  color: #FFFFFF;
}
.btn-primary:hover {
  background: $accentHover;
}
.btn-secondary {
  background: $btnSecondaryBg;
  color: $textColor;
  border: 1px solid $btnSecondaryBorder;
}
.brand {
  margin-top: 40px;
  font-size: 10px;
  color: $subTextColor;
  letter-spacing: 2px;
  text-transform: uppercase;
  opacity: 0.5;
  font-weight: 600;
}
</style>
</head><body>
  <div class="wrap">
    <div class="moon-wrap">
      <svg class="moon-svg" viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="moonGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="$moonHighlight"/>
            <stop offset="100%" stop-color="$moonColor"/>
          </linearGradient>
        </defs>
        <!-- The Moon Body -->
        <circle cx="100" cy="100" r="70" fill="url(#moonGrad)"/>
        <!-- Craters -->
        <circle cx="70" cy="75" r="9" fill="$craterColor" opacity="0.45"/>
        <circle cx="130" cy="85" r="7" fill="$craterColor" opacity="0.45"/>
        <circle cx="80" cy="125" r="11" fill="$craterColor" opacity="0.45"/>
        <circle cx="125" cy="130" r="6" fill="$craterColor" opacity="0.4"/>
        <circle cx="100" cy="145" r="4" fill="$craterColor" opacity="0.35"/>
        <!-- Clean Minimalist Crack Cutouts (drawn in bgColor) -->
        <path d="M100 25 L102 55 L96 85 L104 115 L98 145 L100 175" 
              stroke="$bgColor" stroke-width="8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M96 85 L82 73" 
              stroke="$bgColor" stroke-width="8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M104 115 L118 127" 
              stroke="$bgColor" stroke-width="8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </div>
    <h1 class="title">Tidak dapat terhubung</h1>
    <p class="sub">Periksa koneksi internet Anda atau coba muat ulang halaman.</p>
    <div class="url-box">$displayUrl</div>
    <div class="btns">
      <a href="$failedUrl" class="btn btn-primary">Coba lagi</a>
      <button class="btn btn-secondary" onclick="window.location.href='yue://newtab'">Kembali</button>
    </div>
    <div class="brand">YUE BROWSER</div>
  </div>
</body></html>
        """.trimIndent()
    }

    fun getCustomHttpErrorHtml(failedUrl: String?, errorCode: Int, isDarkActive: Boolean, isPrivate: Boolean): String {
        val title = when (errorCode) {
            400 -> "Permintaan tidak valid"
            401 -> "Anda perlu login"
            403 -> "Akses ditolak"
            404 -> "Halaman tidak ditemukan"
            408 -> "Waktu koneksi habis"
            in 500..599 -> "Situs mengalami gangguan"
            else -> "Terjadi kesalahan"
        }
        val subtitle = when (errorCode) {
            404 -> "Halaman yang Anda cari mungkin telah dipindahkan atau dihapus."
            403 -> "Situs menolak akses dari browser ini."
            408 -> "Situs terlalu lama merespon. Coba sebentar lagi."
            in 500..599 -> "Server situs sedang dalam masalah. Coba sebentar lagi."
            else -> "Kesalahan HTTP $errorCode saat memuat halaman."
        }
        val displayUrl = if (failedUrl.isNullOrBlank()) "situs ini" else failedUrl.take(80)
        
        val bgColor = if (isDarkActive) "#09090B" else "#F9FAFB"
        val textColor = if (isDarkActive) "#FAFAFA" else "#111827"
        val subTextColor = if (isDarkActive) "#A1A1AA" else "#4B5563"
        val cardBg = if (isDarkActive) "rgba(24, 24, 27, 0.6)" else "rgba(255, 255, 255, 0.8)"
        val cardBorder = if (isDarkActive) "rgba(255,255,255,0.05)" else "rgba(0,0,0,0.05)"
        val btnSecondaryBg = if (isDarkActive) "rgba(255,255,255,0.05)" else "rgba(0,0,0,0.04)"
        val btnSecondaryBorder = if (isDarkActive) "rgba(255,255,255,0.08)" else "rgba(0,0,0,0.06)"
        
        val moonColor = if (isDarkActive) "#27272A" else "#E5E7EB"
        val moonHighlight = if (isDarkActive) "#3F3F46" else "#F3F4F6"
        val craterColor = if (isDarkActive) "#18181B" else "#D1D5DB"
        
        val accentColor = if (isPrivate) "#FF002C" else "#EC4899"
        val accentHover = if (isPrivate) "#FF3355" else "#FF6FB5"
        val glowColor = if (isDarkActive) {
            if (isPrivate) "rgba(255,0,44,0.18)" else "rgba(236,72,153,0.18)"
        } else {
            if (isPrivate) "rgba(255,0,44,0.06)" else "rgba(236,72,153,0.06)"
        }

        return """
<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>Yue Browser — $title</title>
<style>
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html, body {
  margin: 0; padding: 0;
  background: $bgColor;
  color: $textColor;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-user-select: none;
  user-select: none;
  -webkit-font-smoothing: antialiased;
}
body {
  background:
    radial-gradient(circle at 50% 25%, $glowColor, transparent 60%),
    $bgColor;
}
.wrap {
  padding: 24px;
  max-width: 380px;
  width: 100%;
  text-align: center;
}
.moon-wrap {
  width: 120px;
  height: 120px;
  margin: 0 auto 24px;
  position: relative;
  animation: floatY 4s ease-in-out infinite;
}
@keyframes floatY {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}
.moon-svg {
  width: 120px;
  height: 120px;
  position: relative;
  z-index: 1;
}
.code-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 32px;
  font-weight: 800;
  color: $accentColor;
  letter-spacing: -1px;
  z-index: 2;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", system-ui, sans-serif;
}
.title {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 8px;
  color: $textColor;
  letter-spacing: -0.3px;
}
.sub {
  font-size: 13.5px;
  line-height: 1.5;
  color: $subTextColor;
  margin: 0 auto 20px;
}
.url-box {
  background: $cardBg;
  border: 1px solid $cardBorder;
  border-radius: 8px;
  padding: 10px 12px;
  margin: 0 0 24px;
  font-size: 11.5px;
  color: $subTextColor;
  text-align: center;
  word-break: break-all;
  font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
}
.btns {
  display: flex;
  gap: 8px;
  justify-content: center;
}
.btn {
  flex: 1;
  display: inline-block;
  padding: 11px 16px;
  border-radius: 8px;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  -webkit-appearance: none;
  appearance: none;
  text-decoration: none;
  transition: transform 0.1s ease, opacity 0.1s ease;
  border: none;
  text-align: center;
}
.btn:active { transform: scale(0.97); }
.btn-primary {
  background: $accentColor;
  color: #FFFFFF;
}
.btn-primary:hover {
  background: $accentHover;
}
.btn-secondary {
  background: $btnSecondaryBg;
  color: $textColor;
  border: 1px solid $btnSecondaryBorder;
}
.brand {
  margin-top: 40px;
  font-size: 10px;
  color: $subTextColor;
  letter-spacing: 2px;
  text-transform: uppercase;
  opacity: 0.5;
  font-weight: 600;
}
</style>
</head><body>
  <div class="wrap">
    <div class="moon-wrap">
      <svg class="moon-svg" viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="moonGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="$moonHighlight"/>
            <stop offset="100%" stop-color="$moonColor"/>
          </linearGradient>
        </defs>
        <!-- The Moon Body -->
        <circle cx="100" cy="100" r="70" fill="url(#moonGrad)"/>
        <!-- Craters -->
        <circle cx="70" cy="75" r="9" fill="$craterColor" opacity="0.45"/>
        <circle cx="130" cy="85" r="7" fill="$craterColor" opacity="0.45"/>
        <circle cx="80" cy="125" r="11" fill="$craterColor" opacity="0.45"/>
        <circle cx="125" cy="130" r="6" fill="$craterColor" opacity="0.4"/>
        <circle cx="100" cy="145" r="4" fill="$craterColor" opacity="0.35"/>
        <!-- Clean Minimalist Crack Cutouts (drawn in bgColor) -->
        <path d="M100 25 L102 55 L96 85 L104 115 L98 145 L100 175" 
              stroke="$bgColor" stroke-width="8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M96 85 L82 73" 
              stroke="$bgColor" stroke-width="8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M104 115 L118 127" 
              stroke="$bgColor" stroke-width="8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <div class="code-overlay">$errorCode</div>
    </div>
    <h1 class="title">$title</h1>
    <p class="sub">$subtitle</p>
    <div class="url-box">$displayUrl</div>
    <div class="btns">
      <a href="$failedUrl" class="btn btn-primary">Coba lagi</a>
      <button class="btn btn-secondary" onclick="window.location.href='yue://newtab'">Kembali</button>
    </div>
    <div class="brand">YUE BROWSER</div>
  </div>
</body></html>
        """.trimIndent()
    }
}
