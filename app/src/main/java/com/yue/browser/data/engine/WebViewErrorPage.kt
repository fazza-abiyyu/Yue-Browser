package com.yue.browser.data.engine

import com.yue.browser.R

object WebViewErrorPage {

    internal fun isNetworkAvailable(context: android.content.Context): Boolean {
        return try {
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true // fallback to assuming connection is available if check crashes
        }
    }

    fun getCustomErrorHtml(
        context: android.content.Context,
        failedUrl: String?,
        errorCode: Int,
        description: String,
        isDarkActive: Boolean,
        isPrivate: Boolean
    ): String {
        android.util.Log.d("WebViewErrorPage", "getCustomErrorHtml failedUrl: '$failedUrl', errorCode: $errorCode, description: '$description'")
        val safeFailedUrl = (failedUrl ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val safeDescription = description.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        
        val isOffline = !isNetworkAvailable(context)
        
        val errCodeStr = if (isOffline) {
            "ERR_INTERNET_DISCONNECTED"
        } else {
            when (errorCode) {
                -2 -> "ERR_NAME_NOT_RESOLVED"
                -5 -> "ERR_CONNECTION_REFUSED"
                -6 -> "ERR_CONNECT_FAIL"
                -8 -> "ERR_CONNECTION_TIMED_OUT"
                -106 -> "ERR_INTERNET_DISCONNECTED"
                -11 -> "ERR_FAILED"
                -14 -> "ERR_ADDRESS_UNREACHABLE"
                else -> "ERR_CONNECTION_FAILED"
            }
        }

        val title: String
        val subtitle: String
        
        val isTechnicalCode = description.contains("net::") || description.length < 25
        
        if (isOffline) {
            title = context.getString(R.string.error_no_internet_title)
            subtitle = if (!failedUrl.isNullOrBlank()) {
                context.getString(R.string.error_no_internet_subtitle_url, safeFailedUrl)
            } else {
                context.getString(R.string.error_no_internet_subtitle)
            }
        } else {
            when (errorCode) {
                -106 -> {
                    title = context.getString(R.string.error_no_internet_title)
                    subtitle = if (!failedUrl.isNullOrBlank()) {
                        context.getString(R.string.error_no_internet_subtitle_url, safeFailedUrl)
                    } else {
                        context.getString(R.string.error_no_internet_subtitle)
                    }
                }
                -8 -> {
                    title = context.getString(R.string.error_timeout_title)
                    subtitle = if (!failedUrl.isNullOrBlank()) {
                        context.getString(R.string.error_timeout_subtitle_url, safeFailedUrl)
                    } else {
                        context.getString(R.string.error_timeout_subtitle)
                    }
                }
                -2, -5, -6, -14 -> {
                    title = context.getString(R.string.error_unreachable_title)
                    subtitle = if (!failedUrl.isNullOrBlank()) {
                        context.getString(R.string.error_unreachable_subtitle_url, safeFailedUrl)
                    } else {
                        context.getString(R.string.error_unreachable_subtitle)
                    }
                }
                else -> {
                    title = context.getString(R.string.error_generic_title)
                    subtitle = if (description.isNotBlank() && !isTechnicalCode) {
                        val urlRegex = Regex("""https?://[^\s]+""")
                        urlRegex.replace(safeDescription) { matchResult ->
                            "<span class=\"url-highlight\">${matchResult.value}</span>"
                        }
                    } else {
                        if (!failedUrl.isNullOrBlank()) {
                            context.getString(R.string.error_generic_subtitle_url, safeFailedUrl)
                        } else {
                            context.getString(R.string.error_generic_subtitle)
                        }
                    }
                }
            }
        }
        
        return buildHtml(
            title = title,
            subtitle = subtitle,
            errCodeBg = "ERR",
            errCodeStr = errCodeStr,
            safeFailedUrl = safeFailedUrl,
            safeDescription = safeDescription,
            isDarkActive = isDarkActive,
            isPrivate = isPrivate,
            retryButtonText = context.getString(R.string.error_retry_button),
            advancedModeText = context.getString(R.string.error_advanced_mode),
            backToHomeText = context.getString(R.string.error_back_to_home),
            technicalDetailsText = context.getString(R.string.error_technical_details)
        )
    }

    fun getCustomHttpErrorHtml(
        context: android.content.Context,
        failedUrl: String?,
        errorCode: Int,
        isDarkActive: Boolean,
        isPrivate: Boolean
    ): String {
        val safeFailedUrl = (failedUrl ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        
        val title = when (errorCode) {
            400 -> context.getString(R.string.error_http_400_title)
            401 -> context.getString(R.string.error_http_401_title)
            403 -> context.getString(R.string.error_http_403_title)
            404 -> context.getString(R.string.error_http_404_title)
            408 -> context.getString(R.string.error_http_408_title)
            429 -> context.getString(R.string.error_http_429_title)
            500 -> context.getString(R.string.error_http_500_title)
            502 -> context.getString(R.string.error_http_502_title)
            503 -> context.getString(R.string.error_http_503_title)
            504 -> context.getString(R.string.error_http_504_title)
            in 500..599 -> context.getString(R.string.error_http_5xx_title)
            else -> context.getString(R.string.error_http_fallback_title)
        }
        
        val subtitle = when (errorCode) {
            400 -> context.getString(R.string.error_http_400_subtitle)
            401 -> context.getString(R.string.error_http_401_subtitle)
            403 -> context.getString(R.string.error_http_403_subtitle)
            404 -> if (!safeFailedUrl.isBlank()) {
                context.getString(R.string.error_http_404_subtitle_url, safeFailedUrl)
            } else {
                context.getString(R.string.error_http_404_subtitle)
            }
            408 -> if (!safeFailedUrl.isBlank()) {
                context.getString(R.string.error_http_408_subtitle_url, safeFailedUrl)
            } else {
                context.getString(R.string.error_http_408_subtitle)
            }
            429 -> context.getString(R.string.error_http_429_subtitle)
            500 -> context.getString(R.string.error_http_500_subtitle)
            502 -> context.getString(R.string.error_http_502_subtitle)
            503 -> context.getString(R.string.error_http_503_subtitle)
            504 -> context.getString(R.string.error_http_504_subtitle)
            in 500..599 -> context.getString(R.string.error_http_5xx_subtitle, errorCode)
            else -> context.getString(R.string.error_http_fallback_subtitle, errorCode)
        }
        
        val safeDescription = context.getString(R.string.error_html_description, errorCode)
        return buildHtml(
            title = title,
            subtitle = subtitle,
            errCodeBg = errorCode.toString(),
            errCodeStr = "HTTP_$errorCode",
            safeFailedUrl = safeFailedUrl,
            safeDescription = safeDescription,
            isDarkActive = isDarkActive,
            isPrivate = isPrivate,
            retryButtonText = context.getString(R.string.error_retry_button),
            advancedModeText = context.getString(R.string.error_advanced_mode),
            backToHomeText = context.getString(R.string.error_back_to_home),
            technicalDetailsText = context.getString(R.string.error_technical_details)
        )
    }

    private fun buildHtml(
        title: String,
        subtitle: String,
        errCodeBg: String,
        errCodeStr: String,
        safeFailedUrl: String,
        safeDescription: String,
        isDarkActive: Boolean,
        isPrivate: Boolean,
        retryButtonText: String = "Coba Lagi",
        advancedModeText: String = "Mode Lanjutan",
        backToHomeText: String = "Kembali ke Beranda",
        technicalDetailsText: String = "Detail masalah teknis:"
    ): String {
        // Theme Colors
        val bgColor = if (isDarkActive) "#0E0C0D" else "#F9F9F9"
        val textColor = if (isDarkActive) "#F0E0E2" else "#1A1C1C"
        val subTextColor = if (isDarkActive) "#A69396" else "#4F4446"
        
        val accentColor = if (isPrivate) {
            if (isDarkActive) "#FFB4AB" else "#BA1A1A"
        } else {
            if (isDarkActive) "#E7BBC6" else "#78555E"
        }
        val accentContainer = if (isPrivate) {
            if (isDarkActive) "#93000A" else "#FFDAD6"
        } else {
            if (isDarkActive) "#5E3E47" else "#FFD1DC"
        }
        val accentTextColor = if (isPrivate) {
            if (isDarkActive) "#FFDAD6" else "#93000A"
        } else {
            if (isDarkActive) "#FFD9E2" else "#7A5761"
        }
        val accentShadow = if (isPrivate) {
            if (isDarkActive) "rgba(147, 0, 10, 0.4)" else "rgba(255, 218, 214, 0.4)"
        } else {
            if (isDarkActive) "rgba(94, 62, 71, 0.4)" else "rgba(255, 209, 220, 0.4)"
        }
        val glowColor = if (isPrivate) {
            if (isDarkActive) "rgba(147, 0, 10, 0.12)" else "rgba(255, 218, 214, 0.08)"
        } else {
            if (isDarkActive) "rgba(94, 62, 71, 0.12)" else "rgba(255, 209, 220, 0.08)"
        }
        
        val cardBg = if (isDarkActive) "rgba(31, 23, 25, 0.6)" else "rgba(238, 230, 232, 0.6)"
        val cardBorder = if (isDarkActive) "rgba(211, 195, 197, 0.12)" else "rgba(129, 116, 118, 0.15)"
        
        val moonColor = if (isDarkActive) "#5E3E47" else "#FFD1DC"
        val moonHighlight = if (isDarkActive) "#894C5C" else "#FFEBEF"
        val craterColor = if (isDarkActive) "#3F252C" else "#E7B3C0"

        return """
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="utf-8"/>
    <meta content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" name="viewport"/>
    <title>Yue Browser — $title</title>
    <style>
        * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
        
        html, body {
            margin: 0;
            padding: 0;
            background-color: $bgColor;
            color: $textColor;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            overflow-x: hidden;
        }

        body {
            background:
                radial-gradient(circle at 50% 30%, $glowColor, transparent 65%),
                $bgColor;
        }

        .main-container {
            position: relative;
            z-index: 10;
            padding: 24px;
            max-width: 448px;
            width: 100%;
            text-align: center;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
        }

        .illustration-box {
            position: relative;
            width: 100%;
            aspect-ratio: 1 / 1;
            max-width: 240px;
            margin-bottom: 32px;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .lunar-bg-glow {
            position: absolute;
            inset: 0;
            background: radial-gradient(circle at center, $accentContainer 0%, transparent 70%);
            opacity: 0.4;
            transform: scale(1.5);
            pointer-events: none;
        }

        .err-text-bg {
            position: absolute;
            inset: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            opacity: 0.03;
            user-select: none;
            pointer-events: none;
            font-size: 110px;
            font-weight: 800;
            color: $accentColor;
            letter-spacing: -0.04em;
        }

        .floating-animation {
            animation: float 8s ease-in-out infinite;
            position: relative;
            z-index: 10;
        }

        @keyframes float {
            0%, 100% { transform: translateY(0px); }
            50% { transform: translateY(-12px); }
        }

        .moon-svg {
            width: 180px;
            height: 180px;
            display: block;
        }

        .space-y-3 {
            margin-bottom: 32px;
            display: flex;
            flex-direction: column;
            gap: 12px;
            align-items: center;
            width: 100%;
        }

        .title-text {
            font-size: 24px;
            font-weight: 700;
            margin: 0;
            color: $textColor;
            letter-spacing: -0.02em;
        }

        .subtitle-text {
            font-size: 14px;
            line-height: 1.5;
            color: $subTextColor;
            margin: 0 auto;
            max-width: 320px;
        }

        .url-highlight {
            font-weight: 600;
            color: $accentColor;
            word-break: break-all;
        }



        .badge-box {
            display: inline-block;
            padding: 4px 12px;
            background-color: $cardBg;
            color: $subTextColor;
            border-radius: 9999px;
            font-size: 10px;
            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
            text-transform: uppercase;
            letter-spacing: 0.1em;
            border: 1px solid $cardBorder;
            margin-top: 8px;
        }

        .btns-container {
            width: 100%;
            padding: 0 16px;
            display: flex;
            flex-direction: column;
            gap: 16px;
        }

        .btn-primary {
            display: flex;
            align-items: center;
            justify-content: center;
            width: 100%;
            padding: 16px 20px;
            background-color: $accentContainer;
            color: $accentTextColor;
            font-weight: 700;
            border-radius: 16px;
            text-decoration: none;
            border: none;
            font-size: 15px;
            cursor: pointer;
            box-shadow: 0 10px 30px -5px $accentShadow;
            transition: transform 0.15s ease, filter 0.15s ease;
        }

        .btn-primary:active {
            transform: scale(0.98);
        }

        .btn-primary:hover {
            filter: brightness(0.95);
        }

        .refresh-icon {
            margin-right: 8px;
        }

        .btn-secondary {
            background: transparent;
            color: $subTextColor;
            border: 1px solid $cardBorder;
            font-size: 13px;
            font-weight: 600;
            padding: 8px 18px;
            width: auto;
            margin: 0 auto;
            border-radius: 20px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            cursor: pointer;
            -webkit-appearance: none;
            appearance: none;
            transition: border-color 0.15s ease, color 0.15s ease;
        }

        .btn-secondary:hover {
            border-color: $accentColor;
            color: $accentColor;
        }

        .chevron-icon {
            transition: transform 0.3s ease;
        }

        .advanced-card {
            display: none;
            margin-top: 12px;
            padding: 16px;
            background-color: $cardBg;
            border: 1px solid $cardBorder;
            border-radius: 16px;
            text-align: left;
            animation: fadeIn 0.3s ease-out;
            width: 100%;
        }

        .advanced-card.show {
            display: block;
        }

        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(8px); }
            to { opacity: 1; transform: translateY(0); }
        }

        .advanced-text {
            font-size: 12px;
            line-height: 1.6;
            color: $subTextColor;
            margin: 0 0 10px;
        }

        .advanced-link {
            font-size: 12px;
            font-weight: 700;
            color: $accentColor;
            text-decoration: underline;
            cursor: pointer;
        }


    </style>
</head>
<body>
    <main class="main-container">
        <!-- Illustration Section -->
        <div class="illustration-box">
            <!-- Background Glow -->
            <div class="lunar-bg-glow"></div>
            <!-- Background Watermark -->
            <div class="err-text-bg">$errCodeBg</div>
            <!-- Integrated Moon Image (SVG offline) -->
            <div class="floating-animation">
                <svg class="moon-svg" viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
                    <defs>
                        <filter id="lunar-glow" x="-20%" y="-20%" width="140%" height="140%">
                            <feGaussianBlur stdDeviation="12" result="blur" />
                            <feComposite in="SourceGraphic" in2="blur" operator="over" />
                        </filter>
                        <linearGradient id="moonGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                            <stop offset="0%" stop-color="$moonHighlight"/>
                            <stop offset="100%" stop-color="$moonColor"/>
                        </linearGradient>
                    </defs>
                    <!-- Outer glow -->
                    <circle cx="100" cy="100" r="65" fill="$moonColor" opacity="0.25" filter="url(#lunar-glow)"/>
                    <!-- Body -->
                    <circle cx="100" cy="100" r="65" fill="url(#moonGrad)"/>
                    <!-- Craters -->
                    <circle cx="75" cy="70" r="8" fill="$craterColor" opacity="0.55"/>
                    <circle cx="125" cy="80" r="6" fill="$craterColor" opacity="0.55"/>
                    <circle cx="85" cy="120" r="10" fill="$craterColor" opacity="0.55"/>
                    <circle cx="120" cy="125" r="5" fill="$craterColor" opacity="0.45"/>
                </svg>
            </div>
        </div>
        
        <!-- Typography Content -->
        <div class="space-y-3">
            <h2 class="title-text">$title</h2>
            <p class="subtitle-text">$subtitle</p>
            
            <!-- Technical Error Code Badge -->
            <div>
                <span class="badge-box">$errCodeStr</span>
            </div>
        </div>
        
        <!-- Action Buttons -->
        <div class="btns-container">
            <a class="btn-primary" href="#" onclick="window.location.replace('$safeFailedUrl'); return false;">
                <!-- Inline refresh icon SVG -->
                <svg class="refresh-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/>
                </svg>
                $retryButtonText
            </a>
            
            <!-- Advanced Toggle Button with thin border -->
            <button class="btn-secondary" id="advanced-toggle">
                <span>$advancedModeText</span>
                <!-- Chevron Down icon SVG -->
                <svg id="toggle-icon" class="chevron-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="6 9 12 15 18 9"></polyline>
                </svg>
            </button>
            
            <!-- Advanced Options Hidden by Default -->
            <div class="advanced-card" id="advanced-options">
                <p class="advanced-text">
                    $technicalDetailsText<br>
                    <span style="font-family:monospace; opacity:0.85;">$safeDescription</span>
                </p>
                <span class="advanced-link" onclick="window.location.href='yue://newtab'">$backToHomeText</span>
            </div>
        </div>
        

    </main>
    <script>
        document.getElementById('advanced-toggle').addEventListener('click', function() {
            const content = document.getElementById('advanced-options');
            const icon = document.getElementById('toggle-icon');
            
            content.classList.toggle('show');
            
            if (content.classList.contains('show')) {
                icon.style.transform = 'rotate(180deg)';
                icon.style.webkitTransform = 'rotate(180deg)';
            } else {
                icon.style.transform = 'rotate(0deg)';
                icon.style.webkitTransform = 'rotate(0deg)';
            }
        });
    </script>
</body>
</html>
""".trimIndent()
    }
}
